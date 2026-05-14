
package com.auction.server.service;
import com.auction.server.dao.UserDAO;
import com.auction.server.dao.WalletDAO;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.TopUpRequest;
import com.auction.shared.dto.request.WithdrawRequest;
import com.auction.shared.dto.response.WalletResponse;
import com.auction.shared.enums.UserRole;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.model.WalletTransaction;
import com.auction.shared.model.user.Admin;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Tests")
class WalletServiceTest {

    @Mock private WalletDAO walletDao;
    @Mock private UserDAO   userDao;

    private WalletService walletService;

    private static final String VALID_TOKEN = "valid-token";
    private static final String BIDDER_ID   = "bidder-001";
    private static final String SELLER_ID   = "seller-001";
    private static final String ADMIN_ID    = "admin-001";
    private static final String AUCTION_ID  = "auction-abc";

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletDao, userDao);
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────
    private Bidder buildBidder() {
        Bidder b = new Bidder();
        b.setId(BIDDER_ID);
        b.setUsername("alice");
        b.setRole(UserRole.BIDDER);
        return b;
    }

    private Seller buildSeller() {
        Seller s = new Seller();
        s.setId(SELLER_ID);
        s.setUsername("bob");
        s.setRole(UserRole.SELLER);
        return s;
    }

    private Admin buildAdmin() {
        Admin a = new Admin();
        a.setId(ADMIN_ID);
        a.setUsername("admin");
        a.setRole(UserRole.ADMIN);
        return a;
    }

    // =========================================================
    //  getWallet()
    // =========================================================
    @Nested
    @DisplayName("getWallet()")
    class GetWalletTests {

        @Test
        @DisplayName("Token hop le -> tra ve WalletResponse day du thong tin")
        void getWallet_success_returnsFullResponse() {
            WalletTransaction tx = mock(WalletTransaction.class);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());
                when(walletDao.getBalance(BIDDER_ID)).thenReturn(500_000.0);
                when(walletDao.getAvailableBalance(BIDDER_ID)).thenReturn(300_000.0);
                when(walletDao.getTransactions(BIDDER_ID)).thenReturn(List.of(tx));

                WalletResponse resp = walletService.getWallet(VALID_TOKEN);

                assertNotNull(resp);
                assertEquals(BIDDER_ID, resp.getUserId());
                assertEquals("alice", resp.getUsername());
                assertEquals(500_000.0, resp.getBalance());
                assertEquals(300_000.0, resp.getAvailableBalance());
                assertEquals(1, resp.getTransactions().size());
            }
        }

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void getWallet_invalidToken_throwsUnauthorized() {
            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId("bad-token")).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.getWallet("bad-token"));

                assertEquals("UNAUTHORIZED", ex.getCode());
                verify(userDao, never()).findById(any());
            }
        }

        @Test
        @DisplayName("User khong ton tai -> nem USER_NOT_FOUND")
        void getWallet_userNotFound_throwsException() {
            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.getWallet(VALID_TOKEN));

                assertEquals("USER_NOT_FOUND", ex.getCode());
            }
        }
    }

    // =========================================================
    //  topUp()
    // =========================================================
    @Nested
    @DisplayName("topUp()")
    class TopUpTests {

        @Test
        @DisplayName("Bidder nap tien hop le -> tra ve WalletResponse, ghi giao dich")
        void topUp_bidder_success() {
            TopUpRequest req = new TopUpRequest(100_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());
                when(walletDao.credit(BIDDER_ID, 100_000.0)).thenReturn(600_000.0);

                WalletResponse resp = walletService.topUp(req, VALID_TOKEN);

                assertNotNull(resp);
                assertEquals(BIDDER_ID, resp.getUserId());
                assertEquals(600_000.0, resp.getBalance());
                assertEquals(100_000.0, resp.getTransactionAmount());
                assertEquals("TOP_UP", resp.getTransactionType());

                // Xac nhan credit va ghi giao dich duoc goi
                verify(walletDao, times(1)).credit(BIDDER_ID, 100_000.0);
                verify(walletDao, times(1)).saveTransaction(any(WalletTransaction.class));
            }
        }

        @Test
        @DisplayName("Seller nap tien -> nem FORBIDDEN (chi Bidder duoc nap)")
        void topUp_seller_throwsForbidden() {
            TopUpRequest req = new TopUpRequest(100_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.topUp(req, VALID_TOKEN));

                assertEquals("FORBIDDEN", ex.getCode());
                verify(walletDao, never()).credit(any(), anyDouble());
            }
        }

        @Test
        @DisplayName("Admin nap tien -> nem FORBIDDEN")
        void topUp_admin_throwsForbidden() {
            TopUpRequest req = new TopUpRequest(100_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(ADMIN_ID);
                when(userDao.findById(ADMIN_ID)).thenReturn(buildAdmin());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.topUp(req, VALID_TOKEN));

                assertEquals("FORBIDDEN", ex.getCode());
            }
        }

        @Test
        @DisplayName("So tien <= 0 -> nem INVALID_AMOUNT")
        void topUp_zeroAmount_throwsInvalidAmount() {
            TopUpRequest req = new TopUpRequest(0.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.topUp(req, VALID_TOKEN));

                assertEquals("INVALID_AMOUNT", ex.getCode());
                verify(walletDao, never()).credit(any(), anyDouble());
            }
        }

        @Test
        @DisplayName("So tien am -> nem INVALID_AMOUNT")
        void topUp_negativeAmount_throwsInvalidAmount() {
            TopUpRequest req = new TopUpRequest(-50_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.topUp(req, VALID_TOKEN));

                assertEquals("INVALID_AMOUNT", ex.getCode());
            }
        }

        @Test
        @DisplayName("Token khong hop le -> nem UNAUTHORIZED")
        void topUp_invalidToken_throwsUnauthorized() {
            TopUpRequest req = new TopUpRequest(100_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId("bad")).thenReturn(null);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.topUp(req, "bad"));

                assertEquals("UNAUTHORIZED", ex.getCode());
            }
        }
    }

    // =========================================================
    //  withdraw()
    // =========================================================
    @Nested
    @DisplayName("withdraw()")
    class WithdrawTests {

        @Test
        @DisplayName("Seller rut tien hop le -> tra ve WalletResponse, ghi giao dich")
        void withdraw_seller_success() {
            WithdrawRequest req = new WithdrawRequest(200_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(walletDao.getBalance(SELLER_ID)).thenReturn(500_000.0); // Du so du
                when(walletDao.debit(SELLER_ID, 200_000.0)).thenReturn(300_000.0);

                WalletResponse resp = walletService.withdraw(req, VALID_TOKEN);

                assertNotNull(resp);
                assertEquals(300_000.0, resp.getBalance());
                assertEquals(200_000.0, resp.getTransactionAmount());
                assertEquals("WITHDRAW", resp.getTransactionType());

                verify(walletDao, times(1)).debit(SELLER_ID, 200_000.0);
                verify(walletDao, times(1)).saveTransaction(any(WalletTransaction.class));
            }
        }

        @Test
        @DisplayName("Bidder rut tien -> nem FORBIDDEN (chi Seller duoc rut)")
        void withdraw_bidder_throwsForbidden() {
            WithdrawRequest req = new WithdrawRequest(100_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(BIDDER_ID);
                when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.withdraw(req, VALID_TOKEN));

                assertEquals("FORBIDDEN", ex.getCode());
                verify(walletDao, never()).debit(any(), anyDouble());
            }
        }

        @Test
        @DisplayName("So du khong du -> nem INSUFFICIENT_BALANCE")
        void withdraw_insufficientBalance_throwsException() {
            WithdrawRequest req = new WithdrawRequest(1_000_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(walletDao.getBalance(SELLER_ID)).thenReturn(500_000.0); // Chi co 500k

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.withdraw(req, VALID_TOKEN));

                assertEquals("INSUFFICIENT_BALANCE", ex.getCode());
                verify(walletDao, never()).debit(any(), anyDouble());
            }
        }

        @Test
        @DisplayName("So tien rut <= 0 -> nem INVALID_AMOUNT")
        void withdraw_zeroAmount_throwsInvalidAmount() {
            WithdrawRequest req = new WithdrawRequest(0.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.withdraw(req, VALID_TOKEN));

                assertEquals("INVALID_AMOUNT", ex.getCode());
            }
        }

        @Test
        @DisplayName("So tien am -> nem INVALID_AMOUNT")
        void withdraw_negativeAmount_throwsInvalidAmount() {
            WithdrawRequest req = new WithdrawRequest(-100_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.withdraw(req, VALID_TOKEN));

                assertEquals("INVALID_AMOUNT", ex.getCode());
            }
        }

        @Test
        @DisplayName("debit tra ve am (race condition) -> nem INSUFFICIENT_BALANCE")
        void withdraw_debitReturnsNegative_throwsException() {
            WithdrawRequest req = new WithdrawRequest(200_000.0);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDao.findById(SELLER_ID)).thenReturn(buildSeller());
                when(walletDao.getBalance(SELLER_ID)).thenReturn(500_000.0);
                when(walletDao.debit(SELLER_ID, 200_000.0)).thenReturn(-1.0); // Race condition

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> walletService.withdraw(req, VALID_TOKEN));

                assertEquals("INSUFFICIENT_BALANCE", ex.getCode());
            }
        }
    }

    // =========================================================
    //  holdBalanceForBid()
    // =========================================================
    @Nested
    @DisplayName("holdBalanceForBid()")
    class HoldBalanceTests {

        @Test
        @DisplayName("So du kha dung du -> hold thanh cong, ghi giao dich")
        void holdBalance_sufficient_success() {
            when(walletDao.getAvailableBalance(BIDDER_ID)).thenReturn(500_000.0);
            when(walletDao.getBalance(BIDDER_ID)).thenReturn(500_000.0);
            when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

            assertDoesNotThrow(() ->
                    walletService.holdBalanceForBid(BIDDER_ID, 200_000.0, AUCTION_ID));

            verify(walletDao, times(1)).hold(BIDDER_ID, 200_000.0);
            verify(walletDao, times(1)).saveTransaction(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("So du kha dung khong du -> nem INSUFFICIENT_BALANCE, KHONG hold")
        void holdBalance_insufficient_throwsException() {
            when(walletDao.getAvailableBalance(BIDDER_ID)).thenReturn(100_000.0);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> walletService.holdBalanceForBid(BIDDER_ID, 200_000.0, AUCTION_ID));

            assertEquals("INSUFFICIENT_BALANCE", ex.getCode());
            verify(walletDao, never()).hold(any(), anyDouble());
            verify(walletDao, never()).saveTransaction(any());
        }

        @Test
        @DisplayName("So du kha dung bang dung so can -> hold thanh cong (bien ranh gioi)")
        void holdBalance_exactAmount_success() {
            when(walletDao.getAvailableBalance(BIDDER_ID)).thenReturn(200_000.0);
            when(walletDao.getBalance(BIDDER_ID)).thenReturn(200_000.0);
            when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

            assertDoesNotThrow(() ->
                    walletService.holdBalanceForBid(BIDDER_ID, 200_000.0, AUCTION_ID));

            verify(walletDao, times(1)).hold(BIDDER_ID, 200_000.0);
        }

        @Test
        @DisplayName("hold thanh cong -> type giao dich la BID_HOLD, auctionId duoc ghi dung")
        void holdBalance_savesCorrectTransactionType() {
            when(walletDao.getAvailableBalance(BIDDER_ID)).thenReturn(500_000.0);
            when(walletDao.getBalance(BIDDER_ID)).thenReturn(500_000.0);
            when(userDao.findById(BIDDER_ID)).thenReturn(buildBidder());

            walletService.holdBalanceForBid(BIDDER_ID, 120_000.0, AUCTION_ID);

            verify(walletDao).saveTransaction(argThat(tx ->
                    tx.getType() == WalletTransaction.TransactionType.BID_HOLD
                            && AUCTION_ID.equals(tx.getAuctionId())
                            && tx.getAmount() == 120_000.0));
        }
    }

    // =========================================================
    //  releaseHeldBalance()
    // =========================================================
    @Nested
    @DisplayName("releaseHeldBalance()")
    class ReleaseHeldBalanceTests {

        @Test
        @DisplayName("Nha hold hop le -> walletDao.release() va saveTransaction duoc goi")
        void releaseHeld_success() {
            when(walletDao.getBalance(BIDDER_ID)).thenReturn(500_000.0);

            walletService.releaseHeldBalance(BIDDER_ID, 200_000.0, AUCTION_ID);

            verify(walletDao, times(1)).release(BIDDER_ID, 200_000.0);
            verify(walletDao, times(1)).saveTransaction(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("previousLeaderId = null -> khong lam gi (bao ve NullPointer)")
        void releaseHeld_nullLeader_doesNothing() {
            assertDoesNotThrow(() ->
                    walletService.releaseHeldBalance(null, 200_000.0, AUCTION_ID));

            verify(walletDao, never()).release(any(), anyDouble());
            verify(walletDao, never()).saveTransaction(any());
        }

        @Test
        @DisplayName("previousAmount <= 0 -> khong lam gi (guard clause)")
        void releaseHeld_zeroAmount_doesNothing() {
            assertDoesNotThrow(() ->
                    walletService.releaseHeldBalance(BIDDER_ID, 0.0, AUCTION_ID));

            verify(walletDao, never()).release(any(), anyDouble());
        }

        @Test
        @DisplayName("release thanh cong -> type giao dich la BID_RELEASE")
        void releaseHeld_savesCorrectTransactionType() {
            when(walletDao.getBalance(BIDDER_ID)).thenReturn(500_000.0);

            walletService.releaseHeldBalance(BIDDER_ID, 110_000.0, AUCTION_ID);

            verify(walletDao).saveTransaction(argThat(tx ->
                    tx.getType() == WalletTransaction.TransactionType.BID_RELEASE
                            && AUCTION_ID.equals(tx.getAuctionId())
                            && tx.getAmount() == 110_000.0));
        }
    }

    // =========================================================
    //  settleAuction()
    // =========================================================
    @Nested
    @DisplayName("settleAuction()")
    class SettleAuctionTests {

        @Test
        @DisplayName("Thanh toan dau gia thanh cong -> tru winner, cong seller 95%, cong admin 5%")
        void settleAuction_success_correctDistribution() {
            double winnerAmount = 1_000_000.0;
            double expectedSellerReceive = 950_000.0; // 95%
            double expectedCommission    =  50_000.0; // 5%

            when(walletDao.getBalance(BIDDER_ID)).thenReturn(0.0);
            when(walletDao.credit(SELLER_ID, expectedSellerReceive)).thenReturn(950_000.0);
            when(walletDao.credit(ADMIN_ID, expectedCommission)).thenReturn(50_000.0);

            walletService.settleAuction(BIDDER_ID, winnerAmount, SELLER_ID, AUCTION_ID, ADMIN_ID);

            // 1. Winner bi tru
            verify(walletDao, times(1)).debitHeld(BIDDER_ID, winnerAmount);

            // 2. Seller nhan 95%
            verify(walletDao, times(1)).credit(SELLER_ID, expectedSellerReceive);

            // 3. Admin nhan 5%
            verify(walletDao, times(1)).credit(ADMIN_ID, expectedCommission);

            // 4. Ghi dung 3 giao dich: AUCTION_WIN, SELLER_RECEIVE, COMMISSION
            verify(walletDao, times(3)).saveTransaction(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("winnerId = null (khong co nguoi dat gia) -> khong lam gi ca")
        void settleAuction_noWinner_doesNothing() {
            assertDoesNotThrow(() ->
                    walletService.settleAuction(null, 0, SELLER_ID, AUCTION_ID, ADMIN_ID));

            verify(walletDao, never()).debitHeld(any(), anyDouble());
            verify(walletDao, never()).credit(any(), anyDouble());
            verify(walletDao, never()).saveTransaction(any());
        }

        @Test
        @DisplayName("adminId = null -> chi tru winner va cong seller, khong cong commission")
        void settleAuction_noAdmin_skipCommission() {
            double winnerAmount = 1_000_000.0;

            when(walletDao.getBalance(BIDDER_ID)).thenReturn(0.0);
            when(walletDao.credit(SELLER_ID, 950_000.0)).thenReturn(950_000.0);

            walletService.settleAuction(BIDDER_ID, winnerAmount, SELLER_ID, AUCTION_ID, null);

            verify(walletDao, times(1)).debitHeld(BIDDER_ID, winnerAmount);
            verify(walletDao, times(1)).credit(SELLER_ID, 950_000.0);
            // Admin khong ton tai -> credit chi duoc goi 1 lan (cho seller)
            verify(walletDao, times(1)).credit(any(), anyDouble());
            // Chi 2 giao dich: AUCTION_WIN + SELLER_RECEIVE
            verify(walletDao, times(2)).saveTransaction(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("Kiem tra ti le hoa hong chinh xac 5% va seller nhan 95%")
        void settleAuction_commissionRate_isExactly5Percent() {
            double winnerAmount = 2_000_000.0;

            when(walletDao.getBalance(BIDDER_ID)).thenReturn(0.0);
            when(walletDao.credit(eq(SELLER_ID), anyDouble())).thenReturn(1_900_000.0);
            when(walletDao.credit(eq(ADMIN_ID), anyDouble())).thenReturn(100_000.0);

            walletService.settleAuction(BIDDER_ID, winnerAmount, SELLER_ID, AUCTION_ID, ADMIN_ID);

            verify(walletDao).credit(SELLER_ID, 1_900_000.0); // 95%
            verify(walletDao).credit(ADMIN_ID,    100_000.0); // 5%
        }
    }

    // =========================================================
    //  findAdminId()
    // =========================================================
    @Nested
    @DisplayName("findAdminId()")
    class FindAdminIdTests {

        @Test
        @DisplayName("Co admin trong he thong -> tra ve adminId")
        void findAdminId_adminExists_returnsId() {
            Bidder bidder = buildBidder();
            Admin  admin  = buildAdmin();

            when(userDao.findAll()).thenReturn(List.of(bidder, admin));

            String adminId = walletService.findAdminId();

            assertEquals(ADMIN_ID, adminId);
        }

        @Test
        @DisplayName("Khong co admin -> tra ve null")
        void findAdminId_noAdmin_returnsNull() {
            when(userDao.findAll()).thenReturn(List.of(buildBidder(), buildSeller()));

            assertNull(walletService.findAdminId());
        }

        @Test
        @DisplayName("He thong rong -> tra ve null")
        void findAdminId_emptySystem_returnsNull() {
            when(userDao.findAll()).thenReturn(List.of());

            assertNull(walletService.findAdminId());
        }

        @Test
        @DisplayName("Nhieu admin -> tra ve admin dau tien")
        void findAdminId_multipleAdmins_returnsFirst() {
            Admin admin1 = buildAdmin();
            Admin admin2 = new Admin();
            admin2.setId("admin-002");
            admin2.setRole(UserRole.ADMIN);

            when(userDao.findAll()).thenReturn(List.of(buildBidder(), admin1, admin2));

            assertEquals(ADMIN_ID, walletService.findAdminId());
        }
    }
}




