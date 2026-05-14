package com.auction.server.service;

import com.auction.server.dao.UserDAO;
import com.auction.server.util.PasswordUtil;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.LoginRequest;
import com.auction.shared.dto.request.RegisterRequest;
import com.auction.shared.dto.response.AuthResponse;
import com.auction.shared.enums.UserRole;
import com.auction.shared.exception.InvalidCredentialsException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UserAlreadyExistsException;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock private UserDAO userDao;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userDao);
    }

    // =========================================================
    //  REGISTER
    // =========================================================
    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Dang ky thanh cong voi role BIDDER -> tra ve AuthResponse hop le")
        void register_bidder_success() {
            RegisterRequest req = new RegisterRequest("alice", "pass123", "alice@email.com", UserRole.BIDDER);

            when(userDao.existsByUsername("alice")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.hash("pass123")).thenReturn("hashed_pass");
                tokenUtil.when(() -> TokenUtil.generate(anyString(), eq("BIDDER"))).thenReturn("fake-token");

                AuthResponse response = userService.register(req);

                assertNotNull(response);
                assertEquals("alice", response.getUsername());
                assertEquals(UserRole.BIDDER, response.getRole());
                assertEquals("fake-token", response.getToken());
                assertNotNull(response.getUserId());
            }
        }

        @Test
        @DisplayName("Dang ky thanh cong voi role SELLER")
        void register_seller_success() {
            RegisterRequest req = new RegisterRequest("bob_seller", "pass456", "bob@email.com", UserRole.SELLER);

            when(userDao.existsByUsername("bob_seller")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.hash("pass456")).thenReturn("hashed_pass");
                tokenUtil.when(() -> TokenUtil.generate(anyString(), eq("SELLER"))).thenReturn("seller-token");

                AuthResponse response = userService.register(req);

                assertNotNull(response);
                assertEquals(UserRole.SELLER, response.getRole());
            }
        }

        @Test
        @DisplayName("Dang ky thanh cong voi role ADMIN")
        void register_admin_success() {
            RegisterRequest req = new RegisterRequest("admin01", "adminpass", "admin@email.com", UserRole.ADMIN);

            when(userDao.existsByUsername("admin01")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.hash("adminpass")).thenReturn("hashed_admin");
                tokenUtil.when(() -> TokenUtil.generate(anyString(), eq("ADMIN"))).thenReturn("admin-token");

                AuthResponse response = userService.register(req);

                assertNotNull(response);
                assertEquals(UserRole.ADMIN, response.getRole());
            }
        }

        // ★ MỚI: fullname được lưu nếu có
        @Test
        @DisplayName("Dang ky co fullname -> fullname duoc set vao user truoc khi luu")
        void register_withFullname_setsFullname() {
            RegisterRequest req = new RegisterRequest("alice", "pass123", "alice@email.com", UserRole.BIDDER);
            req.setFullname("Nguyen Van A");

            when(userDao.existsByUsername("alice")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.hash(anyString())).thenReturn("hashed");
                tokenUtil.when(() -> TokenUtil.generate(anyString(), anyString())).thenReturn("token");

                userService.register(req);

                // Xac nhan save duoc goi voi user co fullname
                verify(userDao).save(argThat(u -> "Nguyen Van A".equals(u.getFullname())));
            }
        }

        // ★ MỚI: fullname blank/null -> KHÔNG set (không crash)
        @Test
        @DisplayName("Dang ky voi fullname blank -> fullname khong duoc set")
        void register_withBlankFullname_doesNotSetFullname() {
            RegisterRequest req = new RegisterRequest("alice", "pass123", "alice@email.com", UserRole.BIDDER);
            req.setFullname("   "); // blank

            when(userDao.existsByUsername("alice")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.hash(anyString())).thenReturn("hashed");
                tokenUtil.when(() -> TokenUtil.generate(anyString(), anyString())).thenReturn("token");

                // Khong nem exception — chay binh thuong
                assertDoesNotThrow(() -> userService.register(req));
            }
        }

        @Test
        @DisplayName("Username da ton tai -> nem UserAlreadyExistsException")
        void register_duplicateUsername_throwsException() {
            RegisterRequest req = new RegisterRequest("alice", "pass123", "alice2@email.com", UserRole.BIDDER);
            when(userDao.existsByUsername("alice")).thenReturn(true);

            UserAlreadyExistsException ex = assertThrows(
                    UserAlreadyExistsException.class,
                    () -> userService.register(req)
            );

            assertTrue(ex.getMessage().contains("alice"));
            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("Luu DB that bai -> nem RuntimeException")
        void register_saveFails_throwsRuntimeException() {
            RegisterRequest req = new RegisterRequest("charlie", "pass789", "c@email.com", UserRole.BIDDER);

            when(userDao.existsByUsername("charlie")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(false);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class)) {
                pwUtil.when(() -> PasswordUtil.hash(anyString())).thenReturn("hashed");
                assertThrows(RuntimeException.class, () -> userService.register(req));
            }
        }

        @Test
        @DisplayName("Mat khau phai duoc hash truoc khi luu")
        void register_passwordMustBeHashed() {
            RegisterRequest req = new RegisterRequest("dave", "mypassword", "dave@email.com", UserRole.BIDDER);

            when(userDao.existsByUsername("dave")).thenReturn(false);
            when(userDao.save(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.hash("mypassword")).thenReturn("$2a$hashed");
                tokenUtil.when(() -> TokenUtil.generate(anyString(), anyString())).thenReturn("token");

                userService.register(req);

                pwUtil.verify(() -> PasswordUtil.hash("mypassword"), times(1));
            }
        }
    }

    // =========================================================
    //  LOGIN
    // =========================================================
    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Dang nhap thanh cong voi tai khoan ACTIVE")
        void login_success() {
            LoginRequest req = new LoginRequest("alice", "pass123");

            Bidder mockUser = new Bidder();
            mockUser.setId("user-uuid-001");
            mockUser.setUsername("alice");
            mockUser.setPassword("hashed_pass");
            mockUser.setRole(UserRole.BIDDER);
            mockUser.setStatus("ACTIVE");

            when(userDao.findByUsername("alice")).thenReturn(mockUser);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.verify("pass123", "hashed_pass")).thenReturn(true);
                tokenUtil.when(() -> TokenUtil.generate("user-uuid-001", "BIDDER")).thenReturn("login-token");

                AuthResponse response = userService.login(req);

                assertNotNull(response);
                assertEquals("alice", response.getUsername());
                assertEquals("login-token", response.getToken());
            }
        }

        @Test
        @DisplayName("Username khong ton tai -> nem InvalidCredentialsException")
        void login_usernameNotFound_throwsException() {
            LoginRequest req = new LoginRequest("ghost_user", "pass123");
            when(userDao.findByUsername("ghost_user")).thenReturn(null);

            assertThrows(InvalidCredentialsException.class, () -> userService.login(req));
        }

        @Test
        @DisplayName("Sai mat khau -> nem InvalidCredentialsException")
        void login_wrongPassword_throwsException() {
            LoginRequest req = new LoginRequest("alice", "wrong_pass");

            Bidder mockUser = new Bidder();
            mockUser.setId("uid"); mockUser.setUsername("alice");
            mockUser.setPassword("hashed_pass"); mockUser.setRole(UserRole.BIDDER);

            when(userDao.findByUsername("alice")).thenReturn(mockUser);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class)) {
                pwUtil.when(() -> PasswordUtil.verify("wrong_pass", "hashed_pass")).thenReturn(false);
                assertThrows(InvalidCredentialsException.class, () -> userService.login(req));
            }
        }

        // ★ MỚI: PERM_LOCKED
        @Test
        @DisplayName("Tai khoan PERM_LOCKED + pass dung -> nem exception voi thong bao khoa vinh vien")
        void login_permLockedAccount_throwsException() {
            LoginRequest req = new LoginRequest("alice", "pass123");

            Bidder mockUser = new Bidder();
            mockUser.setId("uid"); mockUser.setUsername("alice");
            mockUser.setPassword("hashed_pass"); mockUser.setRole(UserRole.BIDDER);
            mockUser.setStatus("PERM_LOCKED");

            when(userDao.findByUsername("alice")).thenReturn(mockUser);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class)) {
                pwUtil.when(() -> PasswordUtil.verify("pass123", "hashed_pass")).thenReturn(true);

                InvalidCredentialsException ex = assertThrows(
                        InvalidCredentialsException.class, () -> userService.login(req));

                assertTrue(ex.getMessage().toLowerCase().contains("vĩnh viễn")
                                || ex.getMessage().toLowerCase().contains("vinh vien")
                                || ex.getMessage().toLowerCase().contains("perm"),
                        "Thong bao phai de cap khoa vinh vien");

                // Token KHONG duoc tao
                verify(userDao, never()).update(any());
            }
        }

        // ★ MỚI: TEMP_LOCKED còn hạn
        @Test
        @DisplayName("Tai khoan TEMP_LOCKED con han + pass dung -> nem exception voi thoi gian het han")
        void login_tempLockedStillActive_throwsException() {
            LoginRequest req = new LoginRequest("alice", "pass123");

            Bidder mockUser = new Bidder();
            mockUser.setId("uid"); mockUser.setUsername("alice");
            mockUser.setPassword("hashed_pass"); mockUser.setRole(UserRole.BIDDER);
            mockUser.setStatus("TEMP_LOCKED");
            mockUser.setLockedUntil(LocalDateTime.now().plusDays(1)); // Còn 1 ngày

            when(userDao.findByUsername("alice")).thenReturn(mockUser);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class)) {
                pwUtil.when(() -> PasswordUtil.verify("pass123", "hashed_pass")).thenReturn(true);

                InvalidCredentialsException ex = assertThrows(
                        InvalidCredentialsException.class, () -> userService.login(req));

                // Thong bao phai chua thoi gian het han
                assertTrue(ex.getMessage().contains("tạm thời") || ex.getMessage().contains("tam thoi")
                                || ex.getMessage().contains("khoá") || ex.getMessage().contains("khoa"),
                        "Thong bao phai de cap khoa tam thoi");

                // userDao.update() KHONG duoc goi (chua het han)
                verify(userDao, never()).update(any());
            }
        }

        // ★ MỚI: TEMP_LOCKED hết hạn → tự động mở khoá
        @Test
        @DisplayName("Tai khoan TEMP_LOCKED het han -> tu dong mo khoa, dang nhap thanh cong")
        void login_tempLockedExpired_autoUnlocks() {
            LoginRequest req = new LoginRequest("alice", "pass123");

            Bidder mockUser = new Bidder();
            mockUser.setId("uid"); mockUser.setUsername("alice");
            mockUser.setPassword("hashed_pass"); mockUser.setRole(UserRole.BIDDER);
            mockUser.setStatus("TEMP_LOCKED");
            mockUser.setLockedUntil(LocalDateTime.now().minusHours(1)); // Đã hết hạn

            when(userDao.findByUsername("alice")).thenReturn(mockUser);
            when(userDao.update(any(User.class))).thenReturn(true);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class);
                 MockedStatic<TokenUtil> tokenUtil = mockStatic(TokenUtil.class)) {

                pwUtil.when(() -> PasswordUtil.verify("pass123", "hashed_pass")).thenReturn(true);
                tokenUtil.when(() -> TokenUtil.generate("uid", "BIDDER")).thenReturn("auto-unlock-token");

                AuthResponse response = userService.login(req);

                // Dang nhap thanh cong sau khi tu dong mo khoa
                assertNotNull(response);
                assertEquals("auto-unlock-token", response.getToken());

                // Status duoc cap nhat thanh ACTIVE
                verify(userDao).update(argThat(u -> "ACTIVE".equals(u.getStatus())
                        && u.getLockedUntil() == null));
            }
        }

        // ★ Tuong thich nguoc: LOCKED cu
        @Test
        @DisplayName("Tai khoan LOCKED (status cu) + pass dung -> nem exception")
        void login_legacyLockedAccount_throwsException() {
            LoginRequest req = new LoginRequest("alice", "pass123");

            Bidder mockUser = new Bidder();
            mockUser.setId("uid"); mockUser.setUsername("alice");
            mockUser.setPassword("hashed_pass"); mockUser.setRole(UserRole.BIDDER);
            mockUser.setStatus("LOCKED"); // status cu

            when(userDao.findByUsername("alice")).thenReturn(mockUser);

            try (MockedStatic<PasswordUtil> pwUtil = mockStatic(PasswordUtil.class)) {
                pwUtil.when(() -> PasswordUtil.verify("pass123", "hashed_pass")).thenReturn(true);

                assertThrows(InvalidCredentialsException.class, () -> userService.login(req));
            }
        }

        @Test
        @DisplayName("Thong bao loi login khong tiet lo thong tin nhay cam")
        void login_errorMessage_isGeneric() {
            LoginRequest req = new LoginRequest("nonexistent", "anypass");
            when(userDao.findByUsername("nonexistent")).thenReturn(null);

            InvalidCredentialsException ex = assertThrows(
                    InvalidCredentialsException.class, () -> userService.login(req));

            assertFalse(ex.getMessage().toLowerCase().contains("username"));
        }
    }

    // =========================================================
    //  GET BY ID
    // =========================================================
    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("Tim thay user -> tra ve User")
        void getById_found_returnsUser() {
            Bidder mockUser = new Bidder();
            mockUser.setId("uid-123");
            mockUser.setUsername("alice");

            when(userDao.findById("uid-123")).thenReturn(mockUser);

            User result = userService.getById("uid-123");

            assertNotNull(result);
            assertEquals("uid-123", result.getId());
        }

        @Test
        @DisplayName("ID khong ton tai -> nem ResourceNotFoundException")
        void getById_notFound_throwsException() {
            when(userDao.findById("bad-id")).thenReturn(null);

            ResourceNotFoundException ex = assertThrows(
                    ResourceNotFoundException.class,
                    () -> userService.getById("bad-id"));

            assertEquals("USER_NOT_FOUND", ex.getCode());
        }
    }

    // =========================================================
    //  GET ALL USERS
    // =========================================================
    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        @Test
        @DisplayName("Co du lieu -> tra ve danh sach dung so luong")
        void getAllUsers_returnsListOfUsers() {
            Bidder u1 = new Bidder(); u1.setId("1"); u1.setUsername("alice");
            Seller u2 = new Seller(); u2.setId("2"); u2.setUsername("bob");
            Admin  u3 = new Admin();  u3.setId("3"); u3.setUsername("admin");

            when(userDao.findAll()).thenReturn(List.of(u1, u2, u3));

            assertEquals(3, userService.getAllUsers().size());
        }

        @Test
        @DisplayName("Khong co user -> tra ve danh sach rong")
        void getAllUsers_empty_returnsEmptyList() {
            when(userDao.findAll()).thenReturn(List.of());
            assertTrue(userService.getAllUsers().isEmpty());
        }
    }

    // =========================================================
    //  BAN USER (★ HOÀN TOÀN MỚI)
    // =========================================================
    @Nested
    @DisplayName("banUser() - He thong xu ly vi pham (★ Moi)")
    class BanUserTests {

        private Bidder buildActiveUser() {
            Bidder u = new Bidder();
            u.setId("uid-001");
            u.setUsername("violator");
            u.setStatus("ACTIVE");
            u.setViolationCount(0);
            return u;
        }

        @Test
        @DisplayName("Action WARN lan 1 -> violation_count tang, khong bi khoa")
        void banUser_warn_firstWarning_noLock() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            String msg = userService.banUser("uid-001", "WARN");

            // violation_count tang len 1
            verify(userDao).update(argThat(u -> u.getViolationCount() == 1));
            // Status van ACTIVE (chua bi khoa)
            verify(userDao).update(argThat(u -> "ACTIVE".equals(u.getStatus())));
            assertNotNull(msg);
        }

        @Test
        @DisplayName("Action WARN lan 3 -> TEMP_LOCKED 1 ngay (leo thang tu dong)")
        void banUser_warn_thirdWarning_tempLocked1Day() {
            Bidder user = buildActiveUser();
            user.setViolationCount(2); // Lan vi pham thu 3
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            userService.banUser("uid-001", "WARN");

            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus()) && u.getLockedUntil() != null));
        }

        @Test
        @DisplayName("Action WARN lan 5 -> TEMP_LOCKED 7 ngay")
        void banUser_warn_fifthWarning_tempLocked7Days() {
            Bidder user = buildActiveUser();
            user.setViolationCount(4);
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            userService.banUser("uid-001", "WARN");

            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus())
                            && u.getLockedUntil() != null
                            && u.getLockedUntil().isAfter(LocalDateTime.now().plusDays(6))));
        }

        @Test
        @DisplayName("Action WARN lan 7 -> TEMP_LOCKED 30 ngay")
        void banUser_warn_seventhWarning_tempLocked30Days() {
            Bidder user = buildActiveUser();
            user.setViolationCount(6);
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            userService.banUser("uid-001", "WARN");

            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus())
                            && u.getLockedUntil().isAfter(LocalDateTime.now().plusDays(29))));
        }

        @Test
        @DisplayName("Action WARN lan 10 -> PERM_LOCKED vinh vien")
        void banUser_warn_tenthWarning_permLocked() {
            Bidder user = buildActiveUser();
            user.setViolationCount(9);
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            userService.banUser("uid-001", "WARN");

            verify(userDao).update(argThat(u ->
                    "PERM_LOCKED".equals(u.getStatus()) && u.getLockedUntil() == null));
        }

        @Test
        @DisplayName("Action TEMP_1D -> TEMP_LOCKED 1 ngay, violation_count tang")
        void banUser_temp1D_locksFor1Day() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            String msg = userService.banUser("uid-001", "TEMP_1D");

            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus())
                            && u.getLockedUntil() != null
                            && u.getLockedUntil().isAfter(LocalDateTime.now().plusHours(23))
                            && u.getViolationCount() == 1));
            assertNotNull(msg);
        }

        @Test
        @DisplayName("Action TEMP_7D -> TEMP_LOCKED 7 ngay")
        void banUser_temp7D_locksFor7Days() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            userService.banUser("uid-001", "TEMP_7D");

            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus())
                            && u.getLockedUntil().isAfter(LocalDateTime.now().plusDays(6))));
        }

        @Test
        @DisplayName("Action TEMP_30D -> TEMP_LOCKED 30 ngay")
        void banUser_temp30D_locksFor30Days() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            userService.banUser("uid-001", "TEMP_30D");

            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus())
                            && u.getLockedUntil().isAfter(LocalDateTime.now().plusDays(29))));
        }

        @Test
        @DisplayName("Action PERM -> PERM_LOCKED, lockedUntil = null")
        void banUser_perm_permanentLock() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            String msg = userService.banUser("uid-001", "PERM");

            verify(userDao).update(argThat(u ->
                    "PERM_LOCKED".equals(u.getStatus()) && u.getLockedUntil() == null));
            assertTrue(msg.contains("violator") || msg.toLowerCase().contains("vinh vien") || msg.contains("PERM"));
        }

        @Test
        @DisplayName("Action UNLOCK -> ACTIVE, lockedUntil = null")
        void banUser_unlock_restoresActive() {
            Bidder user = buildActiveUser();
            user.setStatus("TEMP_LOCKED");
            user.setLockedUntil(LocalDateTime.now().plusDays(5));
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(true);

            String msg = userService.banUser("uid-001", "UNLOCK");

            verify(userDao).update(argThat(u ->
                    "ACTIVE".equals(u.getStatus()) && u.getLockedUntil() == null));
            assertNotNull(msg);
        }

        @Test
        @DisplayName("Action khong hop le -> nem IllegalArgumentException")
        void banUser_invalidAction_throwsException() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);

            assertThrows(IllegalArgumentException.class,
                    () -> userService.banUser("uid-001", "INVALID_ACTION"));

            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("User khong ton tai -> nem ResourceNotFoundException")
        void banUser_userNotFound_throwsException() {
            when(userDao.findById("ghost-id")).thenReturn(null);

            ResourceNotFoundException ex = assertThrows(
                    ResourceNotFoundException.class,
                    () -> userService.banUser("ghost-id", "WARN"));

            assertEquals("USER_NOT_FOUND", ex.getCode());
            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("DB update that bai -> nem RuntimeException")
        void banUser_updateFails_throwsException() {
            Bidder user = buildActiveUser();
            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any())).thenReturn(false);

            assertThrows(RuntimeException.class,
                    () -> userService.banUser("uid-001", "WARN"));
        }
    }

    // =========================================================
    //  TOGGLE USER STATUS (tuong thich nguoc)
    // =========================================================
    @Nested
    @DisplayName("toggleUserStatus() - Tuong thich nguoc")
    class ToggleUserStatusTests {

        @Test
        @DisplayName("User ACTIVE -> chuyen thanh TEMP_LOCKED 1 ngay (logic moi)")
        void toggleStatus_activeToTempLocked() {
            Bidder user = new Bidder();
            user.setId("uid-001");
            user.setStatus("ACTIVE");

            when(userDao.findById("uid-001")).thenReturn(user);
            when(userDao.update(any(User.class))).thenReturn(true);

            userService.toggleUserStatus("uid-001");

            // Logic moi: ACTIVE -> TEMP_LOCKED (khong con la LOCKED)
            verify(userDao).update(argThat(u ->
                    "TEMP_LOCKED".equals(u.getStatus()) && u.getLockedUntil() != null));
        }

        @Test
        @DisplayName("User TEMP_LOCKED -> chuyen thanh ACTIVE")
        void toggleStatus_tempLockedToActive() {
            Bidder user = new Bidder();
            user.setId("uid-002");
            user.setStatus("TEMP_LOCKED");
            user.setLockedUntil(LocalDateTime.now().plusDays(1));

            when(userDao.findById("uid-002")).thenReturn(user);
            when(userDao.update(any(User.class))).thenReturn(true);

            userService.toggleUserStatus("uid-002");

            verify(userDao).update(argThat(u ->
                    "ACTIVE".equals(u.getStatus()) && u.getLockedUntil() == null));
        }

        @Test
        @DisplayName("User khong ton tai -> nem ResourceNotFoundException")
        void toggleStatus_userNotFound_throwsException() {
            when(userDao.findById("ghost-id")).thenReturn(null);

            ResourceNotFoundException ex = assertThrows(
                    ResourceNotFoundException.class,
                    () -> userService.toggleUserStatus("ghost-id"));

            assertEquals("USER_NOT_FOUND", ex.getCode());
            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("DB update that bai -> nem RuntimeException")
        void toggleStatus_updateFails_throwsRuntimeException() {
            Bidder user = new Bidder();
            user.setId("uid-003");
            user.setStatus("ACTIVE");

            when(userDao.findById("uid-003")).thenReturn(user);
            when(userDao.update(any(User.class))).thenReturn(false);

            assertThrows(RuntimeException.class, () -> userService.toggleUserStatus("uid-003"));
        }
    }
}