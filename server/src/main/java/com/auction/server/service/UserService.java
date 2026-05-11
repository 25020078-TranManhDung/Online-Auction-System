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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserService xử lý logic nghiệp vụ liên quan đến người dùng.
 * Áp dụng Dependency Injection để dễ dàng kiểm thử (Unit Test).
 */
public class UserService {

    private final UserDAO userDao;

    public UserService(UserDAO userDao) {
        this.userDao = userDao;
    }

    public AuthResponse register(RegisterRequest request) {
        // 1. Kiểm tra username đã tồn tại chưa
        if (userDao.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Tên đăng nhập '" + request.getUsername() + "' đã tồn tại.");
        }

        // 2. Tạo đối tượng User mới, gán UUID ngay sau khi khởi tạo
        User user;
        switch (request.getRole()) {
            case BIDDER:
                user = new Bidder();
                user.setId(UUID.randomUUID().toString());
                break;
            case SELLER:
                user = new Seller();
                user.setId(UUID.randomUUID().toString());
                break;
            case ADMIN:
                user = new Admin();
                user.setId(UUID.randomUUID().toString());
                break;
            default:
                throw new IllegalArgumentException("Vai trò không hợp lệ!");
        }
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        // 3. Hash mật khẩu bằng BCrypt
        String hashedPw = PasswordUtil.hash(request.getPassword());
        user.setPassword(hashedPw);

        // Gán vai trò
        user.setRole(request.getRole());

        // 4. Lưu vào Database
        boolean isSaved = userDao.save(user);
        if (!isSaved) {
            throw new RuntimeException("Đã xảy ra lỗi khi lưu người dùng vào cơ sở dữ liệu.");
        }

        // 5. Tạo JWT Token (user.getId() lúc này chắc chắn không null)
        String token = TokenUtil.generate(user.getId(), user.getRole().name());

        return new AuthResponse(user.getId(), user.getUsername(), user.getRole(), token);
    }

    public AuthResponse login(LoginRequest request) {
        // 1. Tìm người dùng theo username
        User user = userDao.findByUsername(request.getUsername());
        if (user == null) {
            throw new InvalidCredentialsException("Sai tên đăng nhập hoặc mật khẩu.");
        }

        // 2. Kiểm tra mật khẩu
        if (!PasswordUtil.verify(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Sai tên đăng nhập hoặc mật khẩu.");
        }

        // 3. Tạo token mới
        String token = TokenUtil.generate(user.getId(), user.getRole().name());

        return new AuthResponse(user.getId(), user.getUsername(), user.getRole(), token);
    }

    public User getById(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(
                "USER_NOT_FOUND",
                "Không tìm thấy người dùng với ID: " + userId
            );
        }
        return user;
    }

    /**
     * Lấy danh sách toàn bộ người dùng.
     */
    public List<User> getAllUsers() {
        return userDao.findAll();
    }

    /**
     * Khóa hoặc mở khóa tài khoản người dùng.
     */
    public void toggleUserStatus(String userId) {
        // 1. Tìm user trong DB
        User user = userDao.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(
                "USER_NOT_FOUND",
                "Không tìm thấy người dùng với ID: " + userId
            );
        }

        // 2. Đảo ngược trạng thái
        String currentStatus = user.getStatus();
        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            user.setStatus("LOCKED");
        } else {
            user.setStatus("ACTIVE");
        }

        // 3. Cập nhật lại vào Database
        boolean isUpdated = userDao.update(user);
        if (!isUpdated) {
            throw new RuntimeException("Lỗi khi cập nhật trạng thái người dùng vào cơ sở dữ liệu.");
        }
    }
}