
package com.auction.server.service;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.CreateAuctionRequest;
import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.enums.UserRole;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UnauthorizedException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;

import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionService Tests")
class AuctionServiceTest {

    @Mock private AuctionDAO        auctionDao;
    @Mock private ItemDAO           itemDao;
    @Mock private BidTransactionDAO bidDao;
    @Mock private UserDAO           userDao;
    @Mock private WalletService     walletService;
    @Mock private AuctionManager    auctionManager;
    @Mock private AuctionEventBus   eventBus;

    private static final String VALID_TOKEN  = "valid-token";
    private static final String SELLER_ID    = "seller-001";
    private static final String BIDDER_ID    = "bidder-001";
    private static final String AUCTION_ID   = "auction-abc";
    private static final String WINNER_TOKEN = "winner-token";

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────
    private AuctionService buildService() {
        // Tao service BEN TRONG mock static block
        return new AuctionService(auctionDao, itemDao, bidDao, userDao, walletService);
    }

    private Seller buildSeller() {
        Seller s = new Seller();
        s.setId(SELLER_ID);
        s.setUsername("bob_seller");
        s.setRole(UserRole.SELLER);
        return s;
    }

    private Bidder buildBidder() {
        Bidder b = new Bidder();
        b.setId(BIDDER_ID);
        b.setUsername("alice");
        b.setRole(UserRole.BIDDER);
        return b;
    }

    private Auction buildAuction(AuctionStatus status) {
        Auction a = new Auction();
        a.setId(AUCTION_ID);
        a.setSellerId(SELLER_ID);
        a.setItemId("item-001");
        a.setStatus(status);
        a.setStartPrice(100_000.0);
        a.setCurrentPrice(100_000.0);
        a.setMinBidIncrement(10_000.0);
        a.setStartTime(LocalDateTime.now().minusMinutes(10));
        a.setEndTime(LocalDateTime.now().plusHours(1));
        return a;
    }

    private CreateAuctionRequest buildCreateRequest() {
        CreateAuctionRequest req = new CreateAuctionRequest();
        req.setTitle("Laptop Gaming");
        req.setDescription("RTX 4090, RAM 32GB");
        req.setStartingPrice(5_000_000.0);
        req.setMinBidIncrement(200_000.0);
        req.setDurationMinutes(60);
        req.setCategory(ItemCategory.ELECTRONICS);
        return req;
    }

    // =========================================================
    //  createAuction()
    // =========================================================
    @Nested
    @DisplayName("createAuction()")
    class CreateAuctionTests {

        @Test
        @DisplayName("Seller hop le tao phien -> tra ve AuctionResponse day du thong tin")
        void createAuction_seller_success() {
            CreateAuctionRequest req = buildCreateRequest();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDao.save(any())).thenReturn(true);
                when(auctionDao.save(any())).thenReturn(true);

                AuctionResponse resp = service.createAuction(req, VALID_TOKEN);

                assertNotNull(resp);
                assertNotNull(resp.getAuctionId());
                assertEquals("Laptop Gaming", resp.getTitle());
                assertEquals(5_000_000.0, resp.getStartingPrice());
                assertEquals(AuctionStatus.OPEN, resp.getStatus());
                assertEquals(SELLER_ID, resp.getSellerId());

                verify(itemDao, times(1)).save(any());
                verify(auctionDao, times(1)).save(any());
                verify(auctionManager, times(1)).addAuction(any());
            }
        }

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void createAuction_invalidToken_throwsUnauthorized() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId("bad-token")).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.createAuction(buildCreateRequest(), "bad-token"));

                assertEquals("UNAUTHORIZED", ex.getCode());
                verify(auctionDao, never()).save(any());
            }
        }

        @Test
        @DisplayName("Bidder co gang tao phien -> nem PERMISSION_DENIED")
        void createAuction_bidder_throwsPermissionDenied() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder()); // Bidder, not Seller

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.createAuction(buildCreateRequest(), VALID_TOKEN));

                assertEquals("PERMISSION_DENIED", ex.getCode());
            }
        }

        @Test
        @DisplayName("Luu item that bai -> nem RuntimeException, auction khong duoc tao")
        void createAuction_itemSaveFails_throwsException() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDao.save(any())).thenReturn(false); // DB loi

                assertThrows(RuntimeException.class,
                        () -> service.createAuction(buildCreateRequest(), VALID_TOKEN));

                verify(auctionDao, never()).save(any());
            }
        }

        @Test
        @DisplayName("Luu auction that bai -> nem RuntimeException")
        void createAuction_auctionSaveFails_throwsException() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDao.save(any())).thenReturn(true);
                when(auctionDao.save(any())).thenReturn(false);

                assertThrows(RuntimeException.class,
                        () -> service.createAuction(buildCreateRequest(), VALID_TOKEN));
            }
        }

        @Test
        @DisplayName("startTime tuong lai -> duoc su dung, khong bi ghi de")
        void createAuction_futureStartTime_isPreserved() {
            CreateAuctionRequest req = buildCreateRequest();
            LocalDateTime futureStart = LocalDateTime.now().plusHours(2);
            req.setStartTime(futureStart);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDao.save(any())).thenReturn(true);
                when(auctionDao.save(any())).thenReturn(true);

                AuctionResponse resp = service.createAuction(req, VALID_TOKEN);

                // startTime phai >= futureStart
                assertTrue(resp.getStartTime().isEqual(futureStart)
                        || resp.getStartTime().isAfter(futureStart.minusSeconds(1)));
            }
        }

        @Test
        @DisplayName("minBidIncrement = 0 -> tu dong tinh 5% cua startingPrice")
        void createAuction_zeroIncrement_calculatesDefault() {
            CreateAuctionRequest req = buildCreateRequest();
            req.setMinBidIncrement(0); // Bo trong -> tu tinh
            req.setStartingPrice(1_000_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDao.save(any())).thenReturn(true);
                when(auctionDao.save(any())).thenReturn(true);

                service.createAuction(req, VALID_TOKEN);

                // Xac nhan auction duoc luu voi minBidIncrement = 5% * 1M = 50k
                verify(auctionDao).save(argThat(a -> a.getMinBidIncrement() == 50_000.0));
            }
        }
    }

    // =========================================================
    //  startAuction()
    // =========================================================
    @Nested
    @DisplayName("startAuction()")
    class StartAuctionTests {

        @Test
        @DisplayName("Phien OPEN -> chuyen sang RUNNING, broadcast event")
        void startAuction_open_success() {
            Auction auction = buildAuction(AuctionStatus.OPEN);
            Item item = mock(Item.class);
            when(item.getTitle()).thenReturn("Laptop");
            when(item.getDescription()).thenReturn("RTX 4090");

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.isValid(VALID_TOKEN)).thenReturn(true);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(itemDao.findById(auction.getItemId())).thenReturn(item);

                AuctionResponse resp = service.startAuction(AUCTION_ID, VALID_TOKEN);

                assertNotNull(resp);
                assertEquals(AuctionStatus.RUNNING, resp.getStatus());
                verify(auctionDao, times(1)).update(argThat(a ->
                        a.getStatus() == AuctionStatus.RUNNING));
                verify(auctionManager, times(1)).addAuction(any());
                verify(eventBus, times(1)).publishAuctionStarted(any());
            }
        }

        @Test
        @DisplayName("Phien khong phai OPEN (RUNNING) -> nem INVALID_STATUS")
        void startAuction_alreadyRunning_throwsException() {
            Auction auction = buildAuction(AuctionStatus.RUNNING);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.isValid(VALID_TOKEN)).thenReturn(true);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.startAuction(AUCTION_ID, VALID_TOKEN));

                assertEquals("INVALID_STATUS", ex.getCode());
                verify(eventBus, never()).publishAuctionStarted(any());
            }
        }

        @Test
        @DisplayName("Auction khong ton tai -> nem AUCTION_NOT_FOUND")
        void startAuction_notFound_throwsException() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.isValid(VALID_TOKEN)).thenReturn(true);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(null);
                when(auctionDao.findById(AUCTION_ID)).thenReturn(null);

                assertThrows(ResourceNotFoundException.class,
                        () -> service.startAuction(AUCTION_ID, VALID_TOKEN));
            }
        }
    }

    // =========================================================
    //  closeAuction()
    // =========================================================
    @Nested
    @DisplayName("closeAuction()")
    class CloseAuctionTests {

        @Test
        @DisplayName("Phien RUNNING co nguoi dat gia -> FINISHED, luu winner, broadcast")
        void closeAuction_running_withBids_success() {
            Auction auction = buildAuction(AuctionStatus.RUNNING);
            BidTransaction topBid = mock(BidTransaction.class);
            when(topBid.getBidderId()).thenReturn(BIDDER_ID);
            when(topBid.getBidderName()).thenReturn("alice");
            when(topBid.getAmount()).thenReturn(200_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.of(topBid));

                service.closeAuction(AUCTION_ID);

                assertEquals(AuctionStatus.FINISHED, auction.getStatus());
                assertEquals(BIDDER_ID, auction.getWinnerId());
                assertEquals(200_000.0, auction.getCurrentPrice());
                verify(auctionDao, times(1)).update(auction);
                verify(auctionManager, times(1)).removeAuction(AUCTION_ID);
                verify(eventBus, times(1)).publishAuctionClosed(auction);
            }
        }

        @Test
        @DisplayName("Phien RUNNING khong co bid -> FINISHED, winnerId = null")
        void closeAuction_running_noBids_noWinner() {
            Auction auction = buildAuction(AuctionStatus.RUNNING);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.empty());

                service.closeAuction(AUCTION_ID);

                assertEquals(AuctionStatus.FINISHED, auction.getStatus());
                assertNull(auction.getWinnerId());
            }
        }

        @Test
        @DisplayName("Phien khong phai RUNNING -> bo qua, khong lam gi")
        void closeAuction_notRunning_doesNothing() {
            Auction auction = buildAuction(AuctionStatus.FINISHED);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                service.closeAuction(AUCTION_ID); // Khong nem exception

                verify(auctionDao, never()).update(any());
                verify(eventBus, never()).publishAuctionClosed(any());
            }
        }

        @Test
        @DisplayName("closeAuction KHONG goi settleAuction (settle sau khi confirmPayment)")
        void closeAuction_doesNotSettleWallet() {
            Auction auction = buildAuction(AuctionStatus.RUNNING);
            BidTransaction topBid = mock(BidTransaction.class);
            when(topBid.getBidderId()).thenReturn(BIDDER_ID);
            when(topBid.getBidderName()).thenReturn("alice");
            when(topBid.getAmount()).thenReturn(200_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findHighestBid(AUCTION_ID)).thenReturn(Optional.of(topBid));

                service.closeAuction(AUCTION_ID);

                // Thiet ke moi: khong settle vi ngay, cho winner confirmPayment
                verify(walletService, never()).settleAuction(any(), anyDouble(), any(), any(), any());
            }
        }
    }

    // =========================================================
    //  confirmPayment()
    // =========================================================
    @Nested
    @DisplayName("confirmPayment()")
    class ConfirmPaymentTests {

        @Test
        @DisplayName("Winner xac nhan -> settle vi, chuyen sang PAID, broadcast")
        void confirmPayment_winner_success() {
            Auction auction = buildAuction(AuctionStatus.FINISHED);
            auction.setWinnerId(BIDDER_ID);
            auction.setCurrentPrice(500_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(WINNER_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);
                when(walletService.findAdminId()).thenReturn("admin-001");

                service.confirmPayment(AUCTION_ID, WINNER_TOKEN);

                assertEquals(AuctionStatus.PAID, auction.getStatus());
                verify(walletService, times(1)).settleAuction(
                        eq(BIDDER_ID), eq(500_000.0), eq(SELLER_ID), eq(AUCTION_ID), eq("admin-001"));
                verify(auctionDao, times(1)).update(argThat(a -> a.getStatus() == AuctionStatus.PAID));
                verify(eventBus, times(1)).publishAuctionStatusChanged(any(), eq("PAID"));
            }
        }

        @Test
        @DisplayName("Khong phai winner -> nem UnauthorizedException")
        void confirmPayment_notWinner_throwsUnauthorized() {
            Auction auction = buildAuction(AuctionStatus.FINISHED);
            auction.setWinnerId("other-bidder"); // Winner khac

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                assertThrows(UnauthorizedException.class,
                        () -> service.confirmPayment(AUCTION_ID, VALID_TOKEN));

                verify(walletService, never()).settleAuction(any(), anyDouble(), any(), any(), any());
            }
        }

        @Test
        @DisplayName("Phien khong phai FINISHED -> nem INVALID_STATE")
        void confirmPayment_notFinished_throwsException() {
            Auction auction = buildAuction(AuctionStatus.RUNNING);
            auction.setWinnerId(BIDDER_ID);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(WINNER_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> service.confirmPayment(AUCTION_ID, WINNER_TOKEN));

                assertEquals("INVALID_STATE", ex.getCode());
            }
        }

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void confirmPayment_invalidToken_throwsUnauthorized() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId("bad")).thenReturn(null);

                assertEquals("UNAUTHORIZED",
                        assertThrows(AuctionException.class,
                                () -> service.confirmPayment(AUCTION_ID, "bad")).getCode());
            }
        }
    }

    // =========================================================
    //  cancelAuction()
    // =========================================================
    @Nested
    @DisplayName("cancelAuction()")
    class CancelAuctionTests {

        @Test
        @DisplayName("Admin huy phien bat ky -> CANCELED, broadcast")
        void cancelAuction_admin_success() {
            Auction auction = buildAuction(AuctionStatus.OPEN);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn("admin-001");
                tu.when(() -> TokenUtil.getRole(VALID_TOKEN)).thenReturn("ADMIN");
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                service.cancelAuction(AUCTION_ID, VALID_TOKEN);

                assertEquals(AuctionStatus.CANCELED, auction.getStatus());
                verify(auctionDao, times(1)).update(auction);
                verify(eventBus, times(1)).publishAuctionStatusChanged(any(), eq("CANCELED"));
            }
        }

        @Test
        @DisplayName("Seller huy phien cua chinh minh -> CANCELED")
        void cancelAuction_seller_ownsAuction_success() {
            Auction auction = buildAuction(AuctionStatus.OPEN);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                tu.when(() -> TokenUtil.getRole(VALID_TOKEN)).thenReturn("SELLER");
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                service.cancelAuction(AUCTION_ID, VALID_TOKEN);

                assertEquals(AuctionStatus.CANCELED, auction.getStatus());
            }
        }

        @Test
        @DisplayName("Seller huy phien cua nguoi khac -> nem UnauthorizedException")
        void cancelAuction_seller_notOwner_throwsException() {
            Auction auction = buildAuction(AuctionStatus.OPEN);
            auction.setSellerId("other-seller");

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                tu.when(() -> TokenUtil.getRole(VALID_TOKEN)).thenReturn("SELLER");
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                assertThrows(UnauthorizedException.class,
                        () -> service.cancelAuction(AUCTION_ID, VALID_TOKEN));
            }
        }

        @Test
        @DisplayName("Huy phien PAID -> nem INVALID_STATE")
        void cancelAuction_paid_throwsException() {
            Auction auction = buildAuction(AuctionStatus.PAID);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn("admin-001");
                tu.when(() -> TokenUtil.getRole(VALID_TOKEN)).thenReturn("ADMIN");
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                assertEquals("INVALID_STATE",
                        assertThrows(AuctionException.class,
                                () -> service.cancelAuction(AUCTION_ID, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("Huy phien RUNNING co leader -> nha hold truoc khi huy")
        void cancelAuction_running_withLeader_releasesHeld() {
            Auction auction = buildAuction(AuctionStatus.RUNNING);
            auction.setCurrentLeaderId(BIDDER_ID);
            auction.setCurrentLeaderAmount(200_000.0);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn("admin-001");
                tu.when(() -> TokenUtil.getRole(VALID_TOKEN)).thenReturn("ADMIN");
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);

                service.cancelAuction(AUCTION_ID, VALID_TOKEN);

                verify(walletService, times(1))
                        .releaseHeldBalance(BIDDER_ID, 200_000.0, AUCTION_ID);
            }
        }
    }

    // =========================================================
    //  getList()
    // =========================================================
    @Nested
    @DisplayName("getList()")
    class GetListTests {

        @Test
        @DisplayName("Status hop le -> tra ve danh sach dung")
        void getList_validStatus_returnsList() {
            Auction a1 = buildAuction(AuctionStatus.RUNNING);
            Auction a2 = buildAuction(AuctionStatus.RUNNING);
            a2.setId("auction-002");

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                when(auctionDao.findAuctions(eq(AuctionStatus.RUNNING), anyInt(), anyInt()))
                        .thenReturn(List.of(a1, a2));

                List<AuctionResponse> result = service.getList("RUNNING", 0, 10);

                assertEquals(2, result.size());
            }
        }

        @Test
        @DisplayName("Status khong hop le -> nem INVALID_STATUS")
        void getList_invalidStatus_throwsException() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                assertEquals("INVALID_STATUS",
                        assertThrows(AuctionException.class,
                                () -> service.getList("INVALID_XYZ", 0, 10)).getCode());
            }
        }

        @Test
        @DisplayName("Status = ALL -> lay tat ca khong filter")
        void getList_allStatus_noFilter() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);
                AuctionService service = buildService();

                when(auctionDao.findAuctions(isNull(), anyInt(), anyInt()))
                        .thenReturn(List.of());

                service.getList("ALL", 0, 10);

                verify(auctionDao).findAuctions(isNull(), eq(0), eq(10));
            }
        }
    }
}





