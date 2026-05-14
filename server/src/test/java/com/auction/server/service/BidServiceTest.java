package com.auction.server.service;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.BidRequest;
import com.auction.shared.dto.response.BidResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.model.user.Bidder;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho BidService.
 *
 * KEY FIX: BidService khởi tạo AuctionManager và AuctionEventBus
 * ngay trong field declaration → phải tạo new BidService() BÊN TRONG
 * mỗi try-block MockedStatic, sau khi mock Singleton đã được setup.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BidService Tests")
class BidServiceTest {

    @Mock private BidTransactionDAO bidDao;
    @Mock private AuctionDAO        auctionDao;
    @Mock private UserDAO           userDao;
    @Mock private WalletService     walletService;
    @Mock private AuctionManager    auctionManager;
    @Mock private AuctionEventBus   eventBus;

    // KHÔNG khởi tạo bidService ở đây — phải tạo trong từng try-block
    private static final String VALID_TOKEN = "valid-token-abc";
    private static final String BIDDER_ID   = "bidder-001";
    private static final String SELLER_ID   = "seller-999";
    private static final String AUCTION_ID  = "auction-abc";

    private Auction buildRunningAuction() {
        Auction a = new Auction();
        a.setId(AUCTION_ID);
        a.setSellerId(SELLER_ID);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartPrice(100.0);
        a.setCurrentPrice(100.0);
        a.setMinBidIncrement(10.0);
        a.setEndTime(LocalDateTime.now().plusHours(1));
        return a;
    }

    private Bidder buildBidder() {
        Bidder b = new Bidder();
        b.setId(BIDDER_ID);
        b.setUsername("alice");
        return b;
    }

    // =========================================================
    //  Token validation
    // =========================================================
    @Nested
    @DisplayName("placeBid() - Xac thuc Token")
    class TokenValidationTests {

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void placeBid_invalidToken_throwsUnauthorized() {
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                // Tạo BidService SAU KHI mock Singleton đã được setup
                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId("bad-token")).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> bidService.placeBid(req, "bad-token"));

                assertEquals("UNAUTHORIZED", ex.getCode());
                verify(auctionDao, never()).findById(any());
                verify(walletService, never()).holdBalanceForBid(any(), anyDouble(), any());
            }
        }

        @Test
        @DisplayName("Token null -> nem UNAUTHORIZED")
        void placeBid_nullToken_throwsUnauthorized() {
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(null);

                assertEquals("UNAUTHORIZED",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeBid(req, VALID_TOKEN)).getCode());
            }
        }
    }

    // =========================================================
    //  Auction validation
    // =========================================================
    @Nested
    @DisplayName("placeBid() - Kiem tra trang thai phien dau gia")
    class AuctionValidationTests {

        @Test
        @DisplayName("Auction khong ton tai o dau -> nem AUCTION_NOT_FOUND")
        void placeBid_auctionNotFound_throwsException() {
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(null);
                when(auctionDao.findById(AUCTION_ID)).thenReturn(null);

                assertEquals("AUCTION_NOT_FOUND",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeBid(req, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("Cache miss -> fallback xuong DB thanh cong")
        void placeBid_cacheMiss_loadsFromDB() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(null); // Cache miss
                when(auctionDao.findById(AUCTION_ID)).thenReturn(auction);    // DB hit
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                assertNotNull(bidService.placeBid(req, VALID_TOKEN));
            }
        }

        @Test
        @DisplayName("Auction OPEN -> nem AUCTION_CLOSED")
        void placeBid_auctionOpen_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.OPEN);
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                assertEquals("AUCTION_CLOSED",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeBid(req, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("Auction FINISHED -> nem AUCTION_CLOSED")
        void placeBid_auctionFinished_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.FINISHED);
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                assertEquals("AUCTION_CLOSED",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeBid(req, VALID_TOKEN)).getCode());
            }
        }
    }

    // =========================================================
    //  Business rules
    // =========================================================
    @Nested
    @DisplayName("placeBid() - Luat nghiep vu")
    class BusinessRuleTests {

        @Test
        @DisplayName("Seller tu dat gia -> nem INVALID_BID")
        void placeBid_sellerBidsOwnAuction_throwsException() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, SELLER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> bidService.placeBid(req, VALID_TOKEN));

                assertEquals("INVALID_BID", ex.getCode());
                verify(walletService, never()).holdBalanceForBid(any(), anyDouble(), any());
            }
        }

        @Test
        @DisplayName("Bidder khong ton tai -> nem USER_NOT_FOUND")
        void placeBid_bidderNotFound_throwsException() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(null);

                assertEquals("USER_NOT_FOUND",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeBid(req, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("Gia dat thap hon currentPrice + minIncrement -> nem INSUFFICIENT_BID")
        void placeBid_amountTooLow_throwsException() {
            Auction auction = buildRunningAuction(); // currentPrice=100, minIncrement=10 -> can >= 110
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 105.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> bidService.placeBid(req, VALID_TOKEN));

                assertEquals("INSUFFICIENT_BID", ex.getCode());
                verify(walletService, never()).holdBalanceForBid(any(), anyDouble(), any());
            }
        }

        @Test
        @DisplayName("Gia dat dung bang currentPrice -> nem INSUFFICIENT_BID")
        void placeBid_exactCurrentPrice_throwsException() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 100.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                assertEquals("INSUFFICIENT_BID",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeBid(req, VALID_TOKEN)).getCode());
            }
        }

        @Test
        @DisplayName("Leader hien tai tu dat them -> nem INVALID_BID")
        void placeBid_currentLeaderBidsAgain_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setCurrentLeaderId(BIDDER_ID);
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 150.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> bidService.placeBid(req, VALID_TOKEN));

                assertEquals("INVALID_BID", ex.getCode());
                verify(walletService, never()).holdBalanceForBid(any(), anyDouble(), any());
            }
        }
    }

    // =========================================================
    //  Hold Balance
    // =========================================================
    @Nested
    @DisplayName("placeBid() - Hold Balance")
    class HoldBalanceTests {

        @Test
        @DisplayName("Dat gia hop le -> holdBalanceForBid() duoc goi dung 1 lan")
        void placeBid_success_holdsBalance() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeBid(req, VALID_TOKEN);

                verify(walletService, times(1)).holdBalanceForBid(BIDDER_ID, 120.0, AUCTION_ID);
                verifyNoMoreInteractions(walletService);
            }
        }

        @Test
        @DisplayName("Co leader cu -> releaseHeldBalance() cho leader cu")
        void placeBid_withPreviousLeader_releasesHeldBalance() {
            String prevId  = "prev-007";
            double prevAmt = 110.0;
            Auction auction = buildRunningAuction();
            auction.setCurrentLeaderId(prevId);
            auction.setCurrentLeaderAmount(prevAmt);
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeBid(req, VALID_TOKEN);

                verify(walletService, times(1)).releaseHeldBalance(prevId, prevAmt, AUCTION_ID);
            }
        }

        @Test
        @DisplayName("Chua co leader cu -> KHONG goi releaseHeldBalance")
        void placeBid_noPreviousLeader_noRelease() {
            Auction auction = buildRunningAuction();
            auction.setCurrentLeaderId(null);
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeBid(req, VALID_TOKEN);

                verify(walletService, never()).releaseHeldBalance(any(), anyDouble(), any());
            }
        }

        @Test
        @DisplayName("holdBalance nem exception -> bid that bai, DB khong luu")
        void placeBid_holdFails_bidNotSaved() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());
                doThrow(new AuctionException("INSUFFICIENT_BALANCE", "So du khong du"))
                        .when(walletService).holdBalanceForBid(BIDDER_ID, 120.0, AUCTION_ID);

                assertThrows(AuctionException.class, () -> bidService.placeBid(req, VALID_TOKEN));

                verify(bidDao, never()).save(any());
            }
        }
    }

    // =========================================================
    //  Happy path
    // =========================================================
    @Nested
    @DisplayName("placeBid() - Happy path")
    class HappyPathTests {

        @Test
        @DisplayName("Dat gia hop le -> tra ve BidResponse dung thong tin")
        void placeBid_success_returnsCorrectResponse() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                BidResponse resp = bidService.placeBid(req, VALID_TOKEN);

                assertNotNull(resp);
                assertEquals(AUCTION_ID, resp.getAuctionId());
                assertEquals(BIDDER_ID,  resp.getBidderId());
                assertEquals("alice",    resp.getBidderName());
                assertEquals(120.0,      resp.getAmount());
            }
        }

        @Test
        @DisplayName("Dat gia thanh cong -> bidDao.save() va auctionDao.update() duoc goi")
        void placeBid_success_persistsToDatabase() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeBid(req, VALID_TOKEN);

                verify(bidDao, times(1)).save(any(BidTransaction.class));
                verify(auctionDao, atLeastOnce()).update(any(Auction.class));
            }
        }

        @Test
        @DisplayName("Dat gia thanh cong -> eventBus.publishBidPlaced() duoc goi")
        void placeBid_success_broadcastsEvent() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeBid(req, VALID_TOKEN);

                verify(eventBus, times(1)).publishBidPlaced(any(Auction.class), any(BidTransaction.class));
            }
        }
    }

    // =========================================================
    //  Anti-Sniping
    // =========================================================
    @Nested
    @DisplayName("placeBid() - Anti-Sniping")
    class AntiSnipingTests {

        @Test
        @DisplayName("Bid trong 30 giay cuoi -> gia han them 60 giay")
        void placeBid_withinLast30Seconds_extendsAuction() {
            Auction auction = buildRunningAuction();
            auction.setEndTime(LocalDateTime.now().plusSeconds(20));
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                LocalDateTime before = auction.getEndTime();
                bidService.placeBid(req, VALID_TOKEN);

                assertTrue(auction.getEndTime().isAfter(before));
                verify(eventBus, times(1)).publishAuctionExtended(any(), eq(60L));
            }
        }

        @Test
        @DisplayName("Bid khi con nhieu thoi gian -> KHONG gia han")
        void placeBid_withPlentyOfTime_doesNotExtend() {
            Auction auction = buildRunningAuction(); // con 1 tieng
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeBid(req, VALID_TOKEN);

                verify(eventBus, never()).publishAuctionExtended(any(), anyLong());
            }
        }
    }

    // =========================================================
    //  placeSystemBid()
    // =========================================================
    @Nested
    @DisplayName("placeSystemBid() - Auto-Bid")
    class PlaceSystemBidTests {

        @Test
        @DisplayName("System bid hop le -> thanh cong khong can token")
        void placeSystemBid_success() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, true);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                assertDoesNotThrow(() -> bidService.placeSystemBid(req));
                verify(bidDao, times(1)).save(any(BidTransaction.class));
            }
        }

        @Test
        @DisplayName("System bid voi auction CLOSED -> nem AUCTION_CLOSED")
        void placeSystemBid_auctionClosed_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.FINISHED);
            BidRequest req = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, true);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);

                assertEquals("AUCTION_CLOSED",
                        assertThrows(AuctionException.class,
                                () -> bidService.placeSystemBid(req)).getCode());
            }
        }

        @Test
        @DisplayName("placeSystemBid khong goi TokenUtil")
        void placeSystemBid_doesNotUseToken() {
            Auction auction = buildRunningAuction();
            BidRequest req  = new BidRequest(AUCTION_ID, BIDDER_ID, 120.0, true);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class);
                 MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                bidService.placeSystemBid(req);

                tu.verify(() -> TokenUtil.getUserId(anyString()), never());
            }
        }
    }

    // =========================================================
    //  getHistory()
    // =========================================================
    @Nested
    @DisplayName("getHistory()")
    class GetHistoryTests {

        @Test
        @DisplayName("Auction ton tai -> tra ve lich su bid")
        void getHistory_returnsHistory() {
            Auction auction = buildRunningAuction();
            BidTransaction tx = new BidTransaction(
                    "tx-001", AUCTION_ID, BIDDER_ID, "alice", 120.0, LocalDateTime.now(), false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findByAuctionId(AUCTION_ID)).thenReturn(List.of(tx));

                List<BidTransaction> result = bidService.getHistory(AUCTION_ID);

                assertEquals(1, result.size());
                assertEquals(120.0, result.get(0).getAmount());
            }
        }

        @Test
        @DisplayName("Auction khong ton tai -> nem AUCTION_NOT_FOUND")
        void getHistory_auctionNotFound_throwsException() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(null);
                when(auctionDao.findById(AUCTION_ID)).thenReturn(null);

                assertEquals("AUCTION_NOT_FOUND",
                        assertThrows(AuctionException.class,
                                () -> bidService.getHistory(AUCTION_ID)).getCode());
            }
        }

        @Test
        @DisplayName("DAO tra null -> tra ve list rong, khong NPE")
        void getHistory_daoReturnsNull_returnsEmptyList() {
            Auction auction = buildRunningAuction();

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(auctionManager.getAuction(AUCTION_ID)).thenReturn(auction);
                when(bidDao.findByAuctionId(AUCTION_ID)).thenReturn(null);

                List<BidTransaction> result = bidService.getHistory(AUCTION_ID);

                assertNotNull(result);
                assertTrue(result.isEmpty());
            }
        }
    }

    // =========================================================
    //  getAllBids()
    // =========================================================
    @Nested
    @DisplayName("getAllBids() - Admin")
    class GetAllBidsTests {

        @Test
        @DisplayName("Co du lieu -> tra ve toan bo lich su")
        void getAllBids_returnsAllTransactions() {
            BidTransaction tx1 = new BidTransaction("tx-1","auc-A","b1","alice",100.0,LocalDateTime.now(),false);
            BidTransaction tx2 = new BidTransaction("tx-2","auc-B","b2","bob",  200.0,LocalDateTime.now(),false);

            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(bidDao.findAll()).thenReturn(List.of(tx1, tx2));

                assertEquals(2, bidService.getAllBids().size());
            }
        }

        @Test
        @DisplayName("DAO tra null -> tra ve list rong, khong NPE")
        void getAllBids_daoReturnsNull_returnsEmptyList() {
            try (MockedStatic<AuctionManager> ms = mockStatic(AuctionManager.class);
                 MockedStatic<AuctionEventBus> eb = mockStatic(AuctionEventBus.class)) {

                ms.when(AuctionManager::getInstance).thenReturn(auctionManager);
                eb.when(AuctionEventBus::getInstance).thenReturn(eventBus);

                BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);

                when(bidDao.findAll()).thenReturn(null);

                List<BidTransaction> result = bidService.getAllBids();
                assertNotNull(result);
                assertTrue(result.isEmpty());
            }
        }
    }
}