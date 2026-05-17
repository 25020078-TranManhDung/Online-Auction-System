package com.auction.shared.dto;

import com.auction.shared.dto.response.*;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra toàn bộ Response DTO classes:
 *   - AuthResponse
 *   - BidResponse
 *   - AuctionResponse
 *   - AuctionListResponse
 *   - AutoBidResponse
 *   - WalletResponse
 */
@DisplayName("Response DTO – Kiểm tra toàn bộ Response classes")
class ResponseDtoTest {

    // =====================================================================
    // 1. AuthResponse
    // =====================================================================

    @Nested
    @DisplayName("1. AuthResponse – Phản hồi đăng nhập/đăng ký")
    class AuthResponseTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new AuthResponse());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại – cần cho JSON deserialize")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) AuthResponse::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            AuthResponse res = new AuthResponse(
                    "user-001", "alice", UserRole.BIDDER, "token_xyz_abc");

            assertEquals("user-001",      res.getUserId());
            assertEquals("alice",         res.getUsername());
            assertEquals(UserRole.BIDDER, res.getRole());
            assertEquals("token_xyz_abc", res.getToken());
        }

        @Test
        @DisplayName("Setter/Getter userId hoạt động đúng")
        void userId_setAndGet() {
            AuthResponse res = new AuthResponse();
            res.setUserId("user-999");
            assertEquals("user-999", res.getUserId());
        }

        @Test
        @DisplayName("Token không null sau khi đăng nhập thành công")
        void token_notNull_afterLogin() {
            AuthResponse res = new AuthResponse("u-001", "bob", UserRole.SELLER, "jwt.token.here");
            assertNotNull(res.getToken());
            assertFalse(res.getToken().isBlank());
        }

        @Test
        @DisplayName("role = BIDDER khởi tạo đúng")
        void role_bidder_setCorrectly() {
            AuthResponse res = new AuthResponse("u-001", "u", UserRole.BIDDER, "tk");
            assertEquals(UserRole.BIDDER, res.getRole());
        }

        @Test
        @DisplayName("role = SELLER khởi tạo đúng")
        void role_seller_setCorrectly() {
            AuthResponse res = new AuthResponse("u-002", "u", UserRole.SELLER, "tk");
            assertEquals(UserRole.SELLER, res.getRole());
        }

        @Test
        @DisplayName("role = ADMIN khởi tạo đúng")
        void role_admin_setCorrectly() {
            AuthResponse res = new AuthResponse("u-003", "u", UserRole.ADMIN, "tk");
            assertEquals(UserRole.ADMIN, res.getRole());
        }

        @Test
        @DisplayName("setRole / getRole hoạt động đúng")
        void role_setAndGet() {
            AuthResponse res = new AuthResponse();
            res.setRole(UserRole.SELLER);
            assertEquals(UserRole.SELLER, res.getRole());
        }

        @Test
        @DisplayName("Scenario: Server trả AuthResponse sau đăng nhập thành công")
        void scenario_loginSuccess_response() {
            AuthResponse res = new AuthResponse("user-001", "alice", UserRole.BIDDER, "valid_jwt_token");

            assertNotNull(res.getUserId());
            assertNotNull(res.getUsername());
            assertNotNull(res.getRole());
            assertNotNull(res.getToken());
            assertFalse(res.getToken().isBlank(), "Token không được rỗng khi đăng nhập thành công");
        }
    }

    // =====================================================================
    // 2. BidResponse
    // =====================================================================

    @Nested
    @DisplayName("2. BidResponse – Phản hồi sau khi đặt giá")
    class BidResponseTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new BidResponse());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) BidResponse::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            BidResponse res = new BidResponse(
                    "auction-001", "bidder-001", "Alice", 1_500_000.0, 1_500_000.0);

            assertEquals("auction-001",  res.getAuctionId());
            assertEquals("bidder-001",   res.getBidderId());
            assertEquals("Alice",        res.getBidderName());
            assertEquals(1_500_000.0,    res.getAmount(), 0.001);
            assertEquals(1_500_000.0,    res.getNewCurrentPrice(), 0.001);
        }

        @Test
        @DisplayName("timestamp được set tự động (không null) sau khi tạo qua constructor đầy đủ")
        void timestamp_autoSetByConstructor() {
            BidResponse res = new BidResponse("a-001", "b-001", "Bob", 2_000_000.0, 2_000_000.0);
            assertNotNull(res.getTimestamp(), "Timestamp phải được tự động gán khi tạo BidResponse");
        }

        @Test
        @DisplayName("timestamp có thể set thủ công qua setter")
        void timestamp_setManually() {
            LocalDateTime ts = LocalDateTime.of(2026, 5, 17, 20, 0, 0);
            BidResponse res = new BidResponse();
            res.setTimestamp(ts);
            assertEquals(ts, res.getTimestamp());
        }

        @Test
        @DisplayName("newCurrentPrice phản ánh giá sau khi bid thành công")
        void newCurrentPrice_reflectsBidAmount() {
            BidResponse res = new BidResponse(
                    "a-001", "b-001", "Carol", 3_000_000.0, 3_000_000.0);
            assertEquals(res.getAmount(), res.getNewCurrentPrice(), 0.001,
                    "Giá mới phải bằng amount khi đây là bid hợp lệ");
        }

        @Test
        @DisplayName("Setter/Getter tất cả field hoạt động đúng")
        void allSettersGetters_work() {
            BidResponse res = new BidResponse();
            res.setAuctionId("a-x");
            res.setBidderId("b-x");
            res.setBidderName("Dave");
            res.setAmount(4_000_000.0);
            res.setNewCurrentPrice(4_000_000.0);

            assertEquals("a-x",        res.getAuctionId());
            assertEquals("b-x",        res.getBidderId());
            assertEquals("Dave",       res.getBidderName());
            assertEquals(4_000_000.0,  res.getAmount(), 0.001);
            assertEquals(4_000_000.0,  res.getNewCurrentPrice(), 0.001);
        }

        @Test
        @DisplayName("Scenario: Server broadcast BidResponse đến tất cả client sau bid thành công")
        void scenario_serverBroadcastBidResponse() {
            BidResponse broadcast = new BidResponse(
                    "auction-001", "bidder-002", "Bob", 2_500_000.0, 2_500_000.0);

            // Client nhận response và cập nhật UI
            assertNotNull(broadcast.getAuctionId());
            assertNotNull(broadcast.getBidderName());
            assertTrue(broadcast.getNewCurrentPrice() > 0,
                    "Giá mới phải dương để client cập nhật UI");
            assertNotNull(broadcast.getTimestamp(),
                    "Timestamp cần có để hiển thị biểu đồ giá realtime");
        }
    }

    // =====================================================================
    // 3. AuctionResponse
    // =====================================================================

    @Nested
    @DisplayName("3. AuctionResponse – Thông tin chi tiết phiên đấu giá")
    class AuctionResponseTests {

        private final LocalDateTime START = LocalDateTime.now().minusMinutes(10);
        private final LocalDateTime END   = LocalDateTime.now().plusMinutes(50);

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new AuctionResponse());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) AuctionResponse::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            AuctionResponse res = new AuctionResponse(
                    "auction-001", "iPhone 15 Pro", "Mô tả sản phẩm", "ELECTRONICS",
                    20_000_000.0, 22_000_000.0, "Alice",
                    START, END, AuctionStatus.RUNNING);

            assertEquals("auction-001",        res.getAuctionId());
            assertEquals("iPhone 15 Pro",      res.getTitle());
            assertEquals("Mô tả sản phẩm",     res.getDescription());
            assertEquals("ELECTRONICS",         res.getCategory());
            assertEquals(20_000_000.0,          res.getStartingPrice(), 0.001);
            assertEquals(22_000_000.0,          res.getCurrentPrice(), 0.001);
            assertEquals("Alice",               res.getHighestBidderName());
            assertEquals(START,                 res.getStartTime());
            assertEquals(END,                   res.getEndTime());
            assertEquals(AuctionStatus.RUNNING, res.getStatus());
        }

        @Test
        @DisplayName("timeRemaining: set và get đúng số giây còn lại")
        void timeRemaining_setAndGet() {
            AuctionResponse res = new AuctionResponse();
            res.setTimeRemaining(1800L); // 30 phút
            assertEquals(1800L, res.getTimeRemaining());
        }

        @Test
        @DisplayName("bidCount: set và get đúng số lượt đặt giá")
        void bidCount_setAndGet() {
            AuctionResponse res = new AuctionResponse();
            res.setBidCount(42);
            assertEquals(42, res.getBidCount());
        }

        @Test
        @DisplayName("minBidIncrement: set và get đúng bước giá tối thiểu")
        void minBidIncrement_setAndGet() {
            AuctionResponse res = new AuctionResponse();
            res.setMinBidIncrement(50_000.0);
            assertEquals(50_000.0, res.getMinBidIncrement(), 0.001);
        }

        @Test
        @DisplayName("winnerId = null khi phiên đang RUNNING")
        void winnerId_nullWhenRunning() {
            AuctionResponse res = new AuctionResponse(
                    "a-001", "t", "d", "CAT",
                    1_000_000.0, 1_500_000.0, "Alice",
                    START, END, AuctionStatus.RUNNING);
            assertNull(res.getWinnerId(), "winnerId phải null khi phiên chưa kết thúc");
        }

        @Test
        @DisplayName("winnerId được set khi phiên FINISHED")
        void winnerId_setWhenFinished() {
            AuctionResponse res = new AuctionResponse();
            res.setStatus(AuctionStatus.FINISHED);
            res.setWinnerId("bidder-winner-001");
            assertEquals("bidder-winner-001", res.getWinnerId());
            assertEquals(AuctionStatus.FINISHED, res.getStatus());
        }

        @Test
        @DisplayName("sellerId: set và get đúng")
        void sellerId_setAndGet() {
            AuctionResponse res = new AuctionResponse();
            res.setSellerId("seller-001");
            assertEquals("seller-001", res.getSellerId());
        }

        @Test
        @DisplayName("recentBids: set và get list đúng")
        void recentBids_setAndGet() {
            AuctionResponse res = new AuctionResponse();
            List<Object> bids = List.of("bid1", "bid2", "bid3");
            res.setRecentBids(bids);
            assertEquals(3, res.getRecentBids().size());
        }

        @Test
        @DisplayName("currentPrice sử dụng wrapper Double – có thể null (chưa có bid)")
        void currentPrice_canBeNull() {
            AuctionResponse res = new AuctionResponse();
            assertNull(res.getCurrentPrice(), "currentPrice = null khi chưa có bid nào");
        }

        @Test
        @DisplayName("Vòng đời trạng thái: OPEN → RUNNING → FINISHED")
        void statusLifecycle_openToFinished() {
            AuctionResponse res = new AuctionResponse();

            res.setStatus(AuctionStatus.OPEN);
            assertEquals(AuctionStatus.OPEN, res.getStatus());

            res.setStatus(AuctionStatus.RUNNING);
            assertEquals(AuctionStatus.RUNNING, res.getStatus());

            res.setStatus(AuctionStatus.FINISHED);
            assertEquals(AuctionStatus.FINISHED, res.getStatus());
        }

        @Test
        @DisplayName("Scenario: Client nhận AuctionResponse để hiển thị màn hình đấu giá realtime")
        void scenario_clientDisplaysAuctionScreen() {
            AuctionResponse res = new AuctionResponse(
                    "auction-001", "MacBook Pro M3", "Laptop cao cấp", "ELECTRONICS",
                    40_000_000.0, 42_000_000.0, "Bob",
                    START, END, AuctionStatus.RUNNING);
            res.setTimeRemaining(1200L);    // 20 phút còn lại
            res.setMinBidIncrement(500_000.0);
            res.setBidCount(15);

            // Client dùng các giá trị này để render UI
            assertNotNull(res.getAuctionId());
            assertNotNull(res.getTitle());
            assertNotNull(res.getCurrentPrice());
            assertNotNull(res.getHighestBidderName());
            assertTrue(res.getTimeRemaining() > 0);
            assertTrue(res.getMinBidIncrement() > 0);
            assertTrue(res.getBidCount() >= 0);
        }
    }

    // =====================================================================
    // 4. AuctionListResponse
    // =====================================================================

    @Nested
    @DisplayName("4. AuctionListResponse – Danh sách phiên đấu giá")
    class AuctionListResponseTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new AuctionListResponse());
        }

        @Test
        @DisplayName("Constructor rỗng: auctions khởi tạo là ArrayList rỗng (không null)")
        void defaultConstructor_auctionsNotNull() {
            AuctionListResponse res = new AuctionListResponse();
            assertNotNull(res.getAuctions(),
                    "auctions phải được khởi tạo là empty list, không phải null");
            assertTrue(res.getAuctions().isEmpty());
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng danh sách và message")
        void fullConstructor_setsListAndMessage() {
            List<AuctionResponse> list = List.of(
                    new AuctionResponse("a1", "t1", "d", "ELECTRONICS",
                            1_000_000.0, 1_500_000.0, "Alice",
                            LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                            AuctionStatus.RUNNING),
                    new AuctionResponse("a2", "t2", "d", "ART",
                            5_000_000.0, 5_500_000.0, "Bob",
                            LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                            AuctionStatus.RUNNING)
            );
            AuctionListResponse res = new AuctionListResponse(list, "Tải thành công 2 phiên");

            assertEquals(2,                       res.getAuctions().size());
            assertEquals("Tải thành công 2 phiên", res.getMessage());
        }

        @Test
        @DisplayName("setAuctions / getAuctions hoạt động đúng")
        void auctions_setAndGet() {
            AuctionListResponse res = new AuctionListResponse();
            List<AuctionResponse> list = List.of(new AuctionResponse());
            res.setAuctions(list);
            assertEquals(1, res.getAuctions().size());
        }

        @Test
        @DisplayName("setMessage / getMessage hoạt động đúng")
        void message_setAndGet() {
            AuctionListResponse res = new AuctionListResponse();
            res.setMessage("Không có phiên đấu giá nào đang hoạt động");
            assertEquals("Không có phiên đấu giá nào đang hoạt động", res.getMessage());
        }

        @Test
        @DisplayName("Scenario: Server trả danh sách rỗng khi chưa có phiên nào")
        void scenario_emptyAuctionList() {
            AuctionListResponse res = new AuctionListResponse(List.of(), "Không có phiên nào");
            assertTrue(res.getAuctions().isEmpty());
            assertNotNull(res.getMessage());
        }

        @Test
        @DisplayName("Scenario: Client lọc phiên RUNNING từ danh sách trả về")
        void scenario_filterRunningAuctions() {
            AuctionResponse running = new AuctionResponse();
            running.setStatus(AuctionStatus.RUNNING);

            AuctionResponse finished = new AuctionResponse();
            finished.setStatus(AuctionStatus.FINISHED);

            AuctionListResponse res = new AuctionListResponse(
                    List.of(running, finished), "OK");

            long runningCount = res.getAuctions().stream()
                    .filter(a -> a.getStatus() == AuctionStatus.RUNNING).count();
            assertEquals(1, runningCount, "Chỉ có 1 phiên RUNNING trong danh sách");
        }
    }

    // =====================================================================
    // 5. AutoBidResponse
    // =====================================================================

    @Nested
    @DisplayName("5. AutoBidResponse – Phản hồi cài đặt Auto-Bid")
    class AutoBidResponseTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new AutoBidResponse());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) AutoBidResponse::new);
        }

        @Test
        @DisplayName("Constructor rút gọn (success, message) khởi tạo đúng")
        void shortConstructor_setsSuccessAndMessage() {
            AutoBidResponse res = new AutoBidResponse(true, "Auto-bid đã được kích hoạt");
            assertTrue(res.isSuccess());
            assertEquals("Auto-bid đã được kích hoạt", res.getMessage());
        }

        @Test
        @DisplayName("Constructor rút gọn với success=false – thất bại")
        void shortConstructor_failure() {
            AutoBidResponse res = new AutoBidResponse(false, "Giá tối đa đã vượt qua giá hiện tại");
            assertFalse(res.isSuccess());
            assertNotNull(res.getMessage());
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            AutoBidResponse res = new AutoBidResponse(
                    true, "Cài đặt thành công",
                    "auction-001", 5_000_000.0, 3_000_000.0, false);

            assertTrue(res.isSuccess());
            assertEquals("auction-001",  res.getAuctionId());
            assertEquals(5_000_000.0,    res.getMaxBid(), 0.001);
            assertEquals(3_000_000.0,    res.getCurrentPrice(), 0.001);
            assertFalse(res.isAlreadyWinning(), "Chưa dẫn đầu ngay lúc cài đặt");
        }

        @Test
        @DisplayName("alreadyWinning = true khi user đang dẫn đầu ngay lúc cài auto-bid")
        void alreadyWinning_true() {
            AutoBidResponse res = new AutoBidResponse(
                    true, "Bạn đang dẫn đầu",
                    "auction-001", 5_000_000.0, 5_000_000.0, true);
            assertTrue(res.isAlreadyWinning());
        }

        @Test
        @DisplayName("Setter/Getter tất cả field hoạt động đúng")
        void allSettersGetters_work() {
            AutoBidResponse res = new AutoBidResponse();
            res.setSuccess(true);
            res.setMessage("OK");
            res.setAuctionId("a-001");
            res.setMaxBid(8_000_000.0);
            res.setCurrentPrice(4_000_000.0);
            res.setAlreadyWinning(false);

            assertTrue(res.isSuccess());
            assertEquals("OK",          res.getMessage());
            assertEquals("a-001",       res.getAuctionId());
            assertEquals(8_000_000.0,   res.getMaxBid(), 0.001);
            assertEquals(4_000_000.0,   res.getCurrentPrice(), 0.001);
            assertFalse(res.isAlreadyWinning());
        }

        @Test
        @DisplayName("Scenario: Cài auto-bid thành công, chưa dẫn đầu ngay")
        void scenario_autoBidSetupSuccess_notYetWinning() {
            AutoBidResponse res = new AutoBidResponse(
                    true, "Auto-bid kích hoạt thành công",
                    "auction-001", 5_000_000.0, 3_500_000.0, false);

            assertTrue(res.isSuccess());
            assertTrue(res.getMaxBid() > res.getCurrentPrice(),
                    "maxBid phải lớn hơn giá hiện tại – auto-bid còn có thể kích hoạt");
            assertFalse(res.isAlreadyWinning());
        }

        @Test
        @DisplayName("Scenario: Cài auto-bid thất bại vì maxBid <= currentPrice")
        void scenario_autoBidFailed_maxBidTooLow() {
            AutoBidResponse res = new AutoBidResponse(false, "maxBid phải lớn hơn giá hiện tại");
            assertFalse(res.isSuccess());
            assertNotNull(res.getMessage());
        }
    }

    // =====================================================================
    // 6. WalletResponse
    // =====================================================================

    @Nested
    @DisplayName("6. WalletResponse – Phản hồi thông tin ví điện tử")
    class WalletResponseTests {

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) WalletResponse::new);
        }

        @Test
        @DisplayName("Setter/Getter userId và username hoạt động đúng")
        void userInfo_setAndGet() {
            WalletResponse res = new WalletResponse();
            res.setUserId("user-001");
            res.setUsername("alice");

            assertEquals("user-001", res.getUserId());
            assertEquals("alice",    res.getUsername());
        }

        @Test
        @DisplayName("balance: set và get đúng số dư")
        void balance_setAndGet() {
            WalletResponse res = new WalletResponse();
            res.setBalance(10_000_000.0);
            assertEquals(10_000_000.0, res.getBalance(), 0.001);
        }

        @Test
        @DisplayName("availableBalance: set và get đúng số dư khả dụng")
        void availableBalance_setAndGet() {
            WalletResponse res = new WalletResponse();
            res.setBalance(10_000_000.0);
            res.setAvailableBalance(7_000_000.0); // 3M đang bị hold

            assertEquals(10_000_000.0, res.getBalance(), 0.001);
            assertEquals(7_000_000.0,  res.getAvailableBalance(), 0.001);
        }

        @Test
        @DisplayName("availableBalance <= balance (số dư khả dụng không vượt tổng số dư)")
        void availableBalance_lessOrEqual_balance() {
            WalletResponse res = new WalletResponse();
            res.setBalance(10_000_000.0);
            res.setAvailableBalance(7_000_000.0);

            assertTrue(res.getAvailableBalance() <= res.getBalance(),
                    "availableBalance không được vượt quá tổng balance");
        }

        @Test
        @DisplayName("transactionAmount và transactionType: set và get đúng")
        void transactionInfo_setAndGet() {
            WalletResponse res = new WalletResponse();
            res.setTransactionAmount(2_000_000.0);
            res.setTransactionType("TOP_UP");

            assertEquals(2_000_000.0, res.getTransactionAmount(), 0.001);
            assertEquals("TOP_UP",    res.getTransactionType());
        }

        @Test
        @DisplayName("message: set và get đúng")
        void message_setAndGet() {
            WalletResponse res = new WalletResponse();
            res.setMessage("Nạp tiền thành công");
            assertEquals("Nạp tiền thành công", res.getMessage());
        }

        @Test
        @DisplayName("transactions: set và get list đúng")
        void transactions_setAndGet() {
            WalletResponse res = new WalletResponse();
            assertNull(res.getTransactions(), "transactions null khi chưa set (nullable)");
            // set danh sách rỗng
            res.setTransactions(List.of());
            assertNotNull(res.getTransactions());
            assertTrue(res.getTransactions().isEmpty());
        }

        @Test
        @DisplayName("Scenario: Bidder nạp 2 triệu – balance tăng đúng")
        void scenario_bidderTopUp_balanceIncreases() {
            WalletResponse res = new WalletResponse();
            double oldBalance   = 5_000_000.0;
            double topUpAmount  = 2_000_000.0;

            res.setBalance(oldBalance + topUpAmount);
            res.setAvailableBalance(oldBalance + topUpAmount);
            res.setTransactionAmount(topUpAmount);
            res.setTransactionType("TOP_UP");
            res.setMessage("Nạp 2,000,000đ thành công");

            assertEquals(7_000_000.0, res.getBalance(), 0.001);
            assertEquals("TOP_UP",    res.getTransactionType());
            assertNotNull(res.getMessage());
        }

        @Test
        @DisplayName("Scenario: Seller rút tiền – availableBalance giảm đúng")
        void scenario_sellerWithdraw_balanceDecreases() {
            WalletResponse res = new WalletResponse();
            res.setBalance(10_000_000.0);
            res.setAvailableBalance(8_000_000.0);   // 2M đang bị hold
            res.setTransactionAmount(3_000_000.0);
            res.setTransactionType("WITHDRAW");
            res.setMessage("Rút 3,000,000đ thành công");

            assertEquals("WITHDRAW",   res.getTransactionType());
            assertTrue(res.getTransactionAmount() <= res.getAvailableBalance(),
                    "Không được rút nhiều hơn số dư khả dụng");
        }

        @Test
        @DisplayName("Scenario: Xem lịch sử ví – balance và availableBalance không bằng nhau khi có tiền hold")
        void scenario_viewWallet_withHeldFunds() {
            WalletResponse res = new WalletResponse();
            res.setUserId("user-001");
            res.setUsername("alice");
            res.setBalance(10_000_000.0);
            res.setAvailableBalance(6_000_000.0); // 4M đang hold do đang đấu giá

            assertNotEquals(res.getBalance(), res.getAvailableBalance(),
                    "balance và availableBalance khác nhau khi có tiền đang hold");
            double holdAmount = res.getBalance() - res.getAvailableBalance();
            assertEquals(4_000_000.0, holdAmount, 0.001, "Tiền đang hold = 4 triệu");
        }
    }
}