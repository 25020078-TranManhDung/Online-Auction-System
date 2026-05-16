package com.auction.client.util;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.shared.network.protocol.Actions;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;

public class ProfileHeaderUtil {

    // Lưu trữ popup dùng chung để không phải tạo lại nhiều lần, giúp App mượt hơn
    private static Popup sharedPopup;

    public static void bindHeaderProfile(HBox headerUserArea, ImageView imgAvatar) {
        if (imgAvatar == null || headerUserArea == null) return;

        // 1. Nạp ảnh ban đầu từ Database
        refreshHeaderAvatar(imgAvatar);

        // 2. Gắn hiệu ứng Hover cho toàn bộ khung HBox
        headerUserArea.setOnMouseEntered(e -> {
            headerUserArea.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 12; -fx-background-color: rgba(155,89,182,0.12);");
        });
        headerUserArea.setOnMouseExited(e -> {
            headerUserArea.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 12; -fx-background-color: transparent;");
        });

        // 3. Gắn sự kiện Click vào TOÀN BỘ khung HBox (giúp dễ bấm hơn rất nhiều)
        headerUserArea.setOnMouseClicked(event -> {
            // Xóa popup cũ để cập nhật thông tin mới nhất (tránh lỗi cache)
            sharedPopup = buildSharedPopup(imgAvatar);

            // Tính toán vị trí hiển thị chuẩn xác
            double anchorX = headerUserArea.localToScreen(headerUserArea.getBoundsInLocal()).getMinX() - 40;
            double anchorY = headerUserArea.localToScreen(headerUserArea.getBoundsInLocal()).getMaxY() + 8;
            sharedPopup.show(headerUserArea.getScene().getWindow(), anchorX, anchorY);
        });
    }

    public static void refreshHeaderAvatar(ImageView imageView) {
        UserSession s = UserSession.getInstance();
        Image img;
        if (s.getAvatarBase64() != null && !s.getAvatarBase64().isBlank()) {
            byte[] imageBytes = ImageUtil.decodeToBytes(s.getAvatarBase64());
            img = new Image(new ByteArrayInputStream(imageBytes));
        } else {
            img = new Image(ProfileHeaderUtil.class.getResourceAsStream("/com/auction/client/images/default_avatar.png"));
        }
        applyCenterCrop(imageView, img);
    }

    private static Popup buildSharedPopup(ImageView headerImageView) {
        UserSession session = UserSession.getInstance();
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        StackPane wrapper = new StackPane();
        wrapper.setPadding(new Insets(8));

        VBox card = new VBox(0);
        card.setPrefWidth(300);
        card.setStyle("-fx-background-color: rgba(35, 10, 60, 0.93); -fx-background-radius: 18; -fx-border-color: rgba(155, 89, 182, 0.40); -fx-border-width: 1.5; -fx-border-radius: 18;");

        DropShadow ds = new DropShadow();
        ds.setColor(Color.rgb(67, 20, 118, 0.55)); ds.setRadius(28); ds.setOffsetY(8);
        card.setEffect(ds);

        // TOP SECTION
        VBox topSection = new VBox(6);
        topSection.setAlignment(Pos.CENTER);
        topSection.setPadding(new Insets(24, 20, 18, 20));

        ImageView popupAvatar = new ImageView();
        popupAvatar.setFitWidth(64); popupAvatar.setFitHeight(64); popupAvatar.setPreserveRatio(true);
        popupAvatar.setClip(new Circle(32, 32, 32));

        // Load ảnh cho avatar to trong popup
        if (session.getAvatarBase64() != null && !session.getAvatarBase64().isBlank()) {
            byte[] imageBytes = ImageUtil.decodeToBytes(session.getAvatarBase64());
            applyCenterCrop(popupAvatar, new Image(new ByteArrayInputStream(imageBytes)));
        } else {
            try {
                applyCenterCrop(popupAvatar, new Image(ProfileHeaderUtil.class.getResourceAsStream("/com/auction/client/images/default_avatar.png")));
            } catch (Exception ignored) {}
        }

        DropShadow avatarGlow = new DropShadow();
        avatarGlow.setColor(Color.rgb(155, 89, 182, 0.70));
        avatarGlow.setRadius(14);
        popupAvatar.setEffect(avatarGlow);

        StackPane avatarContainer = new StackPane();
        avatarContainer.setMaxSize(64, 64);

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-background-radius: 32;");
        overlay.setOpacity(0);
        Label editIcon = new Label("📷");
        editIcon.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");
        overlay.getChildren().add(editIcon);

        avatarContainer.getChildren().addAll(popupAvatar, overlay);
        avatarContainer.setCursor(Cursor.HAND);

        // Hiệu ứng Hover đổi viền Cyan
        avatarContainer.setOnMouseEntered(e -> {
            overlay.setOpacity(1);
            popupAvatar.setEffect(new DropShadow(javafx.scene.effect.BlurType.THREE_PASS_BOX, Color.web("#00ffff"), 12, 0.3, 0, 0));
        });
        avatarContainer.setOnMouseExited(e -> {
            overlay.setOpacity(0);
            popupAvatar.setEffect(avatarGlow);
        });

        // Bấm vào ảnh to thì hiện ContextMenu đổi ảnh
        avatarContainer.setOnMouseClicked(e -> {
            handleAvatarClick(popupAvatar, headerImageView);
        });

        Label lblFullName = new Label(nullSafe(session.getFullName(), session.getUsername()));
        lblFullName.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #f0e6ff; -fx-padding: 8 0 2 0;");
        Label lblAtUsername = new Label("@" + nullSafe(session.getUsername(), "—"));
        lblAtUsername.setStyle("-fx-font-size: 13px; -fx-text-fill: #9b59b6;");

        topSection.getChildren().addAll(avatarContainer, lblFullName, lblAtUsername);

        // INFO SECTION
        VBox infoSection = new VBox(10);
        infoSection.setPadding(new Insets(14, 24, 14, 24));
        infoSection.getChildren().addAll(
                infoRow("👤", "User ID", nullSafe(session.getUserId(), "—")),
                infoRow("✉", "Email", nullSafe(session.getEmail(), "Chưa cập nhật")),
                infoRow("🏷", "Vai trò", nullSafe(session.getRole(), "—"))
        );

        // ACTION SECTION
        VBox actionSection = new VBox(10);
        actionSection.setAlignment(Pos.CENTER);
        actionSection.setPadding(new Insets(14, 20, 20, 20));

        Button btnChangePassword = new Button("🔐  Đổi mật khẩu");
        btnChangePassword.setMaxWidth(Double.MAX_VALUE);
        btnChangePassword.setStyle("-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;");
        btnChangePassword.setOnAction(e -> {
            popup.hide();
            handleChangePassword(headerImageView.getScene().getWindow());
        });

        actionSection.getChildren().add(btnChangePassword);
        card.getChildren().addAll(topSection, styledDivider(), infoSection, styledDivider(), actionSection);
        wrapper.getChildren().add(card);
        popup.getContent().add(wrapper);

        return popup;
    }

    private static HBox infoRow(String icon, String labelText, String value) {
        Label iconLbl = new Label(icon); iconLbl.setStyle("-fx-font-size: 14px;"); iconLbl.setMinWidth(22);
        Label keyLbl = new Label(labelText + ":"); keyLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #9b59b6; -fx-min-width: 70;");
        Label valLbl = new Label(value); valLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #dcd0ff; -fx-font-weight: bold;"); valLbl.setWrapText(true);
        HBox row = new HBox(8, iconLbl, keyLbl, valLbl); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Separator styledDivider() {
        Separator sep = new Separator(); sep.setStyle("-fx-background-color: rgba(155,89,182,0.30); -fx-padding: 0 20;");
        VBox.setMargin(sep, new Insets(0, 20, 0, 20)); return sep;
    }

    private static String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static void handleChangePassword(javafx.stage.Window owner) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(ProfileHeaderUtil.class.getResource("/com/auction/client/fxml/change-password.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage modalStage = new Stage();
            modalStage.setTitle("Đổi mật khẩu");
            modalStage.setScene(new Scene(root));
            modalStage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) modalStage.initOwner(owner);
            modalStage.setResizable(false);
            modalStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở màn hình đổi mật khẩu: " + e.getMessage());
        }
    }

    private static void handleAvatarClick(ImageView popupAvatar, ImageView headerAvatar) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        MenuItem uploadItem = new MenuItem("📷  Tải ảnh lên...");
        uploadItem.setOnAction(e -> {
            String base64 = ImageUtil.pickAndEncodeAvatar(null);
            if (base64 == null) return;
            sendAvatarUpdate(base64, popupAvatar, headerAvatar);
        });
        MenuItem removeItem = new MenuItem("🗑  Xóa ảnh");
        removeItem.setOnAction(e -> sendAvatarUpdate(null, popupAvatar, headerAvatar));
        menu.getItems().addAll(uploadItem, removeItem);
        menu.show(popupAvatar, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private static void sendAvatarUpdate(String base64, ImageView popupAvatar, ImageView headerAvatar) {
        UserSession session = UserSession.getInstance();
        if (session.getUserId() == null) return;
        com.auction.shared.dto.request.UpdateAvatarRequest req =
                new com.auction.shared.dto.request.UpdateAvatarRequest(session.getUserId(), base64);
        new Thread(() -> {
            try {
                SocketClient.getInstance().send(Actions.UPDATE_AVATAR, req, String.class);
                Platform.runLater(() -> {
                    session.setAvatarBase64(base64);
                    Image newImg;
                    if (base64 == null) {
                        newImg = new Image(ProfileHeaderUtil.class.getResourceAsStream("/com/auction/client/images/default_avatar.png"));
                    } else {
                        byte[] imageBytes = ImageUtil.decodeToBytes(base64);
                        newImg = new Image(new ByteArrayInputStream(imageBytes));
                    }
                    applyCenterCrop(popupAvatar, newImg);
                    if (headerAvatar != null) applyCenterCrop(headerAvatar, newImg);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi", "Không thể cập nhật ảnh: " + ex.getMessage()));
            }
        }).start();
    }

    private static void applyCenterCrop(ImageView imageView, Image img) {
        imageView.setImage(img);
        double w = img.getWidth(); double h = img.getHeight();
        if (w > 0 && h > 0) {
            double size = Math.min(w, h);
            double x = (w - size) / 2.0; double y = (h - size) / 2.0;
            imageView.setViewport(new javafx.geometry.Rectangle2D(x, y, size, size));
        }
    }
}