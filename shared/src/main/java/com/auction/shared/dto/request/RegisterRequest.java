package com.auction.shared.dto.request;
import com.auction.shared.enums.UserRole;

import java.io.Serializable;

//Lớp DTO (Data Transfer Object) chứa thông tin yêu cầu đăng ký tài khoản từ Client gửi lên Server.

public class RegisterRequest implements Serializable {
    // Tên đăng nhập của người dùng mới
    private String username;

    // Mật khẩu của người dùng
    private String password;

    // Địa chỉ email liên hệ
    private String email;

    // Vai trò của người dùng khi đăng ký vào hệ thống
    // Có thể là Bidder (tham gia đấu giá), Seller (đăng sản phẩm) hoặc Admin (quản lý)
    private UserRole role;

    /**
     * Constructor rỗng (Mặc định).
     * BẮT BUỘC PHẢI CÓ để các thư viện xử lý JSON (như Jackson/Gson) trên Server
     * có thể tự động khởi tạo đối tượng khi nhận dữ liệu từ Client gửi lên.
     */
    public RegisterRequest() {}

    /**
     * Constructor đầy đủ tham số.
     * Giúp phía Client (JavaFX) khởi tạo đối tượng nhanh chóng và nhét dữ liệu vào
     * ngay khi người dùng bấm nút "Đăng ký" trên giao diện.
     */
    public RegisterRequest(String username, String password, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    public String getUsername()  {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword()  {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole()    {
        return role;
    }
    public void setRole(UserRole role) {
        this.role = role;
    }
}
