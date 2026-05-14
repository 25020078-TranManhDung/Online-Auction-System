package com.auction.shared.dto.request;
import com.auction.shared.enums.UserRole;

import java.io.Serializable;

//Lớp DTO (Data Transfer Object) chứa thông tin yêu cầu đăng ký tài khoản từ Client gửi lên Server.

public class RegisterRequest implements Serializable {
    private String username;
    private String password;
    private String email;
    private String fullname;   // Họ và tên — nhận từ form đăng ký
    private UserRole role;

    public RegisterRequest() {}

    public RegisterRequest(String username, String password, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.email    = email;
        this.role     = role;
    }

    public String getUsername()  { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword()  { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public UserRole getRole()    { return role; }
    public void setRole(UserRole role) { this.role = role; }
}