package com.auction.client.controller;

import com.auction.client.util.ViewLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent; // Cần thiết cho sự kiện click vào Label

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    /**
     * Xử lý khi người dùng nhấn nút "ĐĂNG NHẬP"
     */
    @FXML
    private void handleLogin() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        // Kiểm tra nhập liệu
        if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        // Giả lập đóng gói dữ liệu JSON (theo Protocol)
        String requestId = UUID.randomUUID().toString();
        Map<String, String> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);

        System.out.println("==== GỬI REQUEST ĐĂNG NHẬP ====");
        System.out.println("Action: LOGIN");
        System.out.println("ID: " + requestId);
        System.out.println("Data: " + data);
        System.out.println("===============================");

        // Sau này sẽ gọi SocketClient.send(...) ở đây
    }

    /**
     * Xử lý khi click vào dòng chữ "Chưa có tài khoản? Đăng ký ngay"
     * Sử dụng MouseEvent vì Label trong FXML dùng onMouseClicked
     */
    @FXML
    private void goToRegister(MouseEvent event) {
        try {
            System.out.println("Đang chuyển sang màn hình Đăng ký...");
            // Gọi ViewLoader để đổi sang register.fxml
            ViewLoader.load(event, "register.fxml", "Đăng ký tài khoản");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi hệ thống", "Không thể tải màn hình đăng ký. Vui lòng kiểm tra file register.fxml!");
        }
    }

    /**
     * Hàm hiển thị thông báo nhanh
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}