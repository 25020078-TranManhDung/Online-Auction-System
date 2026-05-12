package com.auction.client.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AlertUtil {

    public static void showError(String title, String content) {
        showAlert(AlertType.ERROR, title, "Lỗi", content);
    }

    public static void showInfo(String title, String content) {
        showAlert(AlertType.INFORMATION, title, "Thông báo", content);
    }

    public static void showWarning(String title, String content) {
        showAlert(AlertType.WARNING, title, "Cảnh báo", content);
    }

    /**
     * Hiển thị dialog xác nhận (OK / Cancel).
     * Phải gọi trên FX Application Thread; trả về true nếu người dùng nhấn OK.
     */
    public static boolean showConfirm(String title, String content) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        try {
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            if (stage != null) alert.initModality(Modality.APPLICATION_MODAL);
        } catch (Exception ignored) {}
        java.util.Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void showAlert(AlertType type, String title, String header, String content) {
        // Luôn chạy trên JavaFX Application Thread
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(type);
                alert.setTitle(title);
                alert.setHeaderText(header);
                alert.setContentText(content);

                // Đặt modal owner nếu có Stage hiện tại, tránh NPE
                try {
                    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
                    if (stage != null) {
                        stage.setAlwaysOnTop(true);
                        alert.initModality(Modality.APPLICATION_MODAL);
                    }
                } catch (Exception ignored) {
                    // Nếu chưa có scene, bỏ qua việc setAlwaysOnTop
                }

                alert.showAndWait();
            } catch (Exception e) {
                // Nếu Alert gặp lỗi (hiếm), in ra console để debug
                System.err.println("Không thể hiển thị Alert: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}