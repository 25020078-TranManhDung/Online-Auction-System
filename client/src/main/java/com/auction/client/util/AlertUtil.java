package com.auction.client.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

public class AlertUtil {

    public static void showInfo(String title, String content) {
        javafx.application.Platform.runLater(() ->
                showCustomAlert("✅ " + title, content, "#27ae60", false)
        );
    }

    public static void showError(String title, String content) {
        javafx.application.Platform.runLater(() ->
                showCustomAlert("❌ " + title, content, "#e74c3c", false)
        );
    }

    public static void showWarning(String title, String content) {
        javafx.application.Platform.runLater(() ->
                showCustomAlert("⚠️ " + title, content, "#f39c12", false)
        );
    }

    /**
     * Hiển thị dialog xác nhận (OK / Cancel).
     * Được thiết kế an toàn để gọi từ cả luồng Background Thread và UI Thread.
     */
    public static boolean showConfirm(String title, String content) {
        // Nếu đang ở luồng giao diện (UI Thread), chạy trực tiếp
        if (javafx.application.Platform.isFxApplicationThread()) {
            return showCustomAlert("❓ " + title, content, "#3498db", true);
        } else {
            // Nếu gọi từ luồng nền, phải chờ UI vẽ xong và trả kết quả về
            AtomicBoolean result = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            javafx.application.Platform.runLater(() -> {
                result.set(showCustomAlert("❓ " + title, content, "#3498db", true));
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException ignored) {}

            return result.get();
        }
    }

    /**
     * Hàm lõi tạo ra một chiếc Alert trong suốt, bo góc, phong cách Glassmorphism
     */
    private static boolean showCustomAlert(String titleText, String contentText, String titleColor, boolean isConfirm) {
        Stage stage = new Stage();
        // XÓA BỎ viền cửa sổ thô kệch của hệ điều hành
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setAlwaysOnTop(true);

        // --- Container chính ---
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(25, 30, 25, 30));

        // Style "Glass-box" cực sang: nền tím tối mờ, bo góc, viền tím nhạt
        root.setStyle(
                "-fx-background-color: rgba(30, 10, 50, 0.95); " +
                        "-fx-background-radius: 16; " +
                        "-fx-border-color: rgba(155, 89, 182, 0.5); " +
                        "-fx-border-width: 1.5; " +
                        "-fx-border-radius: 16;"
        );

        // Hiệu ứng đổ bóng nổi bật
        DropShadow ds = new DropShadow();
        ds.setColor(Color.rgb(0, 0, 0, 0.6));
        ds.setRadius(20);
        ds.setOffsetY(5);
        root.setEffect(ds);

        // --- Tiêu đề ---
        Label lblTitle = new Label(titleText);
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + titleColor + ";");

        // --- Nội dung ---
        Label lblContent = new Label(contentText);
        lblContent.setWrapText(true);
        lblContent.setMaxWidth(350);
        lblContent.setStyle("-fx-font-size: 14px; -fx-text-fill: #ecf0f1; -fx-text-alignment: center; -fx-alignment: center; -fx-line-spacing: 4px;");

        // --- Vùng Nút bấm ---
        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        AtomicBoolean confirmResult = new AtomicBoolean(false);

        // Nút OK (Đồng ý)
        Button btnOk = new Button("Đồng ý");
        styleButton(btnOk, "#8e44ad", "#9b59b6");
        btnOk.setOnAction(e -> {
            confirmResult.set(true);
            stage.close();
        });

        // Nút Hủy (Chỉ hiện nếu là showConfirm)
        if (isConfirm) {
            Button btnCancel = new Button("Hủy bỏ");
            styleButton(btnCancel, "#7f8c8d", "#95a5a6");
            btnCancel.setOnAction(e -> {
                confirmResult.set(false);
                stage.close();
            });
            btnBox.getChildren().addAll(btnCancel, btnOk);
        } else {
            btnBox.getChildren().add(btnOk);
        }

        root.getChildren().addAll(lblTitle, lblContent, btnBox);

        // --- Thiết lập Scene trong suốt ---
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        // Căn giữa màn hình và không cho phép kéo dãn
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.showAndWait();

        return confirmResult.get();
    }

    /**
     * Hàm Helper tạo hiệu ứng hover mượt mà cho các nút bấm trong Alert
     */
    private static void styleButton(Button btn, String baseColor, String hoverColor) {
        String baseStyle = "-fx-background-color: " + baseColor + "; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 24; -fx-background-radius: 8; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: " + hoverColor + "; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 24; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, " + hoverColor + "80, 8, 0, 0, 0);";

        btn.setStyle(baseStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(baseStyle));
    }
}