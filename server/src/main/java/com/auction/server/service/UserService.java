package com.auction.server.service;

import com.auction.server.dao.UserDAO;
import com.auction.shared.dto.request.LoginRequest;
import com.auction.shared.dto.request.RegisterRequest;
import com.auction.shared.dto.response.AuthResponse;
import com.auction.shared.model.user.User;
import com.auction.shared.enums.UserRole;
import com.auction.shared.exception.UserAlreadyExistsException;
import com.auction.shared.exception.InvalidCredentialsException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.server.util.PasswordUtil;
import com.auction.server.util.TokenUtil;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.Admin;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import com.auction.shared.dto.request.ChangePasswordRequest;

public class UserService {

    private final UserDAO userDao;

    public UserService(UserDAO userDao) {
        this.userDao = userDao;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userDao.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Tên đăng nhập '" + request.getUsername() + "' đã tồn tại.");
        }

        User user;
        switch (request.getRole()) {
            case BIDDER: user = new Bidder(); user.setId(UUID.randomUUID().toString()); break;
            case SELLER: user = new Seller(); user.setId(UUID.randomUUID().toString()); break;
            case ADMIN:  user = new Admin();  user.setId(UUID.randomUUID().toString()); break;
            default: throw new IllegalArgumentException("Vai trò không hợp lệ!");
        }
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(PasswordUtil.hash(request.getPassword()));
        user.setRole(request.getRole());
        // Lưu họ và tên (nếu client truyền lên, không bắt buộc)
        if (request.getFullname() != null && !request.getFullname().isBlank()) {
            user.setFullname(request.getFullname().trim());
        }

        if (!userDao.save(user)) {
            throw new RuntimeException("Đã xảy ra lỗi khi lưu người dùng vào cơ sở dữ liệu.");
        }

        String token = TokenUtil.generate(user.getId(), user.getRole().name());

        AuthResponse auth = new AuthResponse(user.getId(), user.getUsername(), user.getRole(), token);
        auth.setFullName(user.getFullname());
        auth.setEmail(user.getEmail());

        return auth;
    }

    /**
     * Đăng nhập — 3 lớp kiểm tra trạng thái tài khoản:
     * 1. PERM_LOCKED  → từ chối ngay
     * 2. TEMP_LOCKED + lockedUntil > now → từ chối + thông báo thời gian còn lại
     * 3. TEMP_LOCKED + lockedUntil <= now → tự động mở khóa (→ ACTIVE)
     */
    public AuthResponse login(LoginRequest request) {
        User user = userDao.findByUsername(request.getUsername());
        if (user == null) {
            throw new InvalidCredentialsException("Sai tên đăng nhập hoặc mật khẩu.");
        }

        if (!PasswordUtil.verify(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Sai tên đăng nhập hoặc mật khẩu.");
        }

        String status = user.getStatus();

        // Lớp 1: Khoá vĩnh viễn
        if ("PERM_LOCKED".equalsIgnoreCase(status)) {
            throw new InvalidCredentialsException(
                "Tài khoản của bạn đã bị khoá vĩnh viễn. Vui lòng liên hệ quản trị viên.");
        }

        // Lớp 2 & 3: Khoá tạm thời
        if ("TEMP_LOCKED".equalsIgnoreCase(status)) {
            LocalDateTime now = LocalDateTime.now();
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                // Vẫn còn trong thời gian khoá
                String until = user.getLockedUntil()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                throw new InvalidCredentialsException(
                    "Tài khoản bị khoá tạm thời đến " + until + ". Vui lòng thử lại sau.");
            } else {
                // Hết thời gian khoá → tự động mở
                user.setStatus("ACTIVE");
                user.setLockedUntil(null);
                userDao.update(user);
                System.out.println("[UserService] Tự động mở khoá tài khoản: " + user.getUsername());
            }
        }

        // Tương thích ngược với status cũ "LOCKED"
        if ("LOCKED".equalsIgnoreCase(status)) {
            throw new InvalidCredentialsException(
                "Tài khoản của bạn đã bị khoá. Vui lòng liên hệ quản trị viên.");
        }

        String token = TokenUtil.generate(user.getId(), user.getRole().name());

        // Tạo túi AuthResponse
        AuthResponse auth = new AuthResponse(user.getId(), user.getUsername(), user.getRole(), token);

        // NHÉT THÊM HỌ TÊN VÀ EMAIL VÀO TÚI
        auth.setFullName(user.getFullname());
        auth.setEmail(user.getEmail());

        // ✅ THÊM DÒNG NÀY: Lấy chuỗi ảnh từ Database nhét vào túi để gửi về Client
        auth.setAvatarBase64(user.getAvatar());

        return auth;
    }

    public User getById(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND",
                "Không tìm thấy người dùng với ID: " + userId);
        }
        return user;
    }

    public List<User> getAllUsers() {
        return userDao.findAll();
    }

    /**
     * Xử lý vi phạm theo mức độ admin chọn:
     *   "WARN"       → violation_count +1, không khoá
     *   "TEMP_1D"    → TEMP_LOCKED, locked_until = now + 1 ngày
     *   "TEMP_7D"    → TEMP_LOCKED, locked_until = now + 7 ngày
     *   "TEMP_30D"   → TEMP_LOCKED, locked_until = now + 30 ngày
     *   "PERM"       → PERM_LOCKED, locked_until = null
     *   "UNLOCK"     → ACTIVE, locked_until = null (mở khoá thủ công)
     */
    public String banUser(String targetUserId, String action) {
        User user = userDao.findById(targetUserId);
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND",
                "Không tìm thấy người dùng với ID: " + targetUserId);
        }

        LocalDateTime now = LocalDateTime.now();
        String message;

        switch (action.toUpperCase()) {
            case "WARN" -> {
                user.setViolationCount(user.getViolationCount() + 1);
                int count = user.getViolationCount();

                // Leo thang tự động theo số lần vi phạm
                if (count >= 10) {
                    user.setStatus("PERM_LOCKED");
                    user.setLockedUntil(null);
                    message = "⛔ Cảnh cáo lần " + count + " — Tự động khoá VĨNH VIỄN do vi phạm quá mức.";
                } else if (count >= 7) {
                    user.setStatus("TEMP_LOCKED");
                    user.setLockedUntil(now.plusDays(30));
                    message = "🔴 Cảnh cáo lần " + count + " — Tự động khoá 30 ngày đến "
                        + user.getLockedUntil().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                } else if (count >= 5) {
                    user.setStatus("TEMP_LOCKED");
                    user.setLockedUntil(now.plusDays(7));
                    message = "🟠 Cảnh cáo lần " + count + " — Tự động khoá 7 ngày đến "
                        + user.getLockedUntil().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                } else if (count >= 3) {
                    user.setStatus("TEMP_LOCKED");
                    user.setLockedUntil(now.plusDays(1));
                    message = "🟡 Cảnh cáo lần " + count + " — Tự động khoá 1 ngày đến "
                        + user.getLockedUntil().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                } else {
                    message = "⚠️ Cảnh cáo lần " + count + "/3. Còn " + (3 - count) + " lần trước khi bị khoá.";
                }
            }
            case "TEMP_1D" -> {
                user.setStatus("TEMP_LOCKED");
                user.setLockedUntil(now.plusDays(1));
                user.setViolationCount(user.getViolationCount() + 1);
                message = "Đã khoá tạm 1 ngày đến " +
                    user.getLockedUntil().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            }
            case "TEMP_7D" -> {
                user.setStatus("TEMP_LOCKED");
                user.setLockedUntil(now.plusDays(7));
                user.setViolationCount(user.getViolationCount() + 1);
                message = "Đã khoá tạm 7 ngày đến " +
                    user.getLockedUntil().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            }
            case "TEMP_30D" -> {
                user.setStatus("TEMP_LOCKED");
                user.setLockedUntil(now.plusDays(30));
                user.setViolationCount(user.getViolationCount() + 1);
                message = "Đã khoá tạm 30 ngày đến " +
                    user.getLockedUntil().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            }
            case "PERM" -> {
                user.setStatus("PERM_LOCKED");
                user.setLockedUntil(null);
                user.setViolationCount(user.getViolationCount() + 1);
                message = "Đã khoá vĩnh viễn tài khoản: " + user.getUsername();
            }
            case "UNLOCK" -> {
                user.setStatus("ACTIVE");
                user.setLockedUntil(null);
                message = "Đã mở khoá tài khoản: " + user.getUsername();
            }
            default -> throw new IllegalArgumentException("Hành động không hợp lệ: " + action);
        }

        if (!userDao.update(user)) {
            throw new RuntimeException("Lỗi khi cập nhật trạng thái người dùng.");
        }

        return message;
    }

    /** Giữ lại để tương thích ngược với code cũ dùng TOGGLE_USER_STATUS */
    public void toggleUserStatus(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND",
                "Không tìm thấy người dùng với ID: " + userId);
        }
        String currentStatus = user.getStatus();
        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            user.setStatus("TEMP_LOCKED");
            user.setLockedUntil(LocalDateTime.now().plusDays(1));
        } else {
            user.setStatus("ACTIVE");
            user.setLockedUntil(null);
        }
        if (!userDao.update(user)) {
            throw new RuntimeException("Lỗi khi cập nhật trạng thái người dùng vào cơ sở dữ liệu.");
        }
    }

    /**
     * Xử lý logic Đổi mật khẩu:
     * 1. Tìm User theo ID.
     * 2. Kiểm tra mật khẩu cũ có khớp không.
     * 3. Băm mật khẩu mới và lưu xuống Database.
     */
    public void changePassword(ChangePasswordRequest req) {
        // 1. Tìm người dùng trong DB
        User user = userDao.findById(req.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng với ID này.");
        }

        // 2. Kiểm tra mật khẩu cũ
        if (!PasswordUtil.verify(req.getOldPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Mật khẩu hiện tại không chính xác!");
        }

        // 3. (Tùy chọn) Kiểm tra xem mật khẩu mới có trùng với mật khẩu cũ không
        if (PasswordUtil.verify(req.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        // 4. Băm mật khẩu mới và cập nhật
        user.setPassword(PasswordUtil.hash(req.getNewPassword()));

        if (!userDao.update(user)) {
            throw new RuntimeException("Đã xảy ra lỗi khi cập nhật mật khẩu vào cơ sở dữ liệu.");
        }

        System.out.println("[UserService] Đổi mật khẩu thành công cho user: " + user.getUsername());
    }

    /**
     * Xử lý logic Cập nhật ảnh đại diện:
     * 1. Tìm User theo ID.
     * 2. Gán chuỗi Base64 mới (hoặc null nếu xóa).
     * 3. Lưu xuống Database.
     */
    public void updateAvatar(String userId, String avatarBase64) {
        // 1. Tìm người dùng trong DB
        User user = userDao.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng với ID này.");
        }

        // 2. Cập nhật trường avatar
        user.setAvatar(avatarBase64);

        // 3. Lưu xuống cơ sở dữ liệu
        if (!userDao.update(user)) {
            throw new RuntimeException("Đã xảy ra lỗi khi cập nhật ảnh đại diện vào cơ sở dữ liệu.");
        }

        System.out.println("[UserService] Cập nhật ảnh đại diện thành công cho user: " + user.getUsername());
    }
}


/**
 * UserService xử lý logic nghiệp vụ liên quan đến người dùng.
 * Áp dụng Dependency Injection để dễ dàng kiểm thử (Unit Test).
 */