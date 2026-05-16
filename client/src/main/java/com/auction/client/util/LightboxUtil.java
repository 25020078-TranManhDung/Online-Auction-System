package com.auction.client.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;

public class LightboxUtil {

    private static int currentIndex = 0;

    // Giới hạn zoom
    private static final double MIN_SCALE = 1.0;
    private static final double MAX_SCALE = 5.0;

    public static void show(List<Image> images, int startIndex) {
        if (images == null || images.isEmpty()) return;
        currentIndex = startIndex;

        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.85);");

        ImageView imageView = new ImageView(images.get(currentIndex));
        imageView.setPreserveRatio(true);
        imageView.setEffect(new DropShadow(20, Color.BLACK));

        // Scale ảnh ban đầu không bị tràn màn hình
        javafx.geometry.Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        imageView.setFitWidth(bounds.getWidth() * 0.85);
        imageView.setFitHeight(bounds.getHeight() * 0.85);

        // 🌟 BỔ SUNG CƠ CHẾ ZOOM (LĂN CHUỘT)
        imageView.setOnScroll((ScrollEvent event) -> {
            double zoomFactor = 1.05;
            if (event.getDeltaY() < 0) {
                zoomFactor = 1 / zoomFactor; // Lăn xuống -> Thu nhỏ
            }

            double newScale = imageView.getScaleX() * zoomFactor;

            // Giới hạn không cho thu nhỏ quá mức gốc hoặc phóng to quá đà
            if (newScale < MIN_SCALE) newScale = MIN_SCALE;
            if (newScale > MAX_SCALE) newScale = MAX_SCALE;

            imageView.setScaleX(newScale);
            imageView.setScaleY(newScale);

            // Nếu thu về mức gốc thì reset vị trí di chuyển
            if (newScale == MIN_SCALE) {
                imageView.setTranslateX(0);
                imageView.setTranslateY(0);
            }
            event.consume();
        });

        // 🌟 BỔ SUNG CƠ CHẾ KÉO THẢ (PANNING KHI ĐANG ZOOM)
        final double[] dragStart = new double[2];
        imageView.setOnMousePressed(e -> {
            dragStart[0] = e.getSceneX() - imageView.getTranslateX();
            dragStart[1] = e.getSceneY() - imageView.getTranslateY();
            imageView.setCursor(javafx.scene.Cursor.CLOSED_HAND);
        });

        imageView.setOnMouseDragged(e -> {
            // Chỉ cho phép di chuyển ảnh nếu đang phóng to
            if (imageView.getScaleX() > MIN_SCALE) {
                imageView.setTranslateX(e.getSceneX() - dragStart[0]);
                imageView.setTranslateY(e.getSceneY() - dragStart[1]);
            }
        });

        imageView.setOnMouseReleased(e -> {
            imageView.setCursor(javafx.scene.Cursor.DEFAULT);
        });

        // Nút Đóng (Góc trên cùng bên phải)
        Button btnClose = createRoundButton("✕");
        StackPane.setAlignment(btnClose, Pos.TOP_RIGHT);
        StackPane.setMargin(btnClose, new Insets(30));
        btnClose.setOnAction(e -> stage.close());

        // Bộ đếm trang (Góc dưới cùng)
        Label lblCounter = new Label();
        lblCounter.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: rgba(0,0,0,0.5); -fx-padding: 4 12; -fx-background-radius: 12;");
        StackPane.setAlignment(lblCounter, Pos.BOTTOM_CENTER);
        StackPane.setMargin(lblCounter, new Insets(40));

        // Nút điều hướng (Trái / Phải)
        Button btnPrev = createRoundButton("❮");
        Button btnNext = createRoundButton("❯");
        HBox navBox = new HBox(bounds.getWidth() * 0.7, btnPrev, btnNext);
        navBox.setAlignment(Pos.CENTER);
        navBox.setPickOnBounds(false);

        Runnable updateUI = () -> {
            // Reset zoom và vị trí trước khi chuyển ảnh mới
            imageView.setScaleX(1.0);
            imageView.setScaleY(1.0);
            imageView.setTranslateX(0);
            imageView.setTranslateY(0);

            imageView.setImage(images.get(currentIndex));
            lblCounter.setText((currentIndex + 1) + " / " + images.size());
            btnPrev.setVisible(currentIndex > 0);
            btnNext.setVisible(currentIndex < images.size() - 1);
            navBox.setVisible(images.size() > 1);
            lblCounter.setVisible(images.size() > 1);
        };

        btnPrev.setOnAction(e -> { if (currentIndex > 0) { currentIndex--; updateUI.run(); }});
        btnNext.setOnAction(e -> { if (currentIndex < images.size() - 1) { currentIndex++; updateUI.run(); }});

        // Click ra ngoài thì đóng (chú ý chỉ đóng khi click vào viền đen, không đóng khi click vào ảnh)
        root.setOnMouseClicked(e -> {
            if (e.getTarget() == root || e.getTarget() == navBox) stage.close();
        });

        updateUI.run();
        root.getChildren().addAll(imageView, navBox, btnClose, lblCounter);

        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
            else if (e.getCode() == KeyCode.LEFT && currentIndex > 0) { currentIndex--; updateUI.run(); }
            else if (e.getCode() == KeyCode.RIGHT && currentIndex < images.size() - 1) { currentIndex++; updateUI.run(); }
        });

        stage.setScene(scene);

        root.setOpacity(0);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), root);
        ft.setToValue(1.0);
        ft.play();

        stage.centerOnScreen();
        stage.show();
    }

    private static Button createRoundButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15); -fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-background-radius: 50; -fx-cursor: hand; -fx-padding: 8 16;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-background-radius: 50; -fx-cursor: hand; -fx-padding: 8 16;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15); -fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-background-radius: 50; -fx-cursor: hand; -fx-padding: 8 16;"));
        return btn;
    }
}
