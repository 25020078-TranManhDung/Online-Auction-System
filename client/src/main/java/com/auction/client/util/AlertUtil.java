package com.auction.client.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

public class AlertUtil {

    /**
     * Hiển thị thông báo lỗi (Error)
     */
    public static void showError(String title, String content) {
        showAlert(AlertType.ERROR, title, "Lỗi", content);
    }

    /**
     * Hiển thị thông báo thành công (Information)
     */
    public static void showInfo(String title, String content) {
        showAlert(AlertType.INFORMATION, title, "Thông báo", content);
    }

    /**
     * Hiển thị cảnh báo (Warning)
     */
    public static void showWarning(String title, String content) {
        showAlert(AlertType.WARNING, title, "Cảnh báo", content);
    }

    // Hàm dùng chung để tạo Alert
    private static void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Làm cho cửa sổ thông báo luôn nổi lên trên cùng
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        alert.showAndWait();
    }
}