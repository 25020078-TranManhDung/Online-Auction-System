package com.auction.shared.model.user;

import com.auction.shared.enums.UserRole;
import com.auction.shared.model.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 *   1. Cấu trúc kế thừa & Abstraction
 *   2. Constructor & Encapsulation từng lớp
 *   3. login() – xác thực mật khẩu
 *   4. showRole() – Polymorphism
 *   5. Quản lý trạng thái tài khoản (status, violationCount, lockedUntil)
 *   6. equals() & hashCode() kế thừa từ Entity
 *   7. Field riêng của từng subclass (reputationScore, adminLevel)
 */
@DisplayName("User – Cây kế thừa, vai trò & nghiệp vụ")
class UserRoleTest {

    private Bidder bidder;
    private Seller seller;
    private Admin  admin;

    private ByteArrayOutputStream outCapture;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        bidder = new Bidder("bidder-001", "alice",   "pass123",  "alice@email.com");
        seller = new Seller("seller-001", "bob",     "pass456",  "bob@email.com");
        admin  = new Admin ("admin-001",  "charlie", "adminPass","charlie@email.com", 1);

        outCapture  = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outCapture));
    }

    void restoreOut() {
        System.setOut(originalOut);
    }

    // =====================================================================
    // 1. CẤU TRÚC CÂY KẾ THỪA & ABSTRACTION
    // =====================================================================

    @Nested
    @DisplayName("1. Cấu trúc kế thừa & Abstraction")
    class InheritanceStructureTests {

        @Test
        @DisplayName("Bidder IS-A User")
        void bidder_isA_User() {
            assertInstanceOf(User.class, bidder);
        }

        @Test
        @DisplayName("Bidder IS-A Entity (kế thừa 2 cấp)")
        void bidder_isA_Entity() {
            assertInstanceOf(Entity.class, bidder);
        }

        @Test
        @DisplayName("Seller IS-A User")
        void seller_isA_User() {
            assertInstanceOf(User.class, seller);
        }

        @Test
        @DisplayName("Seller IS-A Entity")
        void seller_isA_Entity() {
            assertInstanceOf(Entity.class, seller);
        }

        @Test
        @DisplayName("Admin IS-A User")
        void admin_isA_User() {
            assertInstanceOf(User.class, admin);
        }

        @Test
        @DisplayName("Admin IS-A Entity")
        void admin_isA_Entity() {
            assertInstanceOf(Entity.class, admin);
        }

        @Test
        @DisplayName("User là abstract – không thể khởi tạo trực tiếp")
        void user_isAbstract() {
            assertTrue(
                    java.lang.reflect.Modifier.isAbstract(User.class.getModifiers()),
                    "User phải là abstract class"
            );
        }

        @Test
        @DisplayName("Bidder, Seller, Admin là lớp concrete (không abstract)")
        void subclasses_areConcrete() {
            assertFalse(java.lang.reflect.Modifier.isAbstract(Bidder.class.getModifiers()));
            assertFalse(java.lang.reflect.Modifier.isAbstract(Seller.class.getModifiers()));
            assertFalse(java.lang.reflect.Modifier.isAbstract(Admin.class.getModifiers()));
        }

        @Test
        @DisplayName("Superclass trực tiếp của Bidder, Seller, Admin phải là User")
        void directSuperclass_isUser() {
            assertEquals(User.class, Bidder.class.getSuperclass());
            assertEquals(User.class, Seller.class.getSuperclass());
            assertEquals(User.class, Admin.class.getSuperclass());
        }

        @Test
        @DisplayName("showRole() trong User là abstract – buộc subclass override")
        void user_showRole_isAbstract() throws NoSuchMethodException {
            var method = User.class.getDeclaredMethod("showRole");
            assertTrue(
                    java.lang.reflect.Modifier.isAbstract(method.getModifiers()),
                    "User.showRole() phải là abstract"
            );
        }
    }

    // =====================================================================
    // 2. CONSTRUCTOR & ENCAPSULATION – Bidder
    // =====================================================================

    @Nested
    @DisplayName("2. Bidder – Constructor & Encapsulation")
    class BidderConstructorTests {

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void bidder_fullConstructor_setsAllFields() {
            restoreOut();
            assertEquals("bidder-001",       bidder.getId());
            assertEquals("alice",            bidder.getUsername());
            assertEquals("pass123",          bidder.getPassword());
            assertEquals("alice@email.com",  bidder.getEmail());
            assertEquals(UserRole.BIDDER,    bidder.getRole());
        }

        @Test
        @DisplayName("Bidder luôn có role = BIDDER – không thể truyền role khác qua constructor")
        void bidder_roleAlwaysBIDDER() {
            restoreOut();
            Bidder b = new Bidder("id", "user", "pw", "e@mail.com");
            assertEquals(UserRole.BIDDER, b.getRole(),
                    "Role của Bidder phải luôn là BIDDER (hardcode trong super())");
        }

        @Test
        @DisplayName("Constructor rỗng không ném exception")
        void bidder_defaultConstructor_noException() {
            restoreOut();
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Bidder::new);
        }

        @Test
        @DisplayName("Setter kế thừa từ User hoạt động đúng trên Bidder")
        void bidder_inheritedSetters_work() {
            restoreOut();
            bidder.setFullname("Alice Nguyen");
            bidder.setEmail("newalice@email.com");
            bidder.setPassword("newPass");

            assertEquals("Alice Nguyen",       bidder.getFullname());
            assertEquals("newalice@email.com", bidder.getEmail());
            assertEquals("newPass",            bidder.getPassword());
        }

        @Test
        @DisplayName("Encapsulation: field username trong User là private")
        void user_username_isPrivate() throws NoSuchFieldException {
            restoreOut();
            var field = User.class.getDeclaredField("username");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'username' phải là private (Encapsulation)");
        }

        @Test
        @DisplayName("Encapsulation: field password trong User là private")
        void user_password_isPrivate() throws NoSuchFieldException {
            restoreOut();
            var field = User.class.getDeclaredField("password");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'password' phải là private");
        }

        @Test
        @DisplayName("Encapsulation: field role trong User là private")
        void user_role_isPrivate() throws NoSuchFieldException {
            restoreOut();
            var field = User.class.getDeclaredField("role");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'role' phải là private");
        }
    }

    // =====================================================================
    // 3. CONSTRUCTOR & ENCAPSULATION – Seller
    // =====================================================================

    @Nested
    @DisplayName("3. Seller – Constructor & Encapsulation")
    class SellerConstructorTests {

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng, reputationScore mặc định = 5.0")
        void seller_fullConstructor_setsAllFields() {
            restoreOut();
            assertEquals("seller-001",     seller.getId());
            assertEquals("bob",            seller.getUsername());
            assertEquals("bob@email.com",  seller.getEmail());
            assertEquals(UserRole.SELLER,  seller.getRole());
            assertEquals(5.0,              seller.getReputationScore(), 0.001,
                    "reputationScore mặc định phải là 5.0");
        }

        @Test
        @DisplayName("Seller luôn có role = SELLER")
        void seller_roleAlwaysSELLER() {
            restoreOut();
            assertEquals(UserRole.SELLER, seller.getRole());
        }

        @Test
        @DisplayName("Constructor rỗng không ném exception")
        void seller_defaultConstructor_noException() {
            restoreOut();
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Seller::new);
        }

        @Test
        @DisplayName("setReputationScore / getReputationScore hoạt động đúng")
        void seller_reputationScore_setAndGet() {
            restoreOut();
            seller.setReputationScore(4.5);
            assertEquals(4.5, seller.getReputationScore(), 0.001);
        }

        @Test
        @DisplayName("reputationScore = 0 hợp lệ (Seller mới bị đánh giá thấp)")
        void seller_zeroReputation_isValid() {
            restoreOut();
            seller.setReputationScore(0.0);
            assertEquals(0.0, seller.getReputationScore(), 0.001);
        }

        @Test
        @DisplayName("Encapsulation: field reputationScore trong Seller là private")
        void seller_reputationScore_isPrivate() throws NoSuchFieldException {
            restoreOut();
            var field = Seller.class.getDeclaredField("reputationScore");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
        }
    }

    // =====================================================================
    // 4. CONSTRUCTOR & ENCAPSULATION – Admin
    // =====================================================================

    @Nested
    @DisplayName("4. Admin – Constructor & Encapsulation")
    class AdminConstructorTests {

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field bao gồm adminLevel")
        void admin_fullConstructor_setsAllFields() {
            restoreOut();
            assertEquals("admin-001",           admin.getId());
            assertEquals("charlie",             admin.getUsername());
            assertEquals("charlie@email.com",   admin.getEmail());
            assertEquals(UserRole.ADMIN,         admin.getRole());
            assertEquals(1,                      admin.getAdminLevel());
        }

        @Test
        @DisplayName("Admin luôn có role = ADMIN")
        void admin_roleAlwaysADMIN() {
            restoreOut();
            assertEquals(UserRole.ADMIN, admin.getRole());
        }

        @Test
        @DisplayName("Constructor rỗng không ném exception")
        void admin_defaultConstructor_noException() {
            restoreOut();
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Admin::new);
        }

        @Test
        @DisplayName("setAdminLevel / getAdminLevel hoạt động đúng")
        void admin_adminLevel_setAndGet() {
            restoreOut();
            admin.setAdminLevel(3);
            assertEquals(3, admin.getAdminLevel());
        }

        @Test
        @DisplayName("Encapsulation: field adminLevel trong Admin là private")
        void admin_adminLevel_isPrivate() throws NoSuchFieldException {
            restoreOut();
            var field = Admin.class.getDeclaredField("adminLevel");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
        }
    }

    // =====================================================================
    // 5. login() – XÁC THỰC MẬT KHẨU
    // =====================================================================

    @Nested
    @DisplayName("5. login() – Xác thực mật khẩu")
    class LoginTests {

        @Test
        @DisplayName("login() trả về true khi mật khẩu đúng – Bidder")
        void login_correctPassword_bidder_returnsTrue() {
            restoreOut();
            assertTrue(bidder.login("pass123"));
        }

        @Test
        @DisplayName("login() trả về false khi mật khẩu sai – Bidder")
        void login_wrongPassword_bidder_returnsFalse() {
            restoreOut();
            assertFalse(bidder.login("wrongPass"));
        }

        @Test
        @DisplayName("login() trả về true khi mật khẩu đúng – Seller")
        void login_correctPassword_seller_returnsTrue() {
            restoreOut();
            assertTrue(seller.login("pass456"));
        }

        @Test
        @DisplayName("login() trả về false khi mật khẩu sai – Seller")
        void login_wrongPassword_seller_returnsFalse() {
            restoreOut();
            assertFalse(seller.login("PASS456")); // case-sensitive
        }

        @Test
        @DisplayName("login() trả về true khi mật khẩu đúng – Admin")
        void login_correctPassword_admin_returnsTrue() {
            restoreOut();
            assertTrue(admin.login("adminPass"));
        }

        @Test
        @DisplayName("login() phân biệt hoa thường (case-sensitive)")
        void login_caseSensitive() {
            restoreOut();
            assertFalse(bidder.login("Pass123"),
                    "login() phải phân biệt hoa thường");
            assertFalse(bidder.login("PASS123"));
            assertTrue(bidder.login("pass123"));
        }

        @Test
        @DisplayName("login() trả về false khi nhập chuỗi rỗng")
        void login_emptyPassword_returnsFalse() {
            restoreOut();
            assertFalse(bidder.login(""));
        }

        @Test
        @DisplayName("login() sau khi đổi mật khẩu phải dùng mật khẩu mới")
        void login_afterPasswordChange_usesNewPassword() {
            restoreOut();
            bidder.setPassword("newPass999");
            assertFalse(bidder.login("pass123"), "Mật khẩu cũ phải bị từ chối");
            assertTrue(bidder.login("newPass999"), "Mật khẩu mới phải được chấp nhận");
        }
    }

    // =====================================================================
    // 6. showRole() – POLYMORPHISM
    // =====================================================================

    @Nested
    @DisplayName("6. showRole() – Tính đa hình Polymorphism")
    class ShowRolePolymorphTests {

        @Test
        @DisplayName("Bidder.showRole() in thông tin vai trò Bidder")
        void bidder_showRole_containsBidder() {
            bidder.showRole();
            String output = outCapture.toString();
            restoreOut();
            assertTrue(output.toLowerCase().contains("bidder") || output.contains("đấu giá"),
                    "showRole() của Bidder phải đề cập đến vai trò Bidder");
        }

        @Test
        @DisplayName("Seller.showRole() in thông tin vai trò Seller")
        void seller_showRole_containsSeller() {
            seller.showRole();
            String output = outCapture.toString();
            restoreOut();
            assertTrue(output.toLowerCase().contains("seller") || output.contains("bán"),
                    "showRole() của Seller phải đề cập đến vai trò Seller");
        }

        @Test
        @DisplayName("Admin.showRole() in thông tin vai trò Admin kèm adminLevel")
        void admin_showRole_containsAdminAndLevel() {
            admin.showRole();
            String output = outCapture.toString();
            restoreOut();
            assertTrue(output.toLowerCase().contains("admin") || output.contains("quản trị"),
                    "showRole() của Admin phải đề cập đến vai trò Admin");
            assertTrue(output.contains("1"),
                    "showRole() của Admin phải in adminLevel");
        }

        @Test
        @DisplayName("Ba lớp in nội dung KHÁC NHAU qua showRole()")
        void threeClasses_showDifferentOutput() {
            bidder.showRole();
            String bidderOut = outCapture.toString(); outCapture.reset();
            seller.showRole();
            String sellerOut = outCapture.toString(); outCapture.reset();
            admin.showRole();
            String adminOut = outCapture.toString();
            restoreOut();

            assertNotEquals(bidderOut, sellerOut, "Bidder và Seller phải in khác nhau");
            assertNotEquals(sellerOut, adminOut,  "Seller và Admin phải in khác nhau");
            assertNotEquals(bidderOut, adminOut,  "Bidder và Admin phải in khác nhau");
        }

        @Test
        @DisplayName("User ref = new Bidder() → showRole() gọi phiên bản Bidder (runtime dispatch)")
        void userRef_bidder_callsBidderShowRole() {
            User user = new Bidder("id", "u", "p", "e@e.com");
            user.showRole();
            String output = outCapture.toString();
            restoreOut();
            assertTrue(output.toLowerCase().contains("bidder") || output.contains("đấu giá"));
        }

        @Test
        @DisplayName("User ref = new Seller() → showRole() gọi phiên bản Seller (runtime dispatch)")
        void userRef_seller_callsSellerShowRole() {
            User user = new Seller("id", "u", "p", "e@e.com");
            user.showRole();
            String output = outCapture.toString();
            restoreOut();
            assertTrue(output.toLowerCase().contains("seller") || output.contains("bán"));
        }

        @Test
        @DisplayName("Duyệt List<User> hỗn hợp, mỗi user gọi đúng showRole() của lớp mình")
        void mixedList_eachCallsOwnShowRole() {
            List<User> users = List.of(
                    new Bidder("b1", "bidder1", "p", "b1@e.com"),
                    new Seller("s1", "seller1", "p", "s1@e.com"),
                    new Admin ("a1", "admin1",  "p", "a1@e.com", 2)
            );
            for (User u : users) u.showRole();
            String allOutput = outCapture.toString();
            restoreOut();

            assertTrue(allOutput.toLowerCase().contains("bidder") || allOutput.contains("đấu giá"),
                    "Bidder phải được gọi");
            assertTrue(allOutput.toLowerCase().contains("seller") || allOutput.contains("bán"),
                    "Seller phải được gọi");
            assertTrue(allOutput.toLowerCase().contains("admin") || allOutput.contains("quản trị"),
                    "Admin phải được gọi");
        }

        @Test
        @DisplayName("Reflection: Bidder, Seller, Admin đều khai báo showRole() riêng (đã override)")
        void allSubclasses_declareShowRole() {
            restoreOut();
            assertDoesNotThrow(() -> Bidder.class.getDeclaredMethod("showRole"),
                    "Bidder phải override showRole()");
            assertDoesNotThrow(() -> Seller.class.getDeclaredMethod("showRole"),
                    "Seller phải override showRole()");
            assertDoesNotThrow(() -> Admin.class.getDeclaredMethod("showRole"),
                    "Admin phải override showRole()");
        }
    }

    // =====================================================================
    // 7. TRẠNG THÁI TÀI KHOẢN – status, violationCount, lockedUntil
    // =====================================================================

    @Nested
    @DisplayName("7. Trạng thái tài khoản (status, violationCount, lockedUntil)")
    class AccountStatusTests {

        @Test
        @DisplayName("Status mặc định phải là 'ACTIVE'")
        void defaultStatus_isActive() {
            restoreOut();
            assertEquals("ACTIVE", bidder.getStatus(),
                    "Tài khoản mới phải có status = ACTIVE");
            assertEquals("ACTIVE", seller.getStatus());
            assertEquals("ACTIVE", admin.getStatus());
        }

        @Test
        @DisplayName("violationCount mặc định là 0")
        void defaultViolationCount_isZero() {
            restoreOut();
            assertEquals(0, bidder.getViolationCount());
        }

        @Test
        @DisplayName("lockedUntil mặc định là null (tài khoản chưa bị khóa)")
        void defaultLockedUntil_isNull() {
            restoreOut();
            assertNull(bidder.getLockedUntil());
        }

        @Test
        @DisplayName("Chuyển status sang TEMP_LOCKED thành công")
        void setStatus_tempLocked() {
            restoreOut();
            bidder.setStatus("TEMP_LOCKED");
            assertEquals("TEMP_LOCKED", bidder.getStatus());
        }

        @Test
        @DisplayName("Chuyển status sang PERM_LOCKED thành công")
        void setStatus_permLocked() {
            restoreOut();
            bidder.setStatus("PERM_LOCKED");
            assertEquals("PERM_LOCKED", bidder.getStatus());
        }

        @Test
        @DisplayName("Tăng violationCount thành công")
        void violationCount_increment() {
            restoreOut();
            bidder.setViolationCount(1);
            assertEquals(1, bidder.getViolationCount());
            bidder.setViolationCount(bidder.getViolationCount() + 1);
            assertEquals(2, bidder.getViolationCount());
        }

        @Test
        @DisplayName("setLockedUntil đặt thời gian khóa hợp lệ")
        void lockedUntil_setFutureTime() {
            restoreOut();
            LocalDateTime lockTime = LocalDateTime.now().plusDays(7);
            bidder.setLockedUntil(lockTime);
            assertEquals(lockTime, bidder.getLockedUntil());
        }

        @Test
        @DisplayName("setLockedUntil(null) – unlock tài khoản")
        void lockedUntil_setNull_unlocksAccount() {
            restoreOut();
            bidder.setLockedUntil(LocalDateTime.now().plusDays(1));
            assertNotNull(bidder.getLockedUntil());

            bidder.setLockedUntil(null);
            assertNull(bidder.getLockedUntil(), "Sau khi unlock, lockedUntil phải là null");
        }

        @Test
        @DisplayName("Kịch bản: vi phạm 3 lần → TEMP_LOCKED → sau đó ACTIVE lại")
        void scenario_violationThenLockThenUnlock() {
            restoreOut();
            // Tích lũy vi phạm
            bidder.setViolationCount(3);
            assertEquals(3, bidder.getViolationCount());

            // Khóa tạm thời
            bidder.setStatus("TEMP_LOCKED");
            bidder.setLockedUntil(LocalDateTime.now().plusHours(24));
            assertEquals("TEMP_LOCKED", bidder.getStatus());
            assertNotNull(bidder.getLockedUntil());

            // Mở khóa
            bidder.setStatus("ACTIVE");
            bidder.setLockedUntil(null);
            assertEquals("ACTIVE", bidder.getStatus());
            assertNull(bidder.getLockedUntil());
        }
    }

    // =====================================================================
    // 8. equals() & hashCode() KẾ THỪA TỪ Entity
    // =====================================================================

    @Nested
    @DisplayName("8. equals() và hashCode() kế thừa từ Entity")
    class EqualityTests {

        @Test
        @DisplayName("Hai Bidder cùng id thì equals() = true")
        void sameId_bidder_equalsTrue() {
            restoreOut();
            Bidder b1 = new Bidder("bidder-001", "user1", "p1", "e1@e.com");
            Bidder b2 = new Bidder("bidder-001", "user2", "p2", "e2@e.com");
            assertEquals(b1, b2, "Entity.equals() so sánh theo id");
        }

        @Test
        @DisplayName("Hai Seller khác id thì equals() = false")
        void differentId_seller_equalsFalse() {
            restoreOut();
            Seller s1 = new Seller("s-001", "u1", "p", "e1@e.com");
            Seller s2 = new Seller("s-002", "u2", "p", "e2@e.com");
            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("Hai đối tượng cùng id thì hashCode() bằng nhau")
        void sameId_sameHashCode() {
            restoreOut();
            Admin a1 = new Admin("admin-001", "u1", "p", "e1@e.com", 1);
            Admin a2 = new Admin("admin-001", "u2", "p", "e2@e.com", 2);
            assertEquals(a1.hashCode(), a2.hashCode());
        }

        @Test
        @DisplayName("Bidder và Seller cùng id không equals (khác class – getClass() check)")
        void differentClass_sameId_notEqual() {
            restoreOut();
            Bidder b = new Bidder("shared-id", "u", "p", "e@e.com");
            Seller s = new Seller("shared-id", "u", "p", "e@e.com");
            assertNotEquals(b, s,
                    "Entity.equals() dùng getClass() – khác class thì không equals");
        }

        @Test
        @DisplayName("equals(null) trả về false")
        void equals_null_returnsFalse() {
            restoreOut();
            assertNotEquals(null, bidder);
        }

        @Test
        @DisplayName("equals(chính nó) trả về true (reflexive)")
        void equals_self_returnsTrue() {
            restoreOut();
            assertEquals(bidder, bidder);
        }
    }
}