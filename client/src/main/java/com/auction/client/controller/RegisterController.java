package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.HashMap;
import java.util.Map;

public class RegisterController {

    // ==== Các field phải khớp với register.fxml ====
    @FXML private TextField txtFullname;
    @FXML private TextField txtUsername;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;

    @FXML private RadioButton radioBidder;
    @FXML private RadioButton radioSeller;
    @FXML private ToggleGroup roleGroup;

    @FXML private Button btnRegister;
    @FXML private Label lblError;

    @FXML
    public void initialize() {
        if (lblError != null) lblError.setVisible(false);

        // Phím tắt Enter: điều hướng tuần tự qua từng field
        txtFullname.setOnAction(event -> txtUsername.requestFocus());
        txtUsername.setOnAction(event -> txtEmail.requestFocus());
        txtEmail.setOnAction(event -> txtPassword.requestFocus());
        txtPassword.setOnAction(event -> txtConfirmPassword.requestFocus());

        // Enter ở field cuối cùng → kích hoạt nút Tạo tài khoản
        txtConfirmPassword.setOnAction(event -> btnRegister.fire());
    }

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
     * Giữ nguyên logic gọi SocketClient nhưng đảm bảo UI không NPE và hiển thị thông báo.
     */
    @FXML
    private void handleRegister(ActionEvent event) {
        // Kiểm tra các control đã được inject
        if (txtFullname == null || txtUsername == null || txtPassword == null || txtConfirmPassword == null || btnRegister == null) {
            AlertUtil.showError("Lỗi", "Form đăng ký chưa được khởi tạo đúng. Vui lòng kiểm tra FXML.");
            return;
        }

        String fullname = safeGetText(txtFullname);
        String username = safeGetText(txtUsername);
        String email = safeGetText(txtEmail);
        String password = safeGetText(txtPassword);
        String confirmPassword = safeGetText(txtConfirmPassword);

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
                requestData.put("fullname", fullname);
                requestData.put("username", username);
                requestData.put("password", password);

                // Nếu user nhập email thì dùng email đó, nếu không thì tạo giả (như trước)
                if (email == null || email.isEmpty()) {
                    requestData.put("email", username + "@example.com");
                } else {
                    requestData.put("email", email);
                }

                // Lấy role từ radio button (mặc định BIDDER)
                String role = "BIDDER";
                try {
                    if (radioSeller != null && radioSeller.isSelected()) role = "SELLER";
                    else if (radioBidder != null && radioBidder.isSelected()) role = "BIDDER";
                } catch (Exception ignored) { /* fallback to BIDDER */ }
                requestData.put("role", role);

                // Gửi Request đồng bộ
                RegisterResponseData response = SocketClient.getInstance().send(
                    Actions.REGISTER,
                    requestData,
                    RegisterResponseData.class
                );

                // 4. Thành công -> Về lại luồng chính để báo cáo và chuyển trang
                Platform.runLater(() -> {
                    String successMsg = (response != null && response.message != null) ? response.message : "Đăng ký tài khoản thành công!";
                    AlertUtil.showInfo("Thành công", successMsg);

                    // Chuyển thẳng về trang Login
                    try {
                        ViewLoader.load(event, "login.fxml", "Đăng nhập");
                    } catch (Exception e) {
                        e.printStackTrace();
                        AlertUtil.showError("Lỗi", "Không thể chuyển về màn hình đăng nhập.");
                    }
                });

            } catch (RuntimeException e) {
                // 5. Bắt lỗi từ Server (Trùng username, lỗi DB...)
                Platform.runLater(() -> {
                    String msg = e.getMessage() == null ? "Đăng ký thất bại" : e.getMessage();
                    AlertUtil.showError("Đăng ký thất bại", msg);
                });
            } catch (Exception e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi", "Đã xảy ra lỗi: " + e.getMessage()));
                e.printStackTrace();
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
     * Quay về màn hình đăng nhập
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

    // Helper an toàn lấy text (tránh NPE)
    private String safeGetText(TextField tf) {
        try {
            return tf == null || tf.getText() == null ? "" : tf.getText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeGetText(PasswordField pf) {
        try {
            return pf == null || pf.getText() == null ? "" : pf.getText().trim();
        } catch (Exception e) {
            return "";
        }
    }
}