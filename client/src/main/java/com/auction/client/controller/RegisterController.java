package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions; // Import Actions từ gói shared của bạn

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.HashMap;
import java.util.Map;

public class RegisterController {

    @FXML private TextField regFullname;
    @FXML private TextField regUsername;
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regConfirmPassword;

    // Thêm khai báo nút bấm để đổi text và khóa nút khi đang xử lý
    @FXML private Button btnRegister;

    /**
     * DTO: Hứng dữ liệu trả về từ Server cho action REGISTER.
     * Khớp với trường "data" của ServerResponse trong PROTOCOL.md
     */
    public static class RegisterResponseData {
        public String userId;
        public String username;
        public String message;
    }

    /**
     * Xử lý khi người dùng nhấn nút "TẠO TÀI KHOẢN"
     */
    @FXML
    private void handleRegister(ActionEvent event) {
        String fullname = regFullname.getText().trim();
        String username = regUsername.getText().trim();
        String password = regPassword.getText().trim();
        String confirmPassword = regConfirmPassword.getText().trim();

        // 1. Kiểm tra nhập liệu cơ bản
        if (fullname.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            AlertUtil.showWarning("Thiếu thông tin", "Vui lòng nhập đầy đủ các thông tin!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            AlertUtil.showWarning("Lỗi mật khẩu", "Mật khẩu xác nhận không trùng khớp!");
            return;
        }

        // 2. Khóa nút bấm để tránh click nhiều lần
        btnRegister.setDisable(true);
        btnRegister.setText("Đang xử lý...");

        // 3. Mở luồng phụ (Background Thread) để gọi mạng
        new Thread(() -> {
            try {
                // Chuẩn bị Request Data chuẩn theo PROTOCOL.md
                Map<String, String> requestData = new HashMap<>();
                requestData.put("username", username);
                requestData.put("password", password);

                // Bổ sung các trường Server yêu cầu nhưng giao diện chưa có
                requestData.put("email", username + "@gmail.com"); // Email giả định
                requestData.put("role", "BIDDER");                 // Mặc định đăng ký là người mua

                // Gửi Request đồng bộ
                RegisterResponseData response = SocketClient.getInstance().send(
                        Actions.REGISTER,
                        requestData,
                        RegisterResponseData.class
                );

                // 4. Thành công -> Về lại luồng chính để báo cáo và chuyển trang
                Platform.runLater(() -> {
                    // Dùng thông báo từ Server trả về (nếu có), không thì dùng mặc định
                    String successMsg = response.message != null ? response.message : "Đăng ký tài khoản thành công!";
                    AlertUtil.showWarning("Thành công", successMsg); // Tạm dùng Warning hoặc tạo hàm showInfo trong AlertUtil

                    // Chuyển thẳng về trang Login
                    try {
                        ViewLoader.load(event, "login.fxml", "Đăng nhập");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (RuntimeException e) {
                // 5. Bắt lỗi từ Server (Trùng username, lỗi DB...)
                Platform.runLater(() -> AlertUtil.showError("Đăng ký thất bại", e.getMessage()));
            } finally {
                // 6. Luôn mở khóa nút bấm
                Platform.runLater(() -> {
                    btnRegister.setDisable(false);
                    btnRegister.setText("TẠO TÀI KHOẢN");
                });
            }
        }).start();
    }

    /**
     * Xử lý khi nhấn "Đã có tài khoản? Đăng nhập"
     */
    @FXML
    private void goBackToLogin(ActionEvent event) {
        try {
            ViewLoader.load(event, "login.fxml", "Đăng nhập");
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi hệ thống", "Không thể tải màn hình đăng nhập.");
        }
    }
}