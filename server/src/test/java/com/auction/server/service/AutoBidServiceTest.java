package com.auction.server.service;

import com.auction.server.dao.AutoBidDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.AutoBidRequest;
import com.auction.shared.dto.response.AutoBidResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidSetting;
import com.auction.shared.model.BidTransaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho AutoBidService.
 *
 * AutoBidService có 3 đặc điểm kỹ thuật quan trọng cần test:
 *  1. register()  — Validate + lưu setting + trigger ngay nếu cần
 *  2. cancel()    — Xóa khỏi queue + deactivate DB
 *  3. Observer events — onBidPlaced, onAuctionClosed, onAuctionStatusChanged
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoBidService Tests")
class AutoBidServiceTest {

    @Mock private BidService        bidService;
    @Mock private AutoBidDAO        autoBidDao;
    @Mock private BidTransactionDAO bidDao;
    @Mock private AuctionManager    auctionManager;
    @Mock private AuctionEventBus   eventBus;

    private static final String VALID_TOKEN  = "valid-token";
    private static final String BIDDER_ID    = "bidder-001";
    private static final String BIDDER_2_ID  = "bidder-002";
    private static final String AUCTION_ID   = "auction-abc";

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────
    private AutoBidService buildService() {
        return new AutoBidService(bidService, autoBidDao, bidDao);
    }

    private Auction buildRunningAuction() {
        Auction a = new Auction();
        a.setId(AUCTION_ID);
        a.setSellerId("seller-999");
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartPrice(100_000.0);
        a.setCurrentPrice(100_000.0);
        a.setMinBidIncrement(10_000.0);
        return a;
    }

    private AutoBidSetting buildSetting(String bidderId, double maxBid, double increment) {
        return new AutoBidSetting(
                "setting-" + bidderId, bidderId, AUCTION_ID,
                maxBid, increment, true, LocalDateTime.now()
        );
    }

    private AutoBidRequest buildRequest(double maxBid, double increment) {
        return new AutoBidRequest(AUCTION_ID, BIDDER_ID, maxBid, increment);
    }

    private BidTransaction buildTopBid(String bidderId, double amount) {
        // Dung object that thay vi mock de tranh UnfinishedStubbingException
        // khi goi buildTopBid() ben trong when().thenReturn()
        return new BidTransaction(
                "tx-" + bidderId, AUCTION_ID, bidderId, bidderId, amount,
                LocalDateTime.now(), false
        );
    }

    // =========================================================
    //  register() — Token validation
    // =========================================================
    @Nested
    @DisplayName("register() - Xac thuc Token")
    class RegisterTokenTests {

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void register_invalidToken_throwsUnauthorized() {
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId("bad-token")).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.register(req, "bad-token"));

                assertEquals("UNAUTHORIZED", ex.getCode());
                verify(autoBidDao, never()).save(any());
            }
        }

        @Test
        @DisplayName("Token hop le -> tiep tuc xu ly")
        void register_validToken_proceeds() {
            Auction auction = buildRunningAuction();
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                assertDoesNotThrow(() -> service.register(req, VALID_TOKEN));
            }
        }
    }

    // =========================================================
    //  register() — Auction validation
    // =========================================================
    @Nested
    @DisplayName("register() - Kiem tra trang thai phien")
    class RegisterAuctionValidationTests {

        @Test
        @DisplayName("Phien khong ton tai -> nem INVALID_AUCTION")
        void register_auctionNotFound_throwsException() {
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(null); // Khong ton tai

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.register(req, VALID_TOKEN));

                assertEquals("INVALID_AUCTION", ex.getCode());
            }
        }

        @Test
        @DisplayName("Phien OPEN (chua bat dau) -> nem INVALID_AUCTION")
        void register_auctionOpen_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.OPEN);
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                assertEquals("INVALID_AUCTION",
                        assertThrows(AuctionException.class,
                                () -> service.register(req, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("Phien FINISHED -> nem INVALID_AUCTION")
        void register_auctionFinished_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.FINISHED);
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                assertEquals("INVALID_AUCTION",
                        assertThrows(AuctionException.class,
                                () -> service.register(req, VALID_TOKEN)).getCode());
            }
        }
    }

    // =========================================================
    //  register() — MaxBid validation
    // =========================================================
    @Nested
    @DisplayName("register() - Kiem tra MaxBid")
    class RegisterMaxBidTests {

        @Test
        @DisplayName("maxBid <= currentPrice -> nem INVALID_BID")
        void register_maxBidBelowCurrentPrice_throwsException() {
            Auction auction = buildRunningAuction(); // currentPrice = 100k
            AutoBidRequest req = buildRequest(100_000.0, 10_000.0); // maxBid = 100k = currentPrice

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.register(req, VALID_TOKEN));

                assertEquals("INVALID_BID", ex.getCode());
                verify(autoBidDao, never()).save(any());
            }
        }

        @Test
        @DisplayName("maxBid < currentPrice -> nem INVALID_BID")
        void register_maxBidBelowCurrentPrice_strict() {
            Auction auction = buildRunningAuction(); // currentPrice = 100k
            AutoBidRequest req = buildRequest(80_000.0, 10_000.0); // maxBid < currentPrice

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                assertEquals("INVALID_BID",
                        assertThrows(AuctionException.class,
                                () -> service.register(req, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("maxBid > currentPrice -> hop le")
        void register_maxBidAboveCurrentPrice_success() {
            Auction auction = buildRunningAuction(); // currentPrice = 100k
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0); // maxBid = 200k > 100k

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                assertDoesNotThrow(() -> service.register(req, VALID_TOKEN));
                verify(autoBidDao, times(1)).save(any(AutoBidSetting.class));
            }
        }
    }

    // =========================================================
    //  register() — Happy path
    // =========================================================
    @Nested
    @DisplayName("register() - Happy path")
    class RegisterHappyPathTests {

        @Test
        @DisplayName("Dang ky thanh cong -> tra ve AutoBidResponse success=true")
        void register_success_returnsSuccessResponse() {
            Auction auction = buildRunningAuction();
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                AutoBidResponse resp = service.register(req, VALID_TOKEN);

                assertTrue(resp.isSuccess());
                assertNotNull(resp.getMessage());
                verify(autoBidDao, times(1)).save(any(AutoBidSetting.class));
            }
        }

        @Test
        @DisplayName("Bidder chua dan dau + nextBid <= maxBid -> setting duoc luu va placeSystemBid duoc goi")
        void register_notLeading_triggersAutoBidImmediately() {
            Auction auction = buildRunningAuction(); // currentPrice=100k
            // Dat currentLeaderId la nguoi KHAC -> BIDDER_ID chua dan dau
            auction.setCurrentLeaderId(BIDDER_2_ID);
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0); // nextBid=110k <= maxBid=200k

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                service.register(req, VALID_TOKEN);

                // Setting phai duoc luu
                verify(autoBidDao, times(1)).save(any(AutoBidSetting.class));
                // placeSystemBid phai duoc goi vi BIDDER chua dan dau
                verify(bidService, atLeastOnce()).placeSystemBid(any());
            }
        }

        @Test
        @DisplayName("Bidder da dan dau -> KHONG trigger auto-bid ngay")
        void register_alreadyLeading_doesNotTrigger() {
            Auction auction = buildRunningAuction(); // currentPrice=100k
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                // Bidder da la nguoi dan dau hien tai
                when(bidDao.findHighestBid(AUCTION_ID))
                        .thenReturn(Optional.of(buildTopBid(BIDDER_ID, 100_000.0)));

                service.register(req, VALID_TOKEN);

                // Da dan dau roi -> khong trigger them
                verify(bidService, never()).placeSystemBid(any());
            }
        }

        @Test
        @DisplayName("nextBid > maxBid -> KHONG trigger du chua dan dau")
        void register_nextBidExceedsMaxBid_doesNotTrigger() {
            Auction auction = buildRunningAuction(); // currentPrice=100k
            // maxBid=105k, nextBid=110k -> 110k > 105k -> khong the dat
            AutoBidRequest req = buildRequest(105_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                service.register(req, VALID_TOKEN);

                verify(bidService, never()).placeSystemBid(any());
            }
        }

        @Test
        @DisplayName("Setting duoc luu voi dung bidderId, auctionId, maxBid, increment")
        void register_savesCorrectSetting() {
            Auction auction = buildRunningAuction();
            AutoBidRequest req = buildRequest(300_000.0, 15_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                service.register(req, VALID_TOKEN);

                verify(autoBidDao).save(argThat(s ->
                        s.getBidderId().equals(BIDDER_ID)
                                && s.getAuctionId().equals(AUCTION_ID)
                                && s.getMaxBid() == 300_000.0
                                && s.getIncrement() == 15_000.0
                                && s.isActive()
                                && s.getRegisteredAt() != null));
            }
        }
    }

    // =========================================================
    //  cancel()
    // =========================================================
    @Nested
    @DisplayName("cancel()")
    class CancelTests {

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void cancel_invalidToken_throwsUnauthorized() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId("bad")).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.cancel(AUCTION_ID, "bad"));

                assertEquals("UNAUTHORIZED", ex.getCode());
            }
        }

        @Test
        @DisplayName("Huy thanh cong -> tra ve AutoBidResponse success=true")
        void cancel_success_returnsSuccessResponse() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);

                AutoBidResponse resp = service.cancel(AUCTION_ID, VALID_TOKEN);

                assertTrue(resp.isSuccess());
                assertNotNull(resp.getMessage());
            }
        }

        @Test
        @DisplayName("Chua dang ky -> cancel van tra ve success (idempotent)")
        void cancel_notRegistered_stillSucceeds() {
            // Khong co setting trong queue -> cancel van khong nem exception
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);

                assertDoesNotThrow(() -> service.cancel(AUCTION_ID, VALID_TOKEN));
            }
        }

        @Test
        @DisplayName("Da dang ky roi huy -> setting bi deactivate trong DB")
        void cancel_afterRegister_deactivatesSettingInDB() {
            Auction auction = buildRunningAuction();
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                // Dang ky truoc
                service.register(req, VALID_TOKEN);

                // Reset de kiem tra cancel rieng
                reset(autoBidDao);

                // Huy
                service.cancel(AUCTION_ID, VALID_TOKEN);

                // Setting phai bi update (deactivate) trong DB
                verify(autoBidDao, atLeastOnce()).update(argThat(s ->
                        !s.isActive() && s.getBidderId().equals(BIDDER_ID)));
            }
        }
    }

    // =========================================================
    //  restoreQueuesFromDatabase()
    // =========================================================
    @Nested
    @DisplayName("restoreQueuesFromDatabase()")
    class RestoreQueueTests {

        @Test
        @DisplayName("Co phien RUNNING voi active settings -> khoi phuc queue")
        void restore_withActiveSettings_loadsToQueue() {
            Auction auction = buildRunningAuction();
            AutoBidSetting s1 = buildSetting(BIDDER_ID,   200_000.0, 10_000.0);
            AutoBidSetting s2 = buildSetting(BIDDER_2_ID, 300_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                when(autoBidDao.findActiveByAuction(AUCTION_ID)).thenReturn(List.of(s1, s2));

                // Khong nem exception
                assertDoesNotThrow(() ->
                        service.restoreQueuesFromDatabase(List.of(auction)));

                // Xac nhan DA goi findActiveByAuction
                verify(autoBidDao, times(1)).findActiveByAuction(AUCTION_ID);
            }
        }

        @Test
        @DisplayName("Phien RUNNING khong co active setting -> bo qua")
        void restore_noActiveSettings_skips() {
            Auction auction = buildRunningAuction();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                when(autoBidDao.findActiveByAuction(AUCTION_ID)).thenReturn(List.of());

                assertDoesNotThrow(() ->
                        service.restoreQueuesFromDatabase(List.of(auction)));
            }
        }

        @Test
        @DisplayName("Danh sach phien rong -> khong lam gi")
        void restore_emptyAuctionList_doesNothing() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                assertDoesNotThrow(() ->
                        service.restoreQueuesFromDatabase(List.of()));

                verify(autoBidDao, never()).findActiveByAuction(any());
            }
        }

        @Test
        @DisplayName("Nhieu phien RUNNING -> khoi phuc tat ca")
        void restore_multipleAuctions_restoresAll() {
            Auction a1 = buildRunningAuction();
            Auction a2 = buildRunningAuction();
            a2.setId("auction-002");

            AutoBidSetting s1 = buildSetting(BIDDER_ID, 200_000.0, 10_000.0);
            AutoBidSetting s2 = buildSetting(BIDDER_2_ID, 300_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                when(autoBidDao.findActiveByAuction(AUCTION_ID)).thenReturn(List.of(s1));
                when(autoBidDao.findActiveByAuction("auction-002")).thenReturn(List.of(s2));

                service.restoreQueuesFromDatabase(List.of(a1, a2));

                verify(autoBidDao, times(1)).findActiveByAuction(AUCTION_ID);
                verify(autoBidDao, times(1)).findActiveByAuction("auction-002");
            }
        }
    }

    // =========================================================
    //  Observer Events
    // =========================================================
    @Nested
    @DisplayName("Observer Events")
    class ObserverEventTests {

        @Test
        @DisplayName("onBidPlaced voi queue rong -> khong nem exception, khong trigger bid")
        void onBidPlaced_emptyQueue_doesNotTrigger() {
            Auction auction = buildRunningAuction();
            BidTransaction bid = new BidTransaction(
                    "tx-001", AUCTION_ID, BIDDER_ID, "alice", 100_000.0, LocalDateTime.now(), false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                // KHONG stub gi them — queue rong, service xu ly ngay lap tuc
                assertDoesNotThrow(() -> service.onBidPlaced(auction, bid));
                verify(bidService, never()).placeSystemBid(any());
            }
        }

        @Test
        @DisplayName("onAuctionClosed -> don dep queue va processing set")
        void onAuctionClosed_cleansUpQueue() {
            Auction auction = buildRunningAuction();
            AutoBidRequest req = buildRequest(200_000.0, 10_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                // Dang ky truoc de co queue
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());
                service.register(req, VALID_TOKEN);

                // Dong phien
                service.onAuctionClosed(auction);

                // Sau khi dong, dang ky moi se khong con queue cu
                // (khong nem exception)
                assertDoesNotThrow(() -> service.onAuctionClosed(auction));
            }
        }

        @Test
        @DisplayName("onAuctionStatusChanged CANCELED -> don dep queue")
        void onAuctionStatusChanged_canceled_cleansUp() {
            Auction auction = buildRunningAuction();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                assertDoesNotThrow(() ->
                        service.onAuctionStatusChanged(auction, "CANCELED"));
            }
        }

        @Test
        @DisplayName("onAuctionStarted -> khong lam gi (empty handler)")
        void onAuctionStarted_doesNothing() {
            Auction auction = buildRunningAuction();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                assertDoesNotThrow(() -> service.onAuctionStarted(auction));
                verifyNoInteractions(bidService, autoBidDao, bidDao);
            }
        }

        @Test
        @DisplayName("onAuctionExtended -> khong lam gi (empty handler)")
        void onAuctionExtended_doesNothing() {
            Auction auction = buildRunningAuction();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                assertDoesNotThrow(() -> service.onAuctionExtended(auction, 60L));
                verifyNoInteractions(bidService, autoBidDao, bidDao);
            }
        }

        @Test
        @DisplayName("onError -> khong nem exception, ghi log")
        void onError_doesNotThrow() {
            Auction auction = buildRunningAuction();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AutoBidService service = buildService();

                assertDoesNotThrow(() ->
                        service.onError(auction, "SOME_ERROR", "Test error message"));
            }
        }
    }
}