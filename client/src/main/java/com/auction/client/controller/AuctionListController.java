package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.enums.AuctionStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Predicate;
import java.util.HashSet;
import java.util.Set;
import java.io.*;
import java.nio.file.*;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import com.google.gson.Gson;

public class AuctionListController {

    // ─── Header ───────────────────────────────────────────────
    @FXML private HBox      headerUserArea;
    @FXML private ImageView imgAvatar;
    @FXML private Label     lblUser;
    @FXML private Label     lblRole;
    @FXML private Button    btnLogout;
    @FXML private Button    btnTheme;
    @FXML private Label     lblWalletBalance;

    // ─── Toolbar / basic filters ──────────────────────────────
    @FXML private TextField        txtSearch;
    @FXML private ComboBox<String> cboFilter;
    @FXML private Button           btnRefresh;
    @FXML private Label            lblAuctionCount;
    @FXML private Label            lblMainTitle;

    // ─── Advanced filter controls ─────────────────────────────
    @FXML private TextField        txtPriceMin;
    @FXML private TextField        txtPriceMax;
    @FXML private TextField        txtMinMinutes;
    @FXML private ComboBox<String> cboHasImage;
    @FXML private Button           btnApplyFilter;
    @FXML private Button           btnClearFilter;
    @FXML private Label            lblFilterStatus;

    // ─── Card Grid (thay thế TableView) ──────────────────────
    @FXML private FlowPane  cardGrid;
    @FXML private ScrollPane cardScrollPane;
    @FXML private VBox      emptyBox;
    @FXML private Label     lblMessage;

    // ─── Data & state ─────────────────────────────────────────
    private final ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();
    private FilteredList<JsonObject> filteredAuctions;
    private String   currentCategory = "ALL";

    private double  filterPriceMin    = -1;
    private double  filterPriceMax    = -1;
    private int     filterMinMinutes  = -1;
    private String  filterHasImage    = "Tất cả";

    private Popup profilePopup;

    // ─── Wishlist (Bookmark) ──────────────────────────────────
    private final Set<String> wishlistIds = new HashSet<>();
    private static final String WISHLIST_FILE =
        System.getProperty("user.home") + File.separator + ".auction_wishlist.json";
    /** Khi đang xem wishlist, chỉ hiện card được yêu thích */
    private boolean showingWishlistOnly = false;
    @FXML private Button btnWishlistFilter;

    // ─── Shared countdown timer cho tất cả cards ──────────────
    /** Mỗi phần tử: long[0] = secondsRemaining, Label = hiển thị */
    private static class CardTimer {
        final long[]  secondsRef;
        final Label   timerLabel;
        final String  status;
        CardTimer(long[] ref, Label lbl, String st) {
            secondsRef = ref; timerLabel = lbl; status = st;
        }
    }
    private final List<CardTimer> cardTimers = new ArrayList<>();
    private Timer sharedTimer;

    // ─── Card width — tính động theo chiều rộng FlowPane ─────────
    /** Số cột cố định. Thay đổi giá trị này nếu muốn 3 hoặc 5 cột. */
    private static final int CARD_COLS = 4;
    /** hgap phải khớp với giá trị trong FXML */
    private static final int CARD_HGAP = 18;
    /** Giá trị mặc định (tính sẵn cho cửa sổ 1400px), được cập nhật lần đầu khi layout xong */
    private int computedCardW = 256;
    private boolean cardWidthInitialized = false;

    // ==========================================================
    //  KHỞI TẠO
    // ==========================================================

    @FXML
    public void initialize() {
        if (btnTheme != null)
            btnTheme.setText(com.auction.client.util.ThemeManager.getInstance().getToggleIcon());

        loadUserInfo();

        filteredAuctions = new FilteredList<>(auctionList, p -> true);

        // ComboBox trạng thái
        if (cboFilter != null) {
            cboFilter.getItems().clear();
            ObservableList<String> statusList = FXCollections.observableArrayList("Tất cả");
            for (AuctionStatus s : AuctionStatus.values()) statusList.add(s.name());
            cboFilter.setItems(statusList);
            cboFilter.setValue("Tất cả");
            cboFilter.setOnAction(e -> applyAuctionFilter());
        }

        // ComboBox có ảnh
        if (cboHasImage != null) {
            cboHasImage.setItems(FXCollections.observableArrayList("Tất cả", "Có ảnh", "Không có ảnh"));
            cboHasImage.setValue("Tất cả");
        }

        // Search realtime
        if (txtSearch != null)
            txtSearch.textProperty().addListener((obs, o, n) -> applyAuctionFilter());

        // Hover header profile
        if (headerUserArea != null) {
            headerUserArea.setOnMouseEntered(e ->
                headerUserArea.setStyle(
                    "-fx-cursor: hand; -fx-padding: 4 6; " +
                        "-fx-background-radius: 14; " +
                        "-fx-background-color: rgba(155,89,182,0.12);"));
            headerUserArea.setOnMouseExited(e ->
                headerUserArea.setStyle(
                    "-fx-cursor: hand; -fx-padding: 4 6; " +
                        "-fx-background-radius: 14; " +
                        "-fx-background-color: transparent;"));
        }

        // ─── Listener: tính lại CARD_W khi FlowPane được layout lần đầu ───
        // Fixes: 4 thẻ không lấp đầy hàng, thừa khoảng trắng bên phải.
        if (cardGrid != null) {
            cardGrid.widthProperty().addListener((obs, oldW, newW) -> {
                double w = newW.doubleValue();
                if (w > 100) {
                    double pad = cardGrid.getPadding().getLeft() + cardGrid.getPadding().getRight();
                    int newCardW = Math.max(180, (int)((w - pad - (CARD_COLS - 1) * CARD_HGAP) / CARD_COLS));
                    if (newCardW != computedCardW) {
                        computedCardW = newCardW;
                        if (cardWidthInitialized) {
                            // Re-render chỉ khi thay đổi đáng kể (> 4px) để tránh loop
                            Platform.runLater(() -> renderCards());
                        } else {
                            cardWidthInitialized = true;
                        }
                    }
                }
            });
        }

        loadWishlist();
        loadAuctions();
        loadWalletBalance();
    }

    // ==========================================================
    //  THÔNG TIN NGƯỜI DÙNG
    // ==========================================================

    private void loadUserInfo() {
        UserSession s = UserSession.getInstance();
        if (lblUser != null) {
            String name = s.getUsername();
            lblUser.setText(name != null && !name.isEmpty() ? name : "Người dùng");
        }
        if (lblRole != null) {
            String role = s.getRole();
            lblRole.setText(role != null && !role.isEmpty() ? role : "");
        }
        if (this.imgAvatar != null) {
            javafx.scene.image.Image img;
            if (s.getAvatarBase64() != null && !s.getAvatarBase64().isBlank()) {
                byte[] imageBytes = com.auction.client.util.ImageUtil.decodeToBytes(s.getAvatarBase64());
                img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes));
            } else {
                img = new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"));
            }
            applyCenterCrop(this.imgAvatar, img);
        }
    }

    // ==========================================================
    //  TẢI DỮ LIỆU TỪ SERVER
    // ==========================================================

    private void loadAuctions() {
        if (lblMessage != null) lblMessage.setVisible(false);
        auctionList.clear();
        if (btnRefresh != null) btnRefresh.setDisable(true);

        // Hiện spinner
        if (cardGrid != null) {
            cardGrid.getChildren().clear();
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(48, 48);
            cardGrid.getChildren().add(spinner);
        }

        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");
                com.google.gson.JsonElement response = SocketClient.getInstance().send(
                    "GET_AUCTIONS", params, com.google.gson.JsonElement.class);

                Platform.runLater(() -> {
                    if (response != null) {
                        JsonArray arr = null;
                        if (response.isJsonArray()) arr = response.getAsJsonArray();
                        else if (response.isJsonObject()) {
                            JsonObject obj = response.getAsJsonObject();
                            if (obj.has("auctions")) arr = obj.getAsJsonArray("auctions");
                        }
                        if (arr != null) {
                            arr.forEach(el -> auctionList.add(el.getAsJsonObject()));
                        }
                    }
                    applyAuctionFilter();
                    if (btnRefresh != null) btnRefresh.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (btnRefresh != null) btnRefresh.setDisable(false);
                    if (cardGrid != null) cardGrid.getChildren().clear();
                    showMessage("Lỗi tải dữ liệu: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    // ==========================================================
    //  LỌC VÀ RENDER CARDS
    // ==========================================================

    private void applyAuctionFilter() {
        String q      = txtSearch == null ? "" : txtSearch.getText().trim().toLowerCase();
        String status = cboFilter == null ? "Tất cả" : cboFilter.getValue();
        filteredAuctions.setPredicate(makeAuctionPredicate(q, status));
        renderCards();
        updateAuctionCount();
    }

    /**
     * Xóa grid cũ + tất cả countdown cũ, sau đó dựng lại toàn bộ card
     * từ danh sách đã lọc.
     */
    private void renderCards() {
        // Dừng và xóa countdown cũ
        stopSharedTimer();
        cardTimers.clear();
        if (cardGrid != null) cardGrid.getChildren().clear();
        updateWishlistFilterButton();

        List<JsonObject> items = new ArrayList<>();
        filteredAuctions.forEach(items::add);

        // Hiện/ẩn emptyBox
        boolean empty = items.isEmpty();
        if (emptyBox != null) { emptyBox.setVisible(empty); emptyBox.setManaged(empty); }

        for (JsonObject auction : items) {
            Node card = buildAuctionCard(auction);
            if (cardGrid != null) cardGrid.getChildren().add(card);
        }

        // Khởi động countdown dùng chung (1 Timer cho tất cả cards)
        if (!cardTimers.isEmpty()) startSharedTimer();
    }

    // ==========================================================
    //  XÂY DỰNG AUCTION CARD (Shopee / Lazada style)
    // ==========================================================

    /**
     * Mỗi card gồm:
     *  ┌─────────────────────────────────┐
     *  │  [Thumbnail 220×160]            │
     *  │  [Badge: RUNNING / OPEN / ...]  │ (overlay góc trái trên)
     *  ├─────────────────────────────────┤
     *  │  Tên sản phẩm (bold, 2 dòng)   │
     *  │  💰 Giá hiện tại (đỏ, to)      │
     *  │  ⏱  Countdown nhỏ              │
     *  │  🔨 N lượt đặt                  │
     *  │  [Nút: Xem chi tiết]            │
     *  └─────────────────────────────────┘
     */
    private Node buildAuctionCard(JsonObject auction) {
        // Tính CARD_W động: lấp đầy đúng 4 cột, không thừa khoảng trắng bên phải.
        // Nếu FlowPane chưa được layout (width = 0), dùng giá trị đã tính trước.
        if (cardGrid != null && cardGrid.getWidth() > 100 && !cardWidthInitialized) {
            double pad = cardGrid.getPadding().getLeft() + cardGrid.getPadding().getRight();
            computedCardW = Math.max(180, (int)((cardGrid.getWidth() - pad - (CARD_COLS - 1) * CARD_HGAP) / CARD_COLS));
            cardWidthInitialized = true;
        }
        final int CARD_W = computedCardW;

        // ── Root card container ──
        VBox card = new VBox(0);
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);
        card.setMinWidth(CARD_W);
        card.setStyle(
            "-fx-background-color: white;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.13), 12, 0, 0, 3);");
        card.setCursor(javafx.scene.Cursor.HAND);

        // ── Hover effect ──
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: white;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-effect: dropshadow(gaussian, rgba(142,68,173,0.35), 22, 0, 0, 6);" +
                "-fx-translate-y: -3;"));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: white;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.13), 12, 0, 0, 3);" +
                "-fx-translate-y: 0;"));

        // ════════════════════════════════
        //  THUMBNAIL SECTION  (async 16:9 load)
        // ════════════════════════════════
        final int THUMB_H = (int) Math.round(CARD_W * 9.0 / 16.0); // 16:9 → ~125px

        StackPane thumbPane = new StackPane();
        thumbPane.setPrefHeight(THUMB_H);
        thumbPane.setMinHeight(THUMB_H);
        thumbPane.setMaxHeight(THUMB_H);
        thumbPane.setStyle("-fx-background-color: #1a1a2e; -fx-background-radius: 16 16 0 0;");

        // Clip bo góc trên
        Rectangle thumbClip = new Rectangle(CARD_W, THUMB_H);
        thumbClip.setArcWidth(32); thumbClip.setArcHeight(32);
        thumbPane.setClip(thumbClip);

        // ── Placeholder hiện NGAY (spinner + icon) — thay thế bằng ảnh thật sau khi decode xong ──
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(32, 32);
        spinner.setStyle("-fx-progress-color: #9b59b6; -fx-opacity: 0.7;");

        Label noImgIcon = new Label("🖼");
        noImgIcon.setStyle("-fx-font-size: 44px; -fx-text-fill: #3d3d5c; -fx-opacity: 0.5;");

        // Badge 📷 ảnh — ẩn trước, bật sau khi ảnh load xong
        Label imgBadge = new Label("📷");
        imgBadge.setStyle(
            "-fx-background-color: rgba(0,0,0,0.55);" +
                "-fx-background-radius: 6;" +
                "-fx-padding: 3 7;" +
                "-fx-font-size: 11px;");
        imgBadge.setVisible(false);
        StackPane.setAlignment(imgBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(imgBadge, new Insets(10, 10, 0, 0));

        // ImageView — fitWidth=CARD_W, fitHeight=THUMB_H, viewport crop 16:9 sẽ áp sau
        ImageView imgProduct = new ImageView();
        imgProduct.setFitWidth(CARD_W);
        imgProduct.setFitHeight(THUMB_H);
        imgProduct.setPreserveRatio(false); // viewport tự crop, không cần preserveRatio
        imgProduct.setSmooth(true);
        imgProduct.setVisible(false); // ẩn cho đến khi ảnh decode xong

        thumbPane.getChildren().addAll(noImgIcon, imgProduct, imgBadge);

        // ── Badge trạng thái (góc trên trái) ──
        String status = getJsonString(auction, "status");
        Label badge = buildStatusBadge(status);
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(10, 0, 0, 10));
        thumbPane.getChildren().add(badge);

        // ── Heart / Bookmark button (góc trên phải thumbnail) ──
        final String auctionId = getJsonString(auction, "auctionId");
        Label heartBtn = new Label(isWishlisted(auctionId) ? "❤" : "♡");
        heartBtn.setStyle(
            "-fx-font-size: 22px; -fx-cursor: hand;" +
                (isWishlisted(auctionId)
                    ? "-fx-text-fill: #e74c3c; -fx-effect: dropshadow(gaussian,rgba(231,76,60,0.6),6,0,0,1);"
                    : "-fx-text-fill: white; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.7),4,0,0,1);"));
        StackPane.setAlignment(heartBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(heartBtn, new Insets(8, 10, 0, 0));
        heartBtn.setOnMouseClicked(ev -> {
            ev.consume();
            toggleWishlist(auctionId);
            boolean nowLiked = isWishlisted(auctionId);
            heartBtn.setText(nowLiked ? "❤" : "♡");
            heartBtn.setStyle(
                "-fx-font-size: 22px; -fx-cursor: hand;" +
                    (nowLiked
                        ? "-fx-text-fill: #e74c3c; -fx-effect: dropshadow(gaussian,rgba(231,76,60,0.6),6,0,0,1);"
                        : "-fx-text-fill: white; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.7),4,0,0,1);"));
            // Fill animation: bounce scale
            ScaleTransition st = new ScaleTransition(Duration.millis(180), heartBtn);
            st.setFromX(1.0); st.setFromY(1.0);
            st.setToX(1.45); st.setToY(1.45);
            st.setAutoReverse(true); st.setCycleCount(2);
            st.play();
            if (showingWishlistOnly && !nowLiked) {
                Platform.runLater(() -> renderCards());
            }
        });
        heartBtn.setOnMouseEntered(ev -> {
            ScaleTransition hs = new ScaleTransition(Duration.millis(120), heartBtn);
            hs.setToX(1.2); hs.setToY(1.2); hs.play();
        });
        heartBtn.setOnMouseExited(ev -> {
            ScaleTransition hs = new ScaleTransition(Duration.millis(120), heartBtn);
            hs.setToX(1.0); hs.setToY(1.0); hs.play();
        });
        thumbPane.getChildren().add(heartBtn);

        // ── Async decode: tách ra background thread, KHÔNG block FX thread ──
        String desc = getJsonString(auction, "description");
        String b64  = extractFirstBase64(desc); // chỉ parse chuỗi, không decode bytes
        if (b64 != null) {
            thumbPane.getChildren().add(spinner); // hiện spinner trong khi chờ
            final String finalB64 = b64;
            Thread imgThread = new Thread(() -> {
                try {
                    // Bước nặng: decode base64 → bytes  (chạy trên background thread)
                    byte[] bytes = com.auction.client.util.ImageUtil.decodeToBytes(finalB64);
                    if (bytes.length == 0) { Platform.runLater(() -> thumbPane.getChildren().remove(spinner)); return; }

                    // Tạo Image — BackgroundLoading=false vì bytes đã có sẵn trong RAM
                    Image img = new Image(new java.io.ByteArrayInputStream(bytes));

                    Platform.runLater(() -> {
                        // Áp crop 16:9 qua Viewport rồi hiện ảnh
                        apply16by9Crop(imgProduct, img);
                        imgProduct.setVisible(true);
                        imgBadge.setVisible(true);
                        noImgIcon.setVisible(false);
                        thumbPane.getChildren().remove(spinner);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> thumbPane.getChildren().remove(spinner));
                }
            }, "thumb-" + getJsonString(auction, "auctionId"));
            imgThread.setDaemon(true);
            imgThread.start();
        }
        // Nếu không có b64 → giữ noImgIcon placeholder, không cần làm gì thêm

        card.getChildren().add(thumbPane);

        // ════════════════════════════════
        //  INFO SECTION
        // ════════════════════════════════
        VBox info = new VBox(6);
        info.setPadding(new Insets(12, 14, 14, 14));

        // Tên sản phẩm
        Label lblName = new Label(getJsonString(auction, "title"));
        lblName.setWrapText(true);
        lblName.setMaxHeight(44);
        lblName.setStyle(
            "-fx-font-size: 13.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #1a1a2e;" +
                "-fx-line-spacing: 2;");
        lblName.setMaxWidth(CARD_W - 28);

        // Giá hiện tại
        long currentPrice = auction.has("currentPrice") ? auction.get("currentPrice").getAsLong() : 0;
        Label lblPrice = new Label(formatMoney(currentPrice));
        lblPrice.setStyle(
            "-fx-font-size: 16px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #c0392b;");

        // Countdown mini
        long timeRemaining = auction.has("timeRemaining")
            ? auction.get("timeRemaining").getAsLong() : 0;
        Label lblTimer = buildMiniTimer(status, timeRemaining);

        // Đăng ký countdown nếu phiên đang chạy
        if ("RUNNING".equalsIgnoreCase(status) && timeRemaining > 0) {
            long[] ref = {timeRemaining};
            cardTimers.add(new CardTimer(ref, lblTimer, status));
        }

        // Số lượt đặt
        String bidCount = getJsonString(auction, "bidCount");
        if (bidCount.isEmpty()) bidCount = "0";
        Label lblBids = new Label("🔨  " + bidCount + " lượt đặt");
        lblBids.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7f8c8d;");

        // Người bán
        String sellerId = getJsonString(auction, "sellerId");
        Label lblSeller = new Label("👤 " + sellerId);
        lblSeller.setStyle("-fx-font-size: 11px; -fx-text-fill: #9b59b6;");
        lblSeller.setMaxWidth(CARD_W - 28);

        // Nút xem chi tiết
        Button btnView = new Button("Xem chi tiết  →");
        btnView.setMaxWidth(Double.MAX_VALUE);
        btnView.setStyle(
            "-fx-background-color: #8e44ad;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 9 0;" +
                "-fx-background-radius: 10;" +
                "-fx-cursor: hand;");
        btnView.setOnMouseEntered(e -> btnView.setStyle(
            "-fx-background-color: #6c3483;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 9 0;" +
                "-fx-background-radius: 10;" +
                "-fx-cursor: hand;" +
                "-fx-effect: dropshadow(gaussian, rgba(142,68,173,0.5), 8, 0, 0, 2);"));
        btnView.setOnMouseExited(e -> btnView.setStyle(
            "-fx-background-color: #8e44ad;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 9 0;" +
                "-fx-background-radius: 10;" +
                "-fx-cursor: hand;"));
        btnView.setOnAction(ev -> openAuctionDetail(auction, btnView));
        // Click cả card cũng mở
        card.setOnMouseClicked(ev -> {
            if (!(ev.getTarget() instanceof Button)) openAuctionDetail(auction, card);
        });

        VBox.setMargin(btnView, new Insets(6, 0, 0, 0));

        info.getChildren().addAll(lblName, lblPrice, lblTimer, lblBids, lblSeller, btnView);
        card.getChildren().add(info);

        return card;
    }

    /** Badge trạng thái: màu sắc tương ứng từng status */
    private Label buildStatusBadge(String status) {
        Label badge = new Label();
        String text; String bg;
        switch (status == null ? "" : status.toUpperCase()) {
            case "RUNNING"  -> { text = "🟢 Đang diễn ra"; bg = "rgba(39,174,96,0.92)"; }
            case "OPEN"     -> { text = "🔵 Sắp diễn ra";  bg = "rgba(41,128,185,0.90)"; }
            case "FINISHED" -> { text = "🟠 Đã kết thúc";  bg = "rgba(230,126,34,0.90)"; }
            case "PAID"     -> { text = "🟣 Đã thanh toán";bg = "rgba(142,68,173,0.90)"; }
            case "CANCELED" -> { text = "⚫ Đã hủy";       bg = "rgba(100,100,100,0.85)"; }
            default         -> { text = status != null ? status : "—"; bg = "rgba(80,80,80,0.80)"; }
        }
        badge.setText(text);
        badge.setStyle(
            "-fx-background-color: " + bg + ";" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 4 10;" +
                "-fx-font-size: 10.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: white;");
        return badge;
    }

    /** Label đếm ngược nhỏ gọn */
    private Label buildMiniTimer(String status, long secondsRemaining) {
        Label lbl = new Label();
        lbl.setStyle(
            "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-font-family: 'Monospace';");
        updateTimerLabel(lbl, status, secondsRemaining);
        return lbl;
    }

    private void updateTimerLabel(Label lbl, String status, long secs) {
        if (!"RUNNING".equalsIgnoreCase(status)) {
            lbl.setText("⏱  —");
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #bdc3c7; -fx-font-family: 'Monospace';");
            return;
        }
        if (secs <= 0) {
            lbl.setText("⏱  ĐÃ KẾT THÚC");
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #c0392b; -fx-font-weight: bold; -fx-font-family: 'Monospace';");
            return;
        }
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        String timeStr = String.format("⏱  %02d:%02d:%02d", h, m, s);
        String color;
        if (secs <= 60)       color = "#c0392b";
        else if (secs <= 300) color = "#e67e22";
        else                  color = "#2980b9";
        lbl.setText(timeStr);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + color + "; -fx-font-family: 'Monospace';");
    }

    // ==========================================================
    //  SHARED COUNTDOWN TIMER
    // ==========================================================

    private void startSharedTimer() {
        sharedTimer = new Timer(true);
        sharedTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                Platform.runLater(() -> {
                    for (CardTimer ct : cardTimers) {
                        if (ct.secondsRef[0] > 0) ct.secondsRef[0]--;
                        updateTimerLabel(ct.timerLabel, ct.status, ct.secondsRef[0]);
                    }
                });
            }
        }, 1000, 1000);
    }

    private void stopSharedTimer() {
        if (sharedTimer != null) { sharedTimer.cancel(); sharedTimer = null; }
    }

    // ==========================================================
    //  MỞ CHI TIẾT PHIÊN
    // ==========================================================

    private void openAuctionDetail(JsonObject auction, Node sourceNode) {
        if (auction == null) { AlertUtil.showWarning("Lỗi", "Dữ liệu phiên rỗng."); return; }
        String auctionId = getJsonString(auction, "auctionId");
        String title     = getJsonString(auction, "title");
        if (auctionId == null || auctionId.isEmpty()) {
            AlertUtil.showWarning("Lỗi dữ liệu", "Phiên đấu giá chưa có ID hợp lệ."); return;
        }
        try {
            stopSharedTimer(); // Dừng timer trước khi chuyển màn hình
            ViewLoader.ViewResult<AuctionDetailController> result =
                ViewLoader.loadViewWithController("auction-detail.fxml");
            if (result != null) {
                result.getController().initData(auctionId);
                javafx.stage.Stage stage =
                    (javafx.stage.Stage) sourceNode.getScene().getWindow();
                stage.getScene().setRoot(result.getView());
                stage.setTitle("Chi tiết phiên đấu giá - " + (title.isEmpty() ? auctionId : title));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở chi tiết phiên: " + ex.getMessage());
        }
    }

    // ==========================================================
    //  BỘ LỌC NÂNG CAO
    // ==========================================================

    @FXML
    private void handleApplyAdvancedFilter(ActionEvent event) {
        filterPriceMin = -1;
        if (txtPriceMin != null && !txtPriceMin.getText().trim().isEmpty()) {
            try {
                filterPriceMin = Double.parseDouble(txtPriceMin.getText().trim().replace(",", ""));
                if (filterPriceMin < 0) { AlertUtil.showError("Lỗi", "Giá từ không được âm."); return; }
            } catch (NumberFormatException ex) {
                AlertUtil.showError("Lỗi định dạng", "Giá tối thiểu không hợp lệ. Chỉ nhập số."); return;
            }
        }
        filterPriceMax = -1;
        if (txtPriceMax != null && !txtPriceMax.getText().trim().isEmpty()) {
            try {
                filterPriceMax = Double.parseDouble(txtPriceMax.getText().trim().replace(",", ""));
                if (filterPriceMax < 0) { AlertUtil.showError("Lỗi", "Giá đến không được âm."); return; }
            } catch (NumberFormatException ex) {
                AlertUtil.showError("Lỗi định dạng", "Giá tối đa không hợp lệ. Chỉ nhập số."); return;
            }
        }
        if (filterPriceMin >= 0 && filterPriceMax >= 0 && filterPriceMin > filterPriceMax) {
            AlertUtil.showError("Lỗi", "Giá từ không được lớn hơn giá đến."); return;
        }
        filterMinMinutes = -1;
        if (txtMinMinutes != null && !txtMinMinutes.getText().trim().isEmpty()) {
            try {
                filterMinMinutes = Integer.parseInt(txtMinMinutes.getText().trim());
                if (filterMinMinutes < 0) { AlertUtil.showError("Lỗi", "Số phút không được âm."); return; }
            } catch (NumberFormatException ex) {
                AlertUtil.showError("Lỗi định dạng", "Số phút không hợp lệ. Chỉ nhập số nguyên."); return;
            }
        }
        filterHasImage = (cboHasImage != null && cboHasImage.getValue() != null)
            ? cboHasImage.getValue() : "Tất cả";
        applyAuctionFilter();
        updateFilterStatusLabel();
    }

    @FXML
    private void handleClearAdvancedFilter(ActionEvent event) {
        filterPriceMin = filterPriceMax = -1;
        filterMinMinutes = -1;
        filterHasImage   = "Tất cả";
        if (txtPriceMin   != null) txtPriceMin.clear();
        if (txtPriceMax   != null) txtPriceMax.clear();
        if (txtMinMinutes != null) txtMinMinutes.clear();
        if (cboHasImage   != null) cboHasImage.setValue("Tất cả");
        if (lblFilterStatus != null) { lblFilterStatus.setText(""); lblFilterStatus.setVisible(false); }
        applyAuctionFilter();
    }

    private void updateFilterStatusLabel() {
        if (lblFilterStatus == null) return;
        StringBuilder sb = new StringBuilder();
        if (filterPriceMin >= 0 || filterPriceMax >= 0) {
            sb.append("💰 Giá: ");
            if (filterPriceMin >= 0) sb.append(formatMoney((long) filterPriceMin));
            sb.append(" → ");
            if (filterPriceMax >= 0) sb.append(formatMoney((long) filterPriceMax));
            else sb.append("∞");
            sb.append("\n");
        }
        if (filterMinMinutes >= 0) sb.append("⏱ Còn ≥ ").append(filterMinMinutes).append(" phút\n");
        if (!"Tất cả".equals(filterHasImage)) sb.append("📷 ").append(filterHasImage).append("\n");
        String text = sb.toString().trim();
        lblFilterStatus.setText(text.isEmpty() ? "" : "Đang lọc:\n" + text);
        lblFilterStatus.setVisible(!text.isEmpty());
    }

    // ==========================================================
    //  PREDICATE LỌC
    // ==========================================================

    private Predicate<JsonObject> makeAuctionPredicate(String q, String statusFilter) {
        return auction -> {
            if (auction == null) return false;
            // Wishlist filter
            if (showingWishlistOnly) {
                String id = getJsonString(auction, "auctionId");
                if (!wishlistIds.contains(id)) return false;
            }
            if (!"ALL".equals(currentCategory)) {
                String category = getJsonString(auction, "category");
                if (!currentCategory.equalsIgnoreCase(category)) return false;
            }
            if (statusFilter != null && !statusFilter.isEmpty() && !"Tất cả".equals(statusFilter)) {
                String s = getJsonString(auction, "status").toLowerCase();
                if (!s.contains(statusFilter.toLowerCase())) return false;
            }
            if (q != null && !q.isEmpty()) {
                String title  = getJsonString(auction, "title").toLowerCase();
                String seller = getJsonString(auction, "sellerId").toLowerCase();
                if (!title.contains(q) && !seller.contains(q)) return false;
            }
            double price = auction.has("currentPrice") ? auction.get("currentPrice").getAsDouble() : 0;
            if (filterPriceMin >= 0 && price < filterPriceMin) return false;
            if (filterPriceMax >= 0 && price > filterPriceMax) return false;
            if (filterMinMinutes >= 0) {
                long timeRemainingSeconds = auction.has("timeRemaining")
                    ? auction.get("timeRemaining").getAsLong() : 0;
                if (timeRemainingSeconds / 60 < filterMinMinutes) return false;
            }
            if (!"Tất cả".equals(filterHasImage)) {
                boolean imgExists = hasImage(auction);
                if ("Có ảnh".equals(filterHasImage) && !imgExists) return false;
                if ("Không có ảnh".equals(filterHasImage) && imgExists) return false;
            }
            return true;
        };
    }

    private void updateAuctionCount() {
        if (lblAuctionCount != null) {
            int shown = filteredAuctions == null ? auctionList.size() : filteredAuctions.size();
            int total = auctionList.size();
            lblAuctionCount.setText((shown == total)
                ? String.format("%d phiên", total)
                : String.format("%d / %d phiên (đã lọc)", shown, total));
        }
    }

    // ==========================================================
    //  HANDLERS DANH MỤC SIDEBAR
    // ==========================================================

    // ==========================================================
    //  WISHLIST / BOOKMARK
    // ==========================================================

    @FXML
    public void showWishlist() {
        showingWishlistOnly = !showingWishlistOnly;
        currentCategory = "ALL";
        setTitle(showingWishlistOnly ? "❤ Phiên yêu thích của bạn" : "Danh sách phiên đấu giá");
        applyAuctionFilter();
    }

    private void updateWishlistFilterButton() {
        if (btnWishlistFilter == null) return;
        if (showingWishlistOnly) {
            btnWishlistFilter.setStyle(
                "-fx-background-color: #c0392b; -fx-font-size: 13px; -fx-cursor: hand;" +
                    "-fx-alignment: CENTER_LEFT; -fx-padding: 7 8; -fx-text-fill: white;" +
                    "-fx-font-weight: bold; -fx-background-radius: 8;");
        } else {
            btnWishlistFilter.setStyle(
                "-fx-background-color: transparent; -fx-font-size: 13px;" +
                    "-fx-cursor: hand; -fx-alignment: CENTER_LEFT; -fx-padding: 7 8;" +
                    "-fx-text-fill: #c0392b; -fx-font-weight: bold;");
        }
    }

    private void toggleWishlist(String auctionId) {
        if (auctionId == null || auctionId.isEmpty()) return;
        if (wishlistIds.contains(auctionId)) {
            wishlistIds.remove(auctionId);
        } else {
            wishlistIds.add(auctionId);
        }
        saveWishlist();
    }

    private boolean isWishlisted(String auctionId) {
        return auctionId != null && wishlistIds.contains(auctionId);
    }

    @SuppressWarnings("unchecked")
    private void loadWishlist() {
        try {
            Path path = Paths.get(WISHLIST_FILE);
            if (Files.exists(path)) {
                String json = new String(Files.readAllBytes(path));
                java.util.List<String> ids = new Gson().fromJson(json,
                    new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
                if (ids != null) wishlistIds.addAll(ids);
            }
        } catch (Exception e) {
            System.out.println("Wishlist load warning: " + e.getMessage());
        }
    }

    private void saveWishlist() {
        try {
            String json = new Gson().toJson(new java.util.ArrayList<>(wishlistIds));
            Files.write(Paths.get(WISHLIST_FILE), json.getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.out.println("Wishlist save warning: " + e.getMessage());
        }
    }

    @FXML public void showAllItems()    { currentCategory = "ALL";         setTitle("Danh sách phiên đấu giá");             applyAuctionFilter(); }
    @FXML public void showArts()        { currentCategory = "Art";         setTitle("Danh sách: Nghệ thuật (Art)");          applyAuctionFilter(); }
    @FXML public void showElectronics() { currentCategory = "Electronics"; setTitle("Danh sách: Đồ điện tử (Electronics)"); applyAuctionFilter(); }
    @FXML public void showVehicles()    { currentCategory = "Vehicle";     setTitle("Danh sách: Phương tiện (Vehicle)");     applyAuctionFilter(); }
    @FXML public void showOthers()      { currentCategory = "Other";       setTitle("Danh sách: Tài sản khác");              applyAuctionFilter(); }

    private void setTitle(String title) { if (lblMainTitle != null) lblMainTitle.setText(title); }

    // ==========================================================
    //  HANDLERS TOOLBAR
    // ==========================================================

    @FXML private void handleSearch(javafx.scene.input.KeyEvent event) { applyAuctionFilter(); }
    @FXML private void handleFilter(ActionEvent event)  { applyAuctionFilter(); }
    @FXML private void handleRefresh(ActionEvent event) { loadAuctions(); }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            stopSharedTimer();
            UserSession.getInstance().cleanUserSession();
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    // ==========================================================
    //  VÍ ĐIỆN TỬ
    // ==========================================================

    private void loadWalletBalance() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                JsonObject data = SocketClient.getInstance().send("GET_WALLET", params, JsonObject.class);
                if (data != null) {
                    long balance = data.has("availableBalance") ? data.get("availableBalance").getAsLong() : 0;
                    Platform.runLater(() -> {
                        if (lblWalletBalance != null) lblWalletBalance.setText(formatMoney(balance));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> { if (lblWalletBalance != null) lblWalletBalance.setText("Lỗi tải ví"); });
            }
        }).start();
    }

    @FXML
    public void handleOpenWallet(ActionEvent event) {
        try {
            stopSharedTimer();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/auction/client/fxml/bidder-wallet.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setTitle("Ví Điện Tử - Quản lý số dư");
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở ví: " + e.getMessage());
        }
    }

    // ==========================================================
    //  PROFILE POPUP
    // ==========================================================

    @FXML
    private void handleProfileClick(MouseEvent event) {
        if (profilePopup == null) profilePopup = buildProfilePopup();
        if (profilePopup.isShowing()) { profilePopup.hide(); return; }
        Node source  = (Node) event.getSource();
        double ax = source.localToScreen(source.getBoundsInLocal()).getMinX();
        double ay = source.localToScreen(source.getBoundsInLocal()).getMaxY() + 6;
        profilePopup.show(source, ax, ay);
    }

    private Popup buildProfilePopup() {
        UserSession session = UserSession.getInstance();
        Popup popup = new Popup();
        popup.setAutoHide(true); popup.setAutoFix(true);

        StackPane wrapper = new StackPane(); wrapper.setPadding(new Insets(8));
        VBox card = new VBox(0); card.setPrefWidth(300);
        card.setStyle("-fx-background-color: rgba(35, 10, 60, 0.93); -fx-background-radius: 18; -fx-border-color: rgba(155, 89, 182, 0.40); -fx-border-width: 1.5; -fx-border-radius: 18;");
        DropShadow ds = new DropShadow(); ds.setColor(Color.rgb(67, 20, 118, 0.55)); ds.setRadius(28); ds.setOffsetY(8);
        card.setEffect(ds);

        VBox topSection = new VBox(6); topSection.setAlignment(Pos.CENTER); topSection.setPadding(new Insets(24, 20, 18, 20));
        ImageView popupAvatar = new ImageView(); popupAvatar.setFitWidth(64); popupAvatar.setFitHeight(64); popupAvatar.setPreserveRatio(true);

        if (session.getAvatarBase64() != null && !session.getAvatarBase64().isBlank()) {
            byte[] imageBytes = com.auction.client.util.ImageUtil.decodeToBytes(session.getAvatarBase64());
            applyCenterCrop(popupAvatar, new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes)));
        } else {
            try { applyCenterCrop(popupAvatar, new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"))); }
            catch (Exception ignored) {}
        }
        popupAvatar.setClip(new Circle(32, 32, 32));
        DropShadow avatarGlow = new DropShadow(); avatarGlow.setColor(Color.rgb(155, 89, 182, 0.70)); avatarGlow.setRadius(14);
        popupAvatar.setEffect(avatarGlow);

        StackPane avatarContainer = new StackPane(); avatarContainer.setMaxSize(64, 64);
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-background-radius: 32;"); overlay.setOpacity(0);
        Label editIcon = new Label("📷"); editIcon.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");
        overlay.getChildren().add(editIcon);
        avatarContainer.getChildren().addAll(popupAvatar, overlay);
        avatarContainer.setCursor(javafx.scene.Cursor.HAND);
        avatarContainer.setOnMouseEntered(e -> { overlay.setOpacity(1); popupAvatar.setEffect(new DropShadow(javafx.scene.effect.BlurType.THREE_PASS_BOX, Color.web("#00ffff"), 12, 0.3, 0, 0)); });
        avatarContainer.setOnMouseExited(e -> { overlay.setOpacity(0); popupAvatar.setEffect(avatarGlow); });
        avatarContainer.setOnMouseClicked(e -> handleAvatarClick(popupAvatar));

        Label lblFullName = new Label(nullSafe(session.getFullName(), session.getUsername()));
        lblFullName.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #f0e6ff; -fx-padding: 8 0 2 0;");
        Label lblAtUsername = new Label("@" + nullSafe(session.getUsername(), "—"));
        lblAtUsername.setStyle("-fx-font-size: 13px; -fx-text-fill: #9b59b6;");
        topSection.getChildren().addAll(avatarContainer, lblFullName, lblAtUsername);

        VBox infoSection = new VBox(10); infoSection.setPadding(new Insets(14, 24, 14, 24));
        infoSection.getChildren().addAll(
            infoRow("👤", "User ID", nullSafe(session.getUserId(), "—")),
            infoRow("✉",  "Email",   nullSafe(session.getEmail(), "Chưa cập nhật")),
            infoRow("🏷",  "Vai trò", nullSafe(session.getRole(), "—"))
        );

        VBox actionSection = new VBox(10); actionSection.setAlignment(Pos.CENTER); actionSection.setPadding(new Insets(14, 20, 20, 20));
        Button btnChangePassword = new Button("🔐  Đổi mật khẩu"); btnChangePassword.setMaxWidth(Double.MAX_VALUE);
        btnChangePassword.setStyle("-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;");
        btnChangePassword.setOnAction(e -> { popup.hide(); handleChangePassword(); });
        actionSection.getChildren().add(btnChangePassword);

        card.getChildren().addAll(topSection, styledDivider(), infoSection, styledDivider(), actionSection);
        wrapper.getChildren().add(card);
        popup.getContent().add(wrapper);
        return popup;
    }

    private HBox infoRow(String icon, String labelText, String value) {
        Label iconLbl = new Label(icon); iconLbl.setStyle("-fx-font-size: 14px;"); iconLbl.setMinWidth(22);
        Label keyLbl  = new Label(labelText + ":"); keyLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #9b59b6; -fx-min-width: 70;");
        Label valLbl  = new Label(value); valLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #dcd0ff; -fx-font-weight: bold;"); valLbl.setWrapText(true);
        HBox row = new HBox(8, iconLbl, keyLbl, valLbl); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Separator styledDivider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: rgba(155,89,182,0.30); -fx-padding: 0 20;");
        VBox.setMargin(sep, new Insets(0, 20, 0, 20));
        return sep;
    }

    // ==========================================================
    //  CHANGE PASSWORD
    // ==========================================================

    private void handleChangePassword() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/auction/client/fxml/change-password.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage modalStage = new javafx.stage.Stage();
            modalStage.setTitle("Đổi mật khẩu");
            modalStage.setScene(new javafx.scene.Scene(root));
            modalStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            if (lblUser != null && lblUser.getScene() != null)
                modalStage.initOwner(lblUser.getScene().getWindow());
            modalStage.setResizable(false);
            modalStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở màn hình đổi mật khẩu: " + e.getMessage());
        }
    }

    // ==========================================================
    //  AVATAR
    // ==========================================================

    private void handleAvatarClick(ImageView popupAvatar) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem uploadItem = new javafx.scene.control.MenuItem("📷  Tải ảnh lên...");
        uploadItem.setOnAction(e -> {
            String base64 = com.auction.client.util.ImageUtil.pickAndEncodeAvatar(null);
            if (base64 == null) return;
            sendAvatarUpdate(base64, popupAvatar);
        });
        javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("🗑  Xóa ảnh");
        removeItem.setOnAction(e -> sendAvatarUpdate(null, popupAvatar));
        menu.getItems().addAll(uploadItem, removeItem);
        menu.show(popupAvatar, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void sendAvatarUpdate(String base64, javafx.scene.image.ImageView popupAvatar) {
        com.auction.client.model.UserSession session = com.auction.client.model.UserSession.getInstance();
        if (session.getUserId() == null) return;
        com.auction.shared.dto.request.UpdateAvatarRequest req =
            new com.auction.shared.dto.request.UpdateAvatarRequest(session.getUserId(), base64);
        new Thread(() -> {
            try {
                com.auction.client.network.SocketClient.getInstance()
                    .send(com.auction.shared.network.protocol.Actions.UPDATE_AVATAR, req, String.class);
                Platform.runLater(() -> {
                    session.setAvatarBase64(base64);
                    javafx.scene.image.Image newImg;
                    if (base64 == null) {
                        newImg = new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"));
                    } else {
                        byte[] imageBytes = com.auction.client.util.ImageUtil.decodeToBytes(base64);
                        newImg = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes));
                    }
                    applyCenterCrop(popupAvatar, newImg);
                    if (this.imgAvatar != null) applyCenterCrop(this.imgAvatar, newImg);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> com.auction.client.util.AlertUtil.showError("Lỗi", "Không thể cập nhật ảnh: " + ex.getMessage()));
            }
        }).start();
    }

    private void applyCenterCrop(javafx.scene.image.ImageView imageView, javafx.scene.image.Image img) {
        imageView.setImage(img);
        double w = img.getWidth(), h = img.getHeight();
        if (w > 0 && h > 0) {
            double size = Math.min(w, h);
            imageView.setViewport(new javafx.geometry.Rectangle2D((w - size) / 2.0, (h - size) / 2.0, size, size));
        }
    }

    // ==========================================================
    //  HELPERS
    // ==========================================================

    @FXML
    private void handleToggleTheme(ActionEvent event) {
        com.auction.client.util.ThemeManager tm = com.auction.client.util.ThemeManager.getInstance();
        tm.toggle();
        if (btnTheme != null) btnTheme.setText(tm.getToggleIcon());
    }

    private boolean hasImage(JsonObject auction) {
        if (auction == null) return false;
        String desc = getJsonString(auction, "description");
        return desc.startsWith("[IMGS:") || desc.startsWith("[IMG:");
    }

    /**
     * Chỉ PARSE chuỗi base64 đầu tiên từ description prefix —
     * KHÔNG decode bytes (tránh block FX thread).
     * Decode thực sự được thực hiện async trong background thread tại buildAuctionCard.
     *
     * Format hỗ trợ:
     *   [IMGS:base64_1|base64_2|...]  → trả base64_1
     *   [IMG:base64]                  → trả base64
     */
    private String extractFirstBase64(String desc) {
        if (desc == null || desc.isBlank()) return null;
        try {
            if (desc.startsWith("[IMGS:")) {
                int end = desc.indexOf("]");
                if (end > 6) {
                    String all = desc.substring(6, end);
                    return all.contains("|") ? all.split("\\|")[0].trim() : all.trim();
                }
            } else if (desc.startsWith("[IMG:")) {
                int end = desc.indexOf("]");
                if (end > 5) return desc.substring(5, end).trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Áp crop 16:9 vào ImageView bằng Viewport (không méo ảnh).
     * Tương đương CSS object-fit: cover với aspect-ratio 16/9.
     * Gọi trên FX thread sau khi Image đã được tạo.
     */
    private void apply16by9Crop(ImageView iv, Image img) {
        if (img == null || img.isError()) return;
        iv.setImage(img);
        double iw = img.getWidth();
        double ih = img.getHeight();
        if (iw <= 0 || ih <= 0) return;

        final double TARGET = 16.0 / 9.0;
        double imgAspect = iw / ih;
        double cropW, cropH, ox, oy;

        if (imgAspect > TARGET) {
            // Ảnh rộng hơn 16:9 → cắt hai bên, giữ chiều cao
            cropH = ih;
            cropW = ih * TARGET;
            ox = (iw - cropW) / 2.0;
            oy = 0;
        } else {
            // Ảnh cao hơn 16:9 → cắt trên/dưới, giữ chiều rộng
            cropW = iw;
            cropH = iw / TARGET;
            ox = 0;
            oy = (ih - cropH) / 2.0;
        }
        iv.setViewport(new javafx.geometry.Rectangle2D(ox, oy, cropW, cropH));
    }

    private String getJsonString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
            ? obj.get(key).getAsString() : "";
    }

    private String formatMoney(long amount) { return String.format("%,d VNĐ", amount); }

    private String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private void showMessage(String msg) {
        if (lblMessage != null) { lblMessage.setText(msg); lblMessage.setVisible(true); }
    }
}