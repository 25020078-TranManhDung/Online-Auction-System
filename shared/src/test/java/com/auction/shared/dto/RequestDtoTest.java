package com.auction.shared.dto;

import com.auction.shared.dto.request.*;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra toàn bộ Request DTO classes:
 *   - LoginRequest
 *   - RegisterRequest
 *   - BidRequest
 *   - AutoBidRequest
 *   - CreateAuctionRequest
 *   - UpdateItemRequest
 *   - DeleteItemRequest
 *   - TopUpRequest
 *   - WithdrawRequest
 */
@DisplayName("Request DTO – Kiểm tra toàn bộ Request classes")
class RequestDtoTest {

    // =====================================================================
    // 1. LoginRequest
    // =====================================================================

    @Nested
    @DisplayName("1. LoginRequest")
    class LoginRequestTests {

        @Test
        @DisplayName("Implements Serializable – có thể truyền qua Socket")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new LoginRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại – cần cho JSON deserialize")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) LoginRequest::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng username và password")
        void fullConstructor_setsFields() {
            LoginRequest req = new LoginRequest("alice", "pass123");
            assertEquals("alice",   req.getUsername());
            assertEquals("pass123", req.getPassword());
        }

        @Test
        @DisplayName("Setter/Getter username hoạt động đúng")
        void username_setAndGet() {
            LoginRequest req = new LoginRequest();
            req.setUsername("bob");
            assertEquals("bob", req.getUsername());
        }

        @Test
        @DisplayName("Setter/Getter password hoạt động đúng")
        void password_setAndGet() {
            LoginRequest req = new LoginRequest();
            req.setPassword("secret999");
            assertEquals("secret999", req.getPassword());
        }

        @Test
        @DisplayName("Scenario: Client tạo LoginRequest để gửi lên Server")
        void scenario_clientCreatesLoginRequest() {
            LoginRequest req = new LoginRequest("charlie", "myPassword");
            assertNotNull(req.getUsername());
            assertNotNull(req.getPassword());
            assertFalse(req.getUsername().isBlank());
            assertFalse(req.getPassword().isBlank());
        }
    }

    // =====================================================================
    // 2. RegisterRequest
    // =====================================================================

    @Nested
    @DisplayName("2. RegisterRequest")
    class RegisterRequestTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new RegisterRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) RegisterRequest::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            RegisterRequest req = new RegisterRequest(
                    "alice", "pass123", "alice@email.com", UserRole.BIDDER);

            assertEquals("alice",           req.getUsername());
            assertEquals("pass123",         req.getPassword());
            assertEquals("alice@email.com", req.getEmail());
            assertEquals(UserRole.BIDDER,   req.getRole());
        }

        @Test
        @DisplayName("setFullname / getFullname hoạt động đúng")
        void fullname_setAndGet() {
            RegisterRequest req = new RegisterRequest();
            req.setFullname("Nguyễn Văn A");
            assertEquals("Nguyễn Văn A", req.getFullname());
        }

        @Test
        @DisplayName("Đăng ký với role SELLER hoạt động đúng")
        void role_seller_setAndGet() {
            RegisterRequest req = new RegisterRequest(
                    "bob", "pw", "bob@e.com", UserRole.SELLER);
            assertEquals(UserRole.SELLER, req.getRole());
        }

        @Test
        @DisplayName("Đăng ký với role ADMIN hoạt động đúng")
        void role_admin_setAndGet() {
            RegisterRequest req = new RegisterRequest();
            req.setRole(UserRole.ADMIN);
            assertEquals(UserRole.ADMIN, req.getRole());
        }

        @Test
        @DisplayName("setEmail / getEmail hoạt động đúng")
        void email_setAndGet() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@email.com");
            assertEquals("new@email.com", req.getEmail());
        }

        @Test
        @DisplayName("Scenario: đăng ký Bidder đầy đủ thông tin")
        void scenario_fullBidderRegistration() {
            RegisterRequest req = new RegisterRequest(
                    "david", "dPass", "david@e.com", UserRole.BIDDER);
            req.setFullname("David Nguyen");

            assertEquals("david",       req.getUsername());
            assertEquals("dPass",       req.getPassword());
            assertEquals("david@e.com", req.getEmail());
            assertEquals(UserRole.BIDDER, req.getRole());
            assertEquals("David Nguyen", req.getFullname());
        }
    }

    // =====================================================================
    // 3. BidRequest
    // =====================================================================

    @Nested
    @DisplayName("3. BidRequest")
    class BidRequestTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new BidRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) BidRequest::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            BidRequest req = new BidRequest(
                    "auction-001", "bidder-001", 1_500_000.0, false);

            assertEquals("auction-001",  req.getAuctionId());
            assertEquals("bidder-001",   req.getBidderId());
            assertEquals(1_500_000.0,    req.getAmount(), 0.001);
            assertFalse(req.isAutoBid());
        }

        @Test
        @DisplayName("isAutoBid = true khi là auto-bid")
        void autoBid_flag_true() {
            BidRequest req = new BidRequest("a-001", "b-001", 2_000_000.0, true);
            assertTrue(req.isAutoBid());
        }

        @Test
        @DisplayName("setAutoBid / isAutoBid hoạt động đúng")
        void autoBid_setAndGet() {
            BidRequest req = new BidRequest();
            req.setAutoBid(true);
            assertTrue(req.isAutoBid());
            req.setAutoBid(false);
            assertFalse(req.isAutoBid());
        }

        @Test
        @DisplayName("setAmount / getAmount với giá trị lớn (VND) hoạt động đúng")
        void amount_largeValue_setAndGet() {
            BidRequest req = new BidRequest();
            req.setAmount(999_999_999.0);
            assertEquals(999_999_999.0, req.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Setter/Getter auctionId và bidderId hoạt động đúng")
        void ids_setAndGet() {
            BidRequest req = new BidRequest();
            req.setAuctionId("auction-xyz");
            req.setBidderId("bidder-abc");
            assertEquals("auction-xyz", req.getAuctionId());
            assertEquals("bidder-abc",  req.getBidderId());
        }

        @Test
        @DisplayName("Scenario: Bidder đặt giá thủ công 1.5 triệu VND")
        void scenario_manualBid() {
            BidRequest req = new BidRequest("a-001", "b-001", 1_500_000.0, false);

            assertEquals("a-001",       req.getAuctionId());
            assertEquals("b-001",       req.getBidderId());
            assertEquals(1_500_000.0,   req.getAmount(), 0.001);
            assertFalse(req.isAutoBid(), "Đây là bid thủ công, không phải auto-bid");
        }

        @Test
        @DisplayName("Scenario: Hệ thống tạo auto-bid thay người dùng")
        void scenario_autoBid() {
            BidRequest req = new BidRequest("a-001", "b-002", 2_000_000.0, true);
            assertTrue(req.isAutoBid(), "Auto-bid phải được đánh dấu đúng");
        }
    }

    // =====================================================================
    // 4. AutoBidRequest
    // =====================================================================

    @Nested
    @DisplayName("4. AutoBidRequest")
    class AutoBidRequestTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new AutoBidRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) AutoBidRequest::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng maxBidAmount và incrementAmount")
        void fullConstructor_setsAllFields() {
            AutoBidRequest req = new AutoBidRequest(
                    "auction-001", "bidder-001", 5_000_000.0, 100_000.0);

            assertEquals("auction-001",  req.getAuctionId());
            assertEquals("bidder-001",   req.getBidderId());
            assertEquals(5_000_000.0,    req.getMaxBidAmount(), 0.001);
            assertEquals(100_000.0,      req.getIncrementAmount(), 0.001);
        }

        @Test
        @DisplayName("setMaxBidAmount / getMaxBidAmount hoạt động đúng")
        void maxBidAmount_setAndGet() {
            AutoBidRequest req = new AutoBidRequest();
            req.setMaxBidAmount(10_000_000.0);
            assertEquals(10_000_000.0, req.getMaxBidAmount(), 0.001);
        }

        @Test
        @DisplayName("setIncrementAmount / getIncrementAmount hoạt động đúng")
        void incrementAmount_setAndGet() {
            AutoBidRequest req = new AutoBidRequest();
            req.setIncrementAmount(50_000.0);
            assertEquals(50_000.0, req.getIncrementAmount(), 0.001);
        }

        @Test
        @DisplayName("maxBidAmount lớn hơn incrementAmount – hợp lệ về mặt nghiệp vụ")
        void maxBid_greaterThan_increment_isValid() {
            AutoBidRequest req = new AutoBidRequest(
                    "a-001", "b-001", 5_000_000.0, 100_000.0);
            assertTrue(req.getMaxBidAmount() > req.getIncrementAmount(),
                    "maxBidAmount phải luôn lớn hơn incrementAmount");
        }

        @Test
        @DisplayName("Scenario: Bidder cài auto-bid tối đa 5 triệu, bước 100 nghìn")
        void scenario_autoBidSetup() {
            AutoBidRequest req = new AutoBidRequest(
                    "auction-001", "bidder-001", 5_000_000.0, 100_000.0);

            assertNotNull(req.getAuctionId());
            assertNotNull(req.getBidderId());
            assertTrue(req.getMaxBidAmount() > 0, "maxBidAmount phải dương");
            assertTrue(req.getIncrementAmount() > 0, "incrementAmount phải dương");
        }
    }

    // =====================================================================
    // 5. CreateAuctionRequest
    // =====================================================================

    @Nested
    @DisplayName("5. CreateAuctionRequest")
    class CreateAuctionRequestTests {

        private final LocalDateTime START = LocalDateTime.now().plusHours(1);
        private final LocalDateTime END   = LocalDateTime.now().plusHours(3);

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new CreateAuctionRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) CreateAuctionRequest::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            Map<String, Object> attrs = Map.of("brand", "Apple", "model", "iPhone 15");
            CreateAuctionRequest req = new CreateAuctionRequest(
                    "seller-001", "iPhone 15 Pro", "Mô tả sản phẩm",
                    20_000_000.0, ItemCategory.ELECTRONICS, START, END, attrs);

            assertEquals("seller-001",          req.getSellerId());
            assertEquals("iPhone 15 Pro",       req.getTitle());
            assertEquals("Mô tả sản phẩm",      req.getDescription());
            assertEquals(20_000_000.0,           req.getStartingPrice(), 0.001);
            assertEquals(ItemCategory.ELECTRONICS, req.getCategory());
            assertEquals(START,                  req.getStartTime());
            assertEquals(END,                    req.getEndTime());
            assertEquals("Apple",                req.getItemAttributes().get("brand"));
        }

        @Test
        @DisplayName("durationMinutes mặc định là 60 khi chưa set")
        void durationMinutes_default_is60() {
            CreateAuctionRequest req = new CreateAuctionRequest();
            assertEquals(60, req.getDurationMinutes(),
                    "durationMinutes mặc định phải là 60 phút");
        }

        @Test
        @DisplayName("setDurationMinutes / getDurationMinutes hoạt động đúng")
        void durationMinutes_setAndGet() {
            CreateAuctionRequest req = new CreateAuctionRequest();
            req.setDurationMinutes(120);
            assertEquals(120, req.getDurationMinutes());
        }

        @Test
        @DisplayName("durationMinutes = 0 hoặc âm → getDurationMinutes() trả về 60 (fallback)")
        void durationMinutes_zeroOrNegative_fallbackTo60() {
            CreateAuctionRequest req = new CreateAuctionRequest();
            req.setDurationMinutes(0);
            assertEquals(60, req.getDurationMinutes(),
                    "Khi durationMinutes <= 0 phải fallback về 60");
            req.setDurationMinutes(-10);
            assertEquals(60, req.getDurationMinutes());
        }

        @Test
        @DisplayName("setMinBidIncrement / getMinBidIncrement hoạt động đúng")
        void minBidIncrement_setAndGet() {
            CreateAuctionRequest req = new CreateAuctionRequest();
            req.setMinBidIncrement(50_000.0);
            assertEquals(50_000.0, req.getMinBidIncrement(), 0.001);
        }

        @Test
        @DisplayName("itemAttributes nhận Map linh hoạt – Art attributes")
        void itemAttributes_artCategory() {
            Map<String, Object> attrs = Map.of(
                    "artist", "Van Gogh", "medium", "Oil", "yearCreated", 1889);
            CreateAuctionRequest req = new CreateAuctionRequest(
                    "seller-001", "Starry Night", "desc",
                    100_000_000.0, ItemCategory.ART, START, END, attrs);

            assertEquals("Van Gogh", req.getItemAttributes().get("artist"));
            assertEquals("Oil",      req.getItemAttributes().get("medium"));
            assertEquals(1889,       req.getItemAttributes().get("yearCreated"));
        }

        @Test
        @DisplayName("itemAttributes nhận Map linh hoạt – Vehicle attributes")
        void itemAttributes_vehicleCategory() {
            Map<String, Object> attrs = Map.of(
                    "make", "Toyota", "vehicleModel", "Camry", "year", 2022, "mileage", 15000);
            CreateAuctionRequest req = new CreateAuctionRequest(
                    "seller-001", "Toyota Camry 2022", "desc",
                    500_000_000.0, ItemCategory.VEHICLE, START, END, attrs);

            assertEquals(ItemCategory.VEHICLE, req.getCategory());
            assertEquals("Toyota",             req.getItemAttributes().get("make"));
            assertEquals(15000,                req.getItemAttributes().get("mileage"));
        }

        @Test
        @DisplayName("Scenario: Seller tạo phiên đấu giá Electronics đầy đủ")
        void scenario_sellerCreatesElectronicsAuction() {
            Map<String, Object> attrs = Map.of("brand", "Sony", "model", "WH-1000XM5", "warrantyMonths", 12);
            CreateAuctionRequest req = new CreateAuctionRequest(
                    "seller-001", "Sony WH-1000XM5", "Tai nghe ANC cao cấp",
                    3_000_000.0, ItemCategory.ELECTRONICS, START, END, attrs);
            req.setMinBidIncrement(100_000.0);
            req.setDurationMinutes(90);

            assertNotNull(req.getSellerId());
            assertNotNull(req.getTitle());
            assertTrue(req.getStartingPrice() > 0);
            assertEquals(90,          req.getDurationMinutes());
            assertEquals(100_000.0,   req.getMinBidIncrement(), 0.001);
        }
    }

    // =====================================================================
    // 6. UpdateItemRequest
    // =====================================================================

    @Nested
    @DisplayName("6. UpdateItemRequest")
    class UpdateItemRequestTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new UpdateItemRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) UpdateItemRequest::new);
        }

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void fullConstructor_setsAllFields() {
            UpdateItemRequest req = new UpdateItemRequest(
                    "auction-001", "iPhone 15 Pro Max", "Mô tả mới", 25_000_000.0);

            assertEquals("auction-001",      req.getAuctionId());
            assertEquals("iPhone 15 Pro Max", req.getTitle());
            assertEquals("Mô tả mới",        req.getDescription());
            assertEquals(25_000_000.0,        req.getStartingPrice(), 0.001);
        }

        @Test
        @DisplayName("Setter/Getter hoạt động đúng với tất cả field")
        void allSettersGetters_work() {
            UpdateItemRequest req = new UpdateItemRequest();
            req.setAuctionId("a-999");
            req.setTitle("New Title");
            req.setDescription("New Description");
            req.setStartingPrice(15_000_000.0);

            assertEquals("a-999",           req.getAuctionId());
            assertEquals("New Title",       req.getTitle());
            assertEquals("New Description", req.getDescription());
            assertEquals(15_000_000.0,       req.getStartingPrice(), 0.001);
        }

        @Test
        @DisplayName("Scenario: Seller sửa thông tin sản phẩm trước khi phiên bắt đầu")
        void scenario_sellerUpdatesItem() {
            UpdateItemRequest req = new UpdateItemRequest(
                    "auction-001", "MacBook Pro M3", "Laptop cao cấp nhất 2024", 45_000_000.0);

            assertNotNull(req.getAuctionId());
            assertFalse(req.getTitle().isBlank());
            assertTrue(req.getStartingPrice() > 0);
        }
    }

    // =====================================================================
    // 7. DeleteItemRequest
    // =====================================================================

    @Nested
    @DisplayName("7. DeleteItemRequest")
    class DeleteItemRequestTests {

        @Test
        @DisplayName("Implements Serializable")
        void isSerializable() {
            assertInstanceOf(Serializable.class, new DeleteItemRequest());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại")
        void defaultConstructor_exists() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) DeleteItemRequest::new);
        }

        @Test
        @DisplayName("Constructor với auctionId khởi tạo đúng")
        void constructor_setsAuctionId() {
            DeleteItemRequest req = new DeleteItemRequest("auction-999");
            assertEquals("auction-999", req.getAuctionId());
        }

        @Test
        @DisplayName("setAuctionId / getAuctionId hoạt động đúng")
        void auctionId_setAndGet() {
            DeleteItemRequest req = new DeleteItemRequest();
            req.setAuctionId("auction-delete");
            assertEquals("auction-delete", req.getAuctionId());
        }

        @Test
        @DisplayName("Scenario: Seller xóa phiên đấu giá chưa bắt đầu")
        void scenario_deleteAuction() {
            DeleteItemRequest req = new DeleteItemRequest("auction-001");
            assertNotNull(req.getAuctionId());
            assertFalse(req.getAuctionId().isBlank());
        }
    }

    // =====================================================================
    // 8. TopUpRequest & WithdrawRequest
    // =====================================================================

    @Nested
    @DisplayName("8. TopUpRequest & WithdrawRequest – Quản lý ví")
    class WalletRequestTests {

        // TopUpRequest và WithdrawRequest không implement Serializable
        // nên KHÔNG kiểm tra assertInstanceOf(Serializable.class, ...)

        @Test
        @DisplayName("TopUpRequest: Constructor rỗng không ném exception")
        void topUp_defaultConstructor() {
            assertDoesNotThrow(() -> new TopUpRequest());
        }

        @Test
        @DisplayName("TopUpRequest: Constructor với amount khởi tạo đúng")
        void topUp_constructor_setsAmount() {
            TopUpRequest req = new TopUpRequest(500_000.0);
            assertEquals(500_000.0, req.getAmount(), 0.001);
        }

        @Test
        @DisplayName("TopUpRequest: setAmount / getAmount hoạt động đúng")
        void topUp_amount_setAndGet() {
            TopUpRequest req = new TopUpRequest();
            req.setAmount(1_000_000.0);
            assertEquals(1_000_000.0, req.getAmount(), 0.001);
        }

        @Test
        @DisplayName("WithdrawRequest: Constructor rỗng không ném exception")
        void withdraw_defaultConstructor() {
            assertDoesNotThrow(() -> new WithdrawRequest());
        }

        @Test
        @DisplayName("WithdrawRequest: Constructor với amount khởi tạo đúng")
        void withdraw_constructor_setsAmount() {
            WithdrawRequest req = new WithdrawRequest(200_000.0);
            assertEquals(200_000.0, req.getAmount(), 0.001);
        }

        @Test
        @DisplayName("WithdrawRequest: setAmount / getAmount hoạt động đúng")
        void withdraw_amount_setAndGet() {
            WithdrawRequest req = new WithdrawRequest();
            req.setAmount(750_000.0);
            assertEquals(750_000.0, req.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Scenario: Bidder nạp tiền 2 triệu vào ví trước khi đấu giá")
        void scenario_bidderTopUp() {
            TopUpRequest req = new TopUpRequest(2_000_000.0);
            assertTrue(req.getAmount() > 0, "Số tiền nạp phải dương");
        }

        @Test
        @DisplayName("Scenario: Seller rút tiền sau khi phiên đấu giá thắng")
        void scenario_sellerWithdraw() {
            WithdrawRequest req = new WithdrawRequest(5_000_000.0);
            assertTrue(req.getAmount() > 0, "Số tiền rút phải dương");
        }

        @Test
        @DisplayName("TopUpRequest và WithdrawRequest là 2 lớp riêng biệt – không nhầm lẫn")
        void topUpAndWithdraw_areDifferentClasses() {
            TopUpRequest    topUp    = new TopUpRequest(1_000_000.0);
            WithdrawRequest withdraw = new WithdrawRequest(1_000_000.0);

            assertNotEquals(topUp.getClass(), withdraw.getClass());
            assertEquals(topUp.getAmount(), withdraw.getAmount(), 0.001,
                    "Cùng amount nhưng khác class – không thể cast nhầm");
        }
    }
}