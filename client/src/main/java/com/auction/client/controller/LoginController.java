package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.Map;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private Label lblError; // Bổ sung để đồng bộ FXML

    @FXML
    public void initialize() {
        if (lblError != null) lblError.setVisible(false);

        // Phím tắt Enter: Username → chuyển focus sang Password
        txtUsername.setOnAction(event -> txtPassword.requestFocus());

        // Phím tắt Enter: Password → kích hoạt nút Đăng nhập
        txtPassword.setOnAction(event -> btnLogin.fire());
    }

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
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        clearError(); // Ẩn lỗi nếu có
        btnLogin.setDisable(true);
        btnLogin.setText("Đang xử lý...");

        new Thread(() -> {
            try {
                Map<String, String> requestData = new HashMap<>();
                requestData.put("username", username);
                requestData.put("password", password);

                LoginResponseData response = SocketClient.getInstance().send(
                    Actions.LOGIN,
                    requestData,
                    LoginResponseData.class
                );

                // Đăng nhập thành công
                Platform.runLater(() -> {
                    UserSession.getInstance().initSession(
                        response.userId,
                        response.username,
                        response.token,
                        response.role,
                        response.expiresAt
                    );

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
                        showError("Không thể tải màn hình chính!");
                    }
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> showError(e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    btnLogin.setText("ĐĂNG NHẬP");
                });
            }
        }).start();
    }

    /**
     * Hiển thị lỗi trên label trong giao diện thay vì chỉ dùng Alert.
     */
    private void showError(String msg) {
        if (lblError != null) {
            lblError.setText(msg);
            lblError.setVisible(true);
        } else {
            AlertUtil.showError("Đăng nhập thất bại", msg);
        }
    }
    private void clearError() {
        if (lblError != null) {
            lblError.setText("");
            lblError.setVisible(false);
        }
    }

    /**
     * Xử lý khi click vào dòng chữ chuyển sang Đăng ký
     */
    @FXML
    private void goToRegister(ActionEvent event) {
        try {
            ViewLoader.load(event, "register.fxml", "Đăng ký tài khoản");
        } catch (Exception e) {
            e.printStackTrace();
            // Lỗi load giao diện thì vẫn show popup.
            AlertUtil.showError("Lỗi hệ thống", "Không thể tải màn hình đăng ký.");
        }
    }
}