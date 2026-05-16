package com.auction.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra toàn bộ cây ngoại lệ của hệ thống đấu giá.
 */
@DisplayName("Exception – Cây ngoại lệ hệ thống đấu giá")
class AuctionExceptionTest {

    // =====================================================================
    // 1. AuctionException – lớp BASE
    // =====================================================================

    @Nested
    @DisplayName("1. AuctionException – Base Exception")
    class AuctionExceptionBaseTests {

        @Test
        @DisplayName("AuctionException IS-A RuntimeException (unchecked)")
        void auctionException_isA_RuntimeException() {
            AuctionException ex = new AuctionException("ERR_001", "Lỗi nghiệp vụ");
            assertInstanceOf(RuntimeException.class, ex,
                    "AuctionException phải là unchecked exception (extends RuntimeException)");
        }

        @Test
        @DisplayName("Constructor khởi tạo đúng code và message")
        void constructor_setsCodeAndMessage() {
            AuctionException ex = new AuctionException("ERR_CODE", "Thông báo lỗi");
            assertEquals("ERR_CODE",      ex.getCode());
            assertEquals("Thông báo lỗi", ex.getMessage());
        }

        @Test
        @DisplayName("getCode() trả về đúng code đã truyền vào")
        void getCode_returnsCorrectCode() {
            AuctionException ex = new AuctionException("CUSTOM_CODE", "msg");
            assertEquals("CUSTOM_CODE", ex.getCode());
        }

        @Test
        @DisplayName("getMessage() kế thừa từ Throwable hoạt động đúng")
        void getMessage_returnsCorrectMessage() {
            AuctionException ex = new AuctionException("CODE", "Chi tiết lỗi tại đây");
            assertEquals("Chi tiết lỗi tại đây", ex.getMessage());
        }

        @Test
        @DisplayName("AuctionException là abstract – chỉ kiểm tra có thể instantiate trực tiếp không")
        void auctionException_canBeInstantiatedDirectly() {
            // AuctionException không phải abstract → có thể dùng trực tiếp làm base
            assertDoesNotThrow(() -> new AuctionException("CODE", "msg"));
        }

        @Test
        @DisplayName("Có thể throw và catch AuctionException")
        void auctionException_throwAndCatch() {
            AuctionException caught = assertThrows(AuctionException.class, () -> {
                throw new AuctionException("TEST", "test message");
            });
            assertEquals("TEST",         caught.getCode());
            assertEquals("test message", caught.getMessage());
        }

        @Test
        @DisplayName("Encapsulation: field code là private trong AuctionException")
        void auctionException_code_isPrivate() throws NoSuchFieldException {
            var field = AuctionException.class.getDeclaredField("code");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'code' phải là private – truy cập qua getCode()");
        }
    }

    // =====================================================================
    // 2. AuctionClosedException – đấu giá khi phiên đã đóng
    // =====================================================================

    @Nested
    @DisplayName("2. AuctionClosedException – Đặt giá khi phiên đã đóng")
    class AuctionClosedExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException (bắt được bằng catch AuctionException)")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new AuctionClosedException("msg"));
        }

        @Test
        @DisplayName("IS-A RuntimeException (unchecked)")
        void isA_RuntimeException() {
            assertInstanceOf(RuntimeException.class, new AuctionClosedException("msg"));
        }

        @Test
        @DisplayName("code phải là 'AUCTION_CLOSED' theo protocol")
        void code_isAuctionClosed() {
            AuctionClosedException ex = new AuctionClosedException("Phiên đấu giá đã kết thúc");
            assertEquals("AUCTION_CLOSED", ex.getCode());
        }

        @Test
        @DisplayName("message được truyền đúng")
        void message_isSetCorrectly() {
            AuctionClosedException ex = new AuctionClosedException("Phiên đã đóng lúc 20:00");
            assertEquals("Phiên đã đóng lúc 20:00", ex.getMessage());
        }

        @Test
        @DisplayName("Scenario: throw và catch bằng kiểu AuctionException (catch cha)")
        void scenario_catchAsAuctionException() {
            AuctionException caught = assertThrows(AuctionException.class, () -> {
                throw new AuctionClosedException("Phiên #auction-001 đã kết thúc");
            });
            assertEquals("AUCTION_CLOSED", caught.getCode());
            assertTrue(caught.getMessage().contains("auction-001"));
        }

        @Test
        @DisplayName("Scenario: catch bằng đúng kiểu AuctionClosedException")
        void scenario_catchAsSpecificType() {
            AuctionClosedException caught = assertThrows(AuctionClosedException.class, () -> {
                // Mô phỏng logic server: phiên FINISHED → ném exception
                String auctionStatus = "FINISHED";
                if (!auctionStatus.equals("RUNNING")) {
                    throw new AuctionClosedException("Phiên đấu giá không còn ở trạng thái RUNNING");
                }
            });
            assertEquals("AUCTION_CLOSED", caught.getCode());
        }
    }

    // =====================================================================
    // 3. InsufficientBidException – đặt giá thấp hơn mức tối thiểu
    // =====================================================================

    @Nested
    @DisplayName("3. InsufficientBidException – Giá đặt thấp hơn mức tối thiểu")
    class InsufficientBidExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new InsufficientBidException("msg"));
        }

        @Test
        @DisplayName("code phải là 'INSUFFICIENT_BID' theo protocol")
        void code_isInsufficientBid() {
            InsufficientBidException ex = new InsufficientBidException("Giá quá thấp");
            assertEquals("INSUFFICIENT_BID", ex.getCode());
        }

        @Test
        @DisplayName("message được truyền đúng")
        void message_isSetCorrectly() {
            InsufficientBidException ex = new InsufficientBidException(
                    "Giá đặt 900,000đ thấp hơn mức tối thiểu 1,050,000đ");
            assertEquals("Giá đặt 900,000đ thấp hơn mức tối thiểu 1,050,000đ", ex.getMessage());
        }

        @Test
        @DisplayName("Scenario: đặt giá thấp hơn currentPrice + minIncrement → throw")
        void scenario_bidBelowMinimum_throws() {
            double currentPrice    = 1_000_000.0;
            double minIncrement    = 50_000.0;
            double userBidAmount   = 900_000.0;     // thấp hơn currentPrice

            InsufficientBidException caught = assertThrows(InsufficientBidException.class, () -> {
                if (userBidAmount < currentPrice + minIncrement) {
                    throw new InsufficientBidException(
                            "Giá đặt " + userBidAmount + " thấp hơn mức tối thiểu "
                                    + (currentPrice + minIncrement));
                }
            });
            assertEquals("INSUFFICIENT_BID", caught.getCode());
        }

        @Test
        @DisplayName("Scenario: đặt giá bằng currentPrice (không đủ increment) → throw")
        void scenario_bidEqualCurrentPrice_throws() {
            double currentPrice  = 1_000_000.0;
            double minIncrement  = 50_000.0;
            double userBid       = 1_000_000.0;    // bằng giá hiện tại, không tăng

            assertThrows(InsufficientBidException.class, () -> {
                if (userBid < currentPrice + minIncrement) {
                    throw new InsufficientBidException("Phải đặt cao hơn giá hiện tại");
                }
            });
        }
    }

    // =====================================================================
    // 4. InvalidBidException – giá không hợp lệ / seller tự đặt giá
    // =====================================================================

    @Nested
    @DisplayName("4. InvalidBidException – Giá đặt không hợp lệ")
    class InvalidBidExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new InvalidBidException("msg"));
        }

        @Test
        @DisplayName("code phải là 'INVALID_BID' theo protocol")
        void code_isInvalidBid() {
            InvalidBidException ex = new InvalidBidException("Không hợp lệ");
            assertEquals("INVALID_BID", ex.getCode());
        }

        @Test
        @DisplayName("Scenario: Seller tự đặt giá sản phẩm của mình → throw")
        void scenario_sellerBidsOwnItem_throws() {
            String sellerId = "seller-001";
            String bidderId = "seller-001"; // cùng ID → tự đặt giá

            InvalidBidException caught = assertThrows(InvalidBidException.class, () -> {
                if (sellerId.equals(bidderId)) {
                    throw new InvalidBidException("Seller không thể tự đặt giá sản phẩm của chính mình");
                }
            });
            assertEquals("INVALID_BID", caught.getCode());
            assertTrue(caught.getMessage().contains("Seller"));
        }

        @Test
        @DisplayName("Scenario: catch InvalidBidException bằng kiểu cha AuctionException")
        void scenario_catchAsAuctionException() {
            AuctionException caught = assertThrows(AuctionException.class, () -> {
                throw new InvalidBidException("Giá không hợp lệ");
            });
            assertEquals("INVALID_BID", caught.getCode());
        }
    }

    // =====================================================================
    // 5. AuctionStatusException – sai trạng thái vòng đời phiên đấu giá
    // =====================================================================

    @Nested
    @DisplayName("5. AuctionStatusException – Sai trạng thái vòng đời phiên")
    class AuctionStatusExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new AuctionStatusException("msg"));
        }

        @Test
        @DisplayName("code phải là 'BAD_REQUEST' theo protocol")
        void code_isBadRequest() {
            AuctionStatusException ex = new AuctionStatusException("Trạng thái không hợp lệ");
            assertEquals("BAD_REQUEST", ex.getCode());
        }

        @Test
        @DisplayName("Scenario: đặt giá khi phiên FINISHED → throw")
        void scenario_bidOnFinishedAuction_throws() {
            assertThrows(AuctionStatusException.class, () -> {
                String status = "FINISHED";
                if (status.equals("FINISHED") || status.equals("CANCELED")) {
                    throw new AuctionStatusException("Phiên đấu giá đã kết thúc với trạng thái: " + status);
                }
            });
        }

        @Test
        @DisplayName("Scenario: đặt giá khi phiên CANCELED → throw")
        void scenario_bidOnCanceledAuction_throws() {
            AuctionStatusException caught = assertThrows(AuctionStatusException.class, () -> {
                String status = "CANCELED";
                if (status.equals("FINISHED") || status.equals("CANCELED")) {
                    throw new AuctionStatusException("Phiên đã bị hủy");
                }
            });
            assertEquals("BAD_REQUEST", caught.getCode());
        }
    }

    // =====================================================================
    // 6. InvalidAuctionRequestException – dữ liệu tạo phiên không hợp lệ
    // =====================================================================

    @Nested
    @DisplayName("6. InvalidAuctionRequestException – Dữ liệu tạo/sửa phiên không hợp lệ")
    class InvalidAuctionRequestExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new InvalidAuctionRequestException("msg"));
        }

        @Test
        @DisplayName("code phải là 'BAD_REQUEST'")
        void code_isBadRequest() {
            assertEquals("BAD_REQUEST", new InvalidAuctionRequestException("msg").getCode());
        }

        @Test
        @DisplayName("Scenario: giá khởi điểm âm → throw")
        void scenario_negativeStartingPrice_throws() {
            assertThrows(InvalidAuctionRequestException.class, () -> {
                double startingPrice = -500_000.0;
                if (startingPrice < 0) {
                    throw new InvalidAuctionRequestException(
                            "Giá khởi điểm không được âm: " + startingPrice);
                }
            });
        }

        @Test
        @DisplayName("Scenario: endTime trước startTime → throw")
        void scenario_endTimeBeforeStartTime_throws() {
            assertThrows(InvalidAuctionRequestException.class, () -> {
                var startTime = java.time.LocalDateTime.now().plusHours(2);
                var endTime   = java.time.LocalDateTime.now().plusHours(1); // trước startTime
                if (endTime.isBefore(startTime)) {
                    throw new InvalidAuctionRequestException(
                            "Thời gian kết thúc phải sau thời gian bắt đầu");
                }
            });
        }

        @Test
        @DisplayName("Scenario: tên sản phẩm rỗng → throw")
        void scenario_emptyTitle_throws() {
            assertThrows(InvalidAuctionRequestException.class, () -> {
                String title = "   ";
                if (title == null || title.isBlank()) {
                    throw new InvalidAuctionRequestException("Tên sản phẩm không được để trống");
                }
            });
        }
    }

    // =====================================================================
    // 7. InvalidCredentialsException – đăng nhập sai thông tin
    // =====================================================================

    @Nested
    @DisplayName("7. InvalidCredentialsException – Xác thực thất bại")
    class InvalidCredentialsExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new InvalidCredentialsException("msg"));
        }

        @Test
        @DisplayName("code phải là 'AUTH_FAILED' theo protocol")
        void code_isAuthFailed() {
            assertEquals("AUTH_FAILED", new InvalidCredentialsException("msg").getCode());
        }

        @Test
        @DisplayName("Scenario: sai mật khẩu → throw")
        void scenario_wrongPassword_throws() {
            InvalidCredentialsException caught = assertThrows(InvalidCredentialsException.class, () -> {
                String storedPassword = "hashed_password_xyz";
                String inputPassword  = "wrong_password";
                if (!storedPassword.equals(inputPassword)) {
                    throw new InvalidCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
                }
            });
            assertEquals("AUTH_FAILED", caught.getCode());
        }
    }

    // =====================================================================
    // 8. UnauthorizedException – không có quyền thực hiện hành động
    // =====================================================================

    @Nested
    @DisplayName("8. UnauthorizedException – Không đủ quyền")
    class UnauthorizedExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new UnauthorizedException("msg"));
        }

        @Test
        @DisplayName("code phải là 'PERMISSION_DENIED' theo protocol")
        void code_isPermissionDenied() {
            assertEquals("PERMISSION_DENIED", new UnauthorizedException("msg").getCode());
        }

        @Test
        @DisplayName("Scenario: Bidder cố tạo phiên đấu giá (chức năng của Seller) → throw")
        void scenario_bidderCreatesAuction_throws() {
            UnauthorizedException caught = assertThrows(UnauthorizedException.class, () -> {
                String userRole = "BIDDER";
                if (!userRole.equals("SELLER") && !userRole.equals("ADMIN")) {
                    throw new UnauthorizedException("Chỉ Seller mới được tạo phiên đấu giá");
                }
            });
            assertEquals("PERMISSION_DENIED", caught.getCode());
        }

        @Test
        @DisplayName("Scenario: request không có token → throw")
        void scenario_noToken_throws() {
            assertThrows(UnauthorizedException.class, () -> {
                String token = null;
                if (token == null || token.isBlank()) {
                    throw new UnauthorizedException("Yêu cầu phải đính kèm token xác thực");
                }
            });
        }
    }

    // =====================================================================
    // 9. UserAlreadyExistsException – đăng ký trùng username/email
    // =====================================================================

    @Nested
    @DisplayName("9. UserAlreadyExistsException – Tài khoản đã tồn tại")
    class UserAlreadyExistsExceptionTests {

        @Test
        @DisplayName("IS-A AuctionException")
        void isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new UserAlreadyExistsException("msg"));
        }

        @Test
        @DisplayName("code phải là 'BAD_REQUEST'")
        void code_isBadRequest() {
            assertEquals("BAD_REQUEST", new UserAlreadyExistsException("msg").getCode());
        }

        @Test
        @DisplayName("Scenario: đăng ký username đã tồn tại → throw")
        void scenario_duplicateUsername_throws() {
            UserAlreadyExistsException caught = assertThrows(UserAlreadyExistsException.class, () -> {
                boolean usernameExists = true; // giả lập DB trả về đã tồn tại
                if (usernameExists) {
                    throw new UserAlreadyExistsException("Username 'alice' đã được sử dụng");
                }
            });
            assertTrue(caught.getMessage().contains("alice"));
        }
    }

    // =====================================================================
    // 10. TokenExpiredException & TokenInvalidException
    // =====================================================================

    @Nested
    @DisplayName("10. Token Exceptions – TOKEN_EXPIRED & TOKEN_INVALID")
    class TokenExceptionTests {

        @Test
        @DisplayName("TokenExpiredException IS-A AuctionException")
        void tokenExpired_isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new TokenExpiredException("msg"));
        }

        @Test
        @DisplayName("TokenExpiredException code phải là 'TOKEN_EXPIRED'")
        void tokenExpired_code() {
            assertEquals("TOKEN_EXPIRED", new TokenExpiredException("Token hết hạn").getCode());
        }

        @Test
        @DisplayName("TokenInvalidException IS-A AuctionException")
        void tokenInvalid_isA_AuctionException() {
            assertInstanceOf(AuctionException.class, new TokenInvalidException("msg"));
        }

        @Test
        @DisplayName("TokenInvalidException code phải là 'TOKEN_INVALID'")
        void tokenInvalid_code() {
            assertEquals("TOKEN_INVALID", new TokenInvalidException("Token sai định dạng").getCode());
        }

        @Test
        @DisplayName("Scenario: token hết hạn → throw TokenExpiredException")
        void scenario_expiredToken_throws() {
            TokenExpiredException caught = assertThrows(TokenExpiredException.class, () -> {
                boolean isExpired = true;
                if (isExpired) throw new TokenExpiredException("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
            });
            assertEquals("TOKEN_EXPIRED", caught.getCode());
        }

        @Test
        @DisplayName("Scenario: token bị giả mạo → throw TokenInvalidException")
        void scenario_tamperedToken_throws() {
            TokenInvalidException caught = assertThrows(TokenInvalidException.class, () -> {
                String token = "invalid.forged.token";
                if (!token.startsWith("valid_prefix")) {
                    throw new TokenInvalidException("Token không hợp lệ hoặc đã bị giả mạo");
                }
            });
            assertEquals("TOKEN_INVALID", caught.getCode());
        }
    }

    // =====================================================================
    // 11. ResourceNotFoundException & UserNotFoundException
    // =====================================================================

    @Nested
    @DisplayName("11. ResourceNotFoundException & UserNotFoundException")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("ResourceNotFoundException IS-A AuctionException")
        void resourceNotFound_isA_AuctionException() {
            assertInstanceOf(AuctionException.class,
                    new ResourceNotFoundException("AUCTION_NOT_FOUND", "msg"));
        }

        @Test
        @DisplayName("ResourceNotFoundException nhận code linh hoạt")
        void resourceNotFound_flexibleCode() {
            ResourceNotFoundException ex1 =
                    new ResourceNotFoundException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá");
            ResourceNotFoundException ex2 =
                    new ResourceNotFoundException("ITEM_NOT_FOUND", "Không tìm thấy sản phẩm");

            assertEquals("AUCTION_NOT_FOUND", ex1.getCode());
            assertEquals("ITEM_NOT_FOUND",    ex2.getCode());
        }

        @Test
        @DisplayName("UserNotFoundException IS-A ResourceNotFoundException (kế thừa 3 cấp)")
        void userNotFound_isA_ResourceNotFoundException() {
            assertInstanceOf(ResourceNotFoundException.class, new UserNotFoundException());
            assertInstanceOf(AuctionException.class,          new UserNotFoundException());
            assertInstanceOf(RuntimeException.class,          new UserNotFoundException());
        }

        @Test
        @DisplayName("UserNotFoundException() – code mặc định là 'USER_NOT_FOUND'")
        void userNotFound_defaultConstructor_code() {
            UserNotFoundException ex = new UserNotFoundException();
            assertEquals("USER_NOT_FOUND", ex.getCode());
            assertNotNull(ex.getMessage(), "Message mặc định không được null");
            assertFalse(ex.getMessage().isBlank(), "Message mặc định không được rỗng");
        }

        @Test
        @DisplayName("UserNotFoundException(String) – code vẫn là 'USER_NOT_FOUND', message tuỳ chỉnh")
        void userNotFound_customMessage_code() {
            UserNotFoundException ex = new UserNotFoundException("Không tìm thấy user với id: user-999");
            assertEquals("USER_NOT_FOUND",                       ex.getCode());
            assertEquals("Không tìm thấy user với id: user-999", ex.getMessage());
        }

        @Test
        @DisplayName("UserNotFoundException(String, Throwable) – có root cause")
        void userNotFound_withCause_setsCause() {
            RuntimeException rootCause = new RuntimeException("SQLException: no rows");
            UserNotFoundException ex = new UserNotFoundException("DB error", rootCause);

            assertEquals("USER_NOT_FOUND", ex.getCode());
            assertEquals(rootCause, ex.getCause(), "Root cause phải được gán đúng");
        }

        @Test
        @DisplayName("Scenario: tìm user theo id không tồn tại → throw UserNotFoundException")
        void scenario_userNotFound_throws() {
            UserNotFoundException caught = assertThrows(UserNotFoundException.class, () -> {
                String userId = "user-999";
                boolean found = false; // DB trả về không tìm thấy
                if (!found) throw new UserNotFoundException("Không tìm thấy user: " + userId);
            });
            assertEquals("USER_NOT_FOUND", caught.getCode());
            assertTrue(caught.getMessage().contains("user-999"));
        }

        @Test
        @DisplayName("Scenario: catch UserNotFoundException bằng kiểu ResourceNotFoundException")
        void scenario_catchAsResourceNotFoundException() {
            ResourceNotFoundException caught = assertThrows(ResourceNotFoundException.class, () -> {
                throw new UserNotFoundException("user-001 không tồn tại");
            });
            assertEquals("USER_NOT_FOUND", caught.getCode());
        }
    }

    // =====================================================================
    // 12. CATCH-ALL – bắt nhiều loại exception bằng kiểu cha AuctionException
    // =====================================================================

    @Nested
    @DisplayName("12. Catch-all – Bắt mọi lỗi nghiệp vụ qua AuctionException")
    class CatchAllTests {

        @Test
        @DisplayName("catch(AuctionException) bắt được tất cả subclass")
        void catchAll_catchesAllSubclasses() {
            // Danh sách tất cả exception ném ra
            AuctionException[] exceptions = {
                    new AuctionClosedException("closed"),
                    new AuctionStatusException("status"),
                    new InsufficientBidException("insufficient"),
                    new InvalidBidException("invalid bid"),
                    new InvalidAuctionRequestException("bad request"),
                    new InvalidCredentialsException("auth failed"),
                    new UnauthorizedException("unauthorized"),
                    new UserAlreadyExistsException("exists"),
                    new TokenExpiredException("expired"),
                    new TokenInvalidException("invalid token"),
                    new ResourceNotFoundException("NOT_FOUND", "not found"),
                    new UserNotFoundException("user not found"),
            };

            for (AuctionException ex : exceptions) {
                AuctionException caught = assertThrows(AuctionException.class, () -> { throw ex; });
                assertNotNull(caught.getCode(),    ex.getClass().getSimpleName() + " phải có code");
                assertNotNull(caught.getMessage(), ex.getClass().getSimpleName() + " phải có message");
            }
        }

        @Test
        @DisplayName("Mỗi exception có code khác nhau theo protocol")
        void eachException_hasCorrectProtocolCode() {
            assertEquals("AUCTION_CLOSED",   new AuctionClosedException("").getCode());
            assertEquals("BAD_REQUEST",      new AuctionStatusException("").getCode());
            assertEquals("INSUFFICIENT_BID", new InsufficientBidException("").getCode());
            assertEquals("INVALID_BID",      new InvalidBidException("").getCode());
            assertEquals("BAD_REQUEST",      new InvalidAuctionRequestException("").getCode());
            assertEquals("AUTH_FAILED",      new InvalidCredentialsException("").getCode());
            assertEquals("PERMISSION_DENIED",new UnauthorizedException("").getCode());
            assertEquals("BAD_REQUEST",      new UserAlreadyExistsException("").getCode());
            assertEquals("TOKEN_EXPIRED",    new TokenExpiredException("").getCode());
            assertEquals("TOKEN_INVALID",    new TokenInvalidException("").getCode());
            assertEquals("USER_NOT_FOUND",   new UserNotFoundException().getCode());
        }

        @Test
        @DisplayName("Kịch bản thực tế: bid pipeline – nhiều lỗi khác nhau, catch 1 lần")
        void scenario_bidPipeline_catchAll() {
            // Server handler chỉ cần 1 catch(AuctionException)
            // mô phỏng 3 loại lỗi khác nhau từ cùng một luồng xử lý bid

            String[] errorTypes = { "CLOSED", "LOW_BID", "UNAUTHORIZED" };

            for (String errorType : errorTypes) {
                AuctionException caught = assertThrows(AuctionException.class, () -> {
                    switch (errorType) {
                        case "CLOSED"       -> throw new AuctionClosedException("Phiên đã đóng");
                        case "LOW_BID"      -> throw new InsufficientBidException("Giá quá thấp");
                        case "UNAUTHORIZED" -> throw new UnauthorizedException("Không có quyền");
                    }
                });
                // Server chỉ cần caught.getCode() để trả JSON về client
                assertNotNull(caught.getCode());
                assertNotNull(caught.getMessage());
            }
        }
    }
}