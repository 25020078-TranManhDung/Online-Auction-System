package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions; // Đảm bảo import đúng đường dẫn file Actions của bạn

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.Map;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;

    /**
     * DTO: Hứng dữ liệu trả về từ Server cho action LOGIN.
     * Khớp với trường "data" của ServerResponse.
     */
    public static class LoginResponseData {
        public String token;
        public String userId;
        public String username;
        public String role;
        public String expiresAt;
    }

    /**
     * Xử lý khi người dùng nhấn nút "ĐĂNG NHẬP"
     */
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        // 1. Kiểm tra nhập liệu cơ bản
        if (username.isEmpty() || password.isEmpty()) {
            AlertUtil.showWarning("Thiếu thông tin", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        // 2. Khóa nút bấm, tránh click nhiều lần
        btnLogin.setDisable(true);
        btnLogin.setText("Đang xử lý...");

        // 3. Mở luồng phụ (Background Thread) để gọi mạng, không làm đơ giao diện
        new Thread(() -> {
            try {
                // Chuẩn bị dữ liệu gửi đi (Request Data)
                Map<String, String> requestData = new HashMap<>();
                requestData.put("username", username);
                requestData.put("password", password);

                // Gửi Request đồng bộ (Chờ tối đa 10s theo code SocketClient)
                LoginResponseData response = SocketClient.getInstance().send(
                        Actions.LOGIN,
                        requestData,
                        LoginResponseData.class
                );

                // 4. Nếu thành công, quay lại luồng chính (UI Thread) để cập nhật giao diện
                Platform.runLater(() -> {
                    // Lưu phiên đăng nhập
                    UserSession.getInstance().initSession(
                            response.userId,
                            response.username,
                            response.token,
                            response.role,
                            response.expiresAt
                    );

                    // Điều hướng theo Role
                    try {
                        if ("SELLER".equals(response.role)) {
                            ViewLoader.load(event, "seller-dashboard.fxml", "Bảng điều khiển Người bán");
                        } else if ("ADMIN".equals(response.role)) {
                            ViewLoader.load(event, "admin-dashboard.fxml", "Quản trị hệ thống");
                        } else {
                            ViewLoader.load(event, "auction-list.fxml", "Sàn đấu giá");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        AlertUtil.showError("Lỗi giao diện", "Không thể tải màn hình chính!");
                    }
                });

            } catch (RuntimeException e) {
                // 5. Bắt lỗi từ Server (Sai pass) hoặc Timeout từ SocketClient
                Platform.runLater(() -> AlertUtil.showError("Đăng nhập thất bại", e.getMessage()));
            } finally {
                // 6. Luôn mở khóa nút bấm dù thành công hay thất bại
                Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    btnLogin.setText("ĐĂNG NHẬP");
                });
            }
        }).start();
    }

    /**
     * Xử lý khi click vào dòng chữ chuyển sang Đăng ký
     */
    @FXML
    private void goToRegister(MouseEvent event) {
        try {
            ViewLoader.load(event, "register.fxml", "Đăng ký tài khoản");
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi hệ thống", "Không thể tải màn hình đăng ký.");
        }
    }
}