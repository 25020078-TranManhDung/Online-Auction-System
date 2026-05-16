package com.auction.client.controller;

import com.auction.client.network.MessageHandler;
import com.auction.client.network.SocketClient;
import com.auction.client.model.UserSession;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.client.controller.AuctionDetailController;
import com.auction.client.controller.BidderWalletController;
import com.auction.shared.dto.response.WalletResponse;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class BiddingController implements BidUpdateListener {

    // ===== Root pane =====
    @FXML private AnchorPane biddingPane;

    // ===== Header =====
    @FXML private ImageView imgAvatar;
    @FXML private Label lblUser;
    @FXML private Label lblRole;
    @FXML private Button btnBack;
    @FXML private Button btnTheme;  // Dark/Light mode toggle


    // ===== Left column (info) =====
    @FXML private Label lblProductName;
    @FXML private Label lblSeller;
    @FXML private Label lblStatus;
    @FXML private Label lblBidCount;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblCountdown;
    @FXML private Label lblMinIncrement;

    // ===== Product images =====
    @FXML private VBox      paneImages;
    @FXML private StackPane imgMainPane;
    @FXML private ImageView imgProductMain;
    @FXML private Button    btnImgPrev;
    @FXML private Button    btnImgNext;
    @FXML private Label     lblImgCounter;
    @FXML private HBox      hboxThumbs;

    // Bid history table
    @FXML private TableView<JsonObject> tbBidHistory;
    @FXML private TableColumn<JsonObject, String>  colBidNo;
    @FXML private TableColumn<JsonObject, String>  colBidder;
    @FXML private TableColumn<JsonObject, String>  colBidAmount;
    @FXML private TableColumn<JsonObject, String>  colBidTime;
    @FXML private Label lblHistoryCount;

    // ===== Right column (form) =====
    @FXML private TextField txtBidAmount;
    @FXML private Label lblBidHint;
    @FXML private Button btnQuick1;
    @FXML private Button btnQuick2;
    @FXML private Button btnQuick3;
    @FXML private Button btnConfirmBid;
    @FXML private Label lblMessage;

    // ===== Auto-bid form =====
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtIncrement;
    @FXML private Button btnSetAutoBid;
    @FXML private Button btnCancelAutoBid;

    // ===== Leader card =====
    @FXML private ImageView imgLeaderAvatar;
    @FXML private Label lblLeaderName;
    @FXML private Label lblLeaderBid;

    @FXML private Label lblWalletBalance;

    // Internal state
    private String auctionId;
    private double currentPrice;
    private double minIncrement;
    private String leaderName    = "—";
    private double leaderBid     = 0;
    private String productName   = "—";
    private String sellerId      = "—";
    private String statusText    = "—";
    private String rawDescription = "";
    private final List<byte[]> productImages = new ArrayList<>();
    private int currentImageIndex = 0;
    private int    bidCount      = 0;
    private long   secondsLeft   = 0;
    private Timer  countdownTimer;

    // Wallet state
    private double walletBalance = -1; // -1 = chưa tải

    private final DecimalFormat currencyFormat = new DecimalFormat("#,### VNĐ");

    @FXML
    public void initialize() {
        if (btnTheme != null) btnTheme.setText(com.auction.client.util.ThemeManager.getInstance().getToggleIcon());

        loadUserInfo();

        // Đăng ký listener real-time (nếu có)
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) {
            handler.addBidListener(this);
        }

        // Tải số dư ví ngầm
        loadWalletBalance();
    }

    /**
     * Được gọi từ AuctionDetailController trước khi mở popup.
     * Nhận đầy đủ dữ liệu để populate toàn bộ UI ngay khi mở cửa sổ.
     */
    public void setAuctionData(String auctionId, double currentPrice, double minIncrement,
                               String leaderName, double leaderBid,
                               String productName, String sellerId, String statusText,
                               int bidCount, long secondsLeft, JsonArray bidHistory,
                               String description) {
        this.auctionId      = auctionId;
        this.currentPrice   = currentPrice;
        this.minIncrement   = minIncrement;
        this.leaderName     = (leaderName != null && !leaderName.isBlank()) ? leaderName : "—";
        this.leaderBid      = leaderBid;
        this.productName    = (productName  != null && !productName.isBlank()) ? productName : "—";
        this.sellerId       = (sellerId     != null && !sellerId.isBlank())    ? sellerId    : "—";
        this.statusText     = (statusText   != null && !statusText.isBlank())  ? statusText  : "—";
        this.rawDescription = (description  != null) ? description : "";
        this.bidCount       = bidCount;
        this.secondsLeft    = secondsLeft;

        updateLabels();
        updateBidHint();
        loadProductImage();
        populateBidHistory(bidHistory);
        startCountdown();
    }

    /** Parse ảnh từ rawDescription, hỗ trợ [IMGS:b64|b64...] và [IMG:b64] (backward compat) */
    private void loadProductImage() {
        Platform.runLater(() -> {
            productImages.clear();
            currentImageIndex = 0;
            if (rawDescription == null || rawDescription.isEmpty()) {
                hidePaneImages(); return;
            }
            try {
                if (rawDescription.startsWith("[IMGS:")) {
                    int end = rawDescription.indexOf("]");
                    if (end > 6) {
                        String[] parts = rawDescription.substring(6, end).split("[|]");
                        for (String p : parts) {
                            if (!p.isBlank()) productImages.add(Base64.getDecoder().decode(p.trim()));
                        }
                    }
                } else if (rawDescription.startsWith("[IMG:")) {
                    int end = rawDescription.indexOf("]");
                    if (end > 5) productImages.add(Base64.getDecoder().decode(rawDescription.substring(5, end)));
                }
            } catch (Exception ignored) {}

            if (productImages.isEmpty()) { hidePaneImages(); return; }

            // Hiện panel ảnh
            if (paneImages != null) { paneImages.setVisible(true); paneImages.setManaged(true); }
            buildThumbnailStrip();
            showImage(0);
            setupImageZoom();
        });
    }

    private void hidePaneImages() {
        if (paneImages != null) { paneImages.setVisible(false); paneImages.setManaged(false); }
    }

    /** Zoom ảnh chính bằng scroll chuột. Reset zoom khi chuyển ảnh. */
    private void setupImageZoom() {
        if (imgProductMain == null || imgMainPane == null) return;
        // Xóa sự kiện cũ nếu có (tránh đăng ký nhiều lần)
        imgMainPane.setOnScroll(null);
        // Scale transform
        javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(1, 1,
            imgProductMain.getFitWidth() / 2, imgProductMain.getFitHeight() / 2);
        if (imgProductMain.getTransforms().isEmpty()) {
            imgProductMain.getTransforms().add(scale);
        } else {
            imgProductMain.getTransforms().set(0, scale);
        }
        // Scroll để zoom
        imgMainPane.setOnScroll(event -> {
            double delta = event.getDeltaY();
            double factor = (delta > 0) ? 1.12 : (1.0 / 1.12);
            double newScaleX = scale.getX() * factor;
            double newScaleY = scale.getY() * factor;
            // Giới hạn zoom từ 0.5x đến 4x
            newScaleX = Math.max(0.5, Math.min(4.0, newScaleX));
            newScaleY = Math.max(0.5, Math.min(4.0, newScaleY));
            // Pivot theo vị trí chuột trong ảnh
            double pivotX = event.getX();
            double pivotY = event.getY();
            scale.setPivotX(pivotX);
            scale.setPivotY(pivotY);
            scale.setX(newScaleX);
            scale.setY(newScaleY);
            event.consume();
        });
        // Double-click để reset zoom
        imgMainPane.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                scale.setX(1.0); scale.setY(1.0);
                scale.setPivotX(imgProductMain.getFitWidth() / 2);
                scale.setPivotY(imgProductMain.getFitHeight() / 2);
            } else if (event.getClickCount() == 1 && scale.getX() == 1.0) {
                // Single click khi chưa zoom → mở fullscreen
                if (!productImages.isEmpty()) openFullscreenViewer(currentImageIndex);
            }
        });
    }

    /** Reset zoom về 1x khi chuyển ảnh */
    private void resetZoom() {
        if (imgProductMain == null || imgProductMain.getTransforms().isEmpty()) return;
        javafx.scene.transform.Transform t = imgProductMain.getTransforms().get(0);
        if (t instanceof javafx.scene.transform.Scale) {
            javafx.scene.transform.Scale s = (javafx.scene.transform.Scale) t;
            s.setX(1.0); s.setY(1.0);
        }
    }

    private void showImage(int index) {
        if (productImages.isEmpty()) return;
        currentImageIndex = Math.max(0, Math.min(index, productImages.size() - 1));
        try {
            Image img = new Image(new ByteArrayInputStream(productImages.get(currentImageIndex)));
            if (imgProductMain != null) imgProductMain.setImage(img);
        } catch (Exception ignored) {}
        // Reset zoom về 1x khi chuyển ảnh
        resetZoom();
        // Counter
        if (lblImgCounter != null) lblImgCounter.setText((currentImageIndex + 1) + "/" + productImages.size());
        // Arrows visibility - luôn hiện khi có nhiều ảnh
        boolean multi = productImages.size() > 1;
        if (btnImgPrev != null) btnImgPrev.setVisible(multi);
        if (btnImgNext != null) btnImgNext.setVisible(multi);
        // Highlight active thumbnail
        highlightThumb(currentImageIndex);
    }

    private void buildThumbnailStrip() {
        if (hboxThumbs == null) return;
        hboxThumbs.getChildren().clear();
        boolean multi = productImages.size() > 1;
        if (!multi) return; // chỉ 1 ảnh thì không cần strip
        for (int i = 0; i < productImages.size(); i++) {
            final int idx = i;
            StackPane cell = new StackPane();
            cell.setPrefSize(56, 56); cell.setMinSize(56, 56); cell.setMaxSize(56, 56);
            cell.setStyle("-fx-background-color:#111; -fx-background-radius:8;"
                + "-fx-border-color:#44444470; -fx-border-radius:8; -fx-border-width:2; -fx-cursor:hand;"
                + "-fx-clip:true;");
            try {
                Image img = new Image(new ByteArrayInputStream(productImages.get(i)));
                ImageView iv = new ImageView(img);
                // Lấp đầy ô thumbnail (crop thay vì letterbox)
                iv.setFitWidth(56); iv.setFitHeight(56);
                iv.setPreserveRatio(false);
                cell.getChildren().add(iv);
            } catch (Exception ignored) {}
            cell.setOnMouseClicked(e -> showImage(idx));
            hboxThumbs.getChildren().add(cell);
        }
    }

    private void highlightThumb(int active) {
        if (hboxThumbs == null) return;
        for (int i = 0; i < hboxThumbs.getChildren().size(); i++) {
            String border = (i == active) ? "#9b59b6" : "#44444470";
            hboxThumbs.getChildren().get(i).setStyle(
                "-fx-background-color:#111; -fx-background-radius:8;"
                    + "-fx-border-color:" + border + "; -fx-border-radius:8; -fx-border-width:2; -fx-cursor:hand;");
        }
    }

    @FXML
    void handleImgPrev(ActionEvent event) {
        if (productImages.isEmpty()) return;
        showImage((currentImageIndex - 1 + productImages.size()) % productImages.size());
    }

    @FXML
    void handleImgNext(ActionEvent event) {
        if (productImages.isEmpty()) return;
        showImage((currentImageIndex + 1) % productImages.size());
    }

    @FXML
    void handleViewImageFullscreen(javafx.scene.input.MouseEvent event) {
        if (productImages.isEmpty()) return;
        openFullscreenViewer(currentImageIndex);
    }

    private void openFullscreenViewer(int startIndex) {
        Stage viewer = new Stage();
        viewer.initStyle(StageStyle.UNDECORATED);
        viewer.initModality(Modality.APPLICATION_MODAL);

        // Index holder
        int[] idx = {startIndex};

        // UI elements
        ImageView ivFull = new ImageView();
        ivFull.setPreserveRatio(true);
        ivFull.setFitWidth(1000); ivFull.setFitHeight(700);

        // Counter
        Label counter = new Label();
        counter.setStyle("-fx-text-fill:white; -fx-font-size:14px; -fx-background-color:#00000080;"
            + "-fx-padding:4 12; -fx-background-radius:20;");

        // Buttons
        Button prev = new Button("❮");
        Button next = new Button("❯");
        Button close = new Button("✕");
        String arrowStyle = "-fx-background-color:#00000080; -fx-text-fill:white; -fx-font-size:32px;"
            + "-fx-padding:16 22; -fx-cursor:hand; -fx-background-radius:50; -fx-border-width:0;";
        prev.setStyle(arrowStyle); next.setStyle(arrowStyle);
        close.setStyle("-fx-background-color:#e74c3c; -fx-text-fill:white; -fx-font-size:16px;"
            + "-fx-padding:6 14; -fx-cursor:hand; -fx-background-radius:8; -fx-border-width:0;");

        Runnable updateImg = () -> {
            try {
                Image img = new Image(new ByteArrayInputStream(productImages.get(idx[0])));
                ivFull.setImage(img);
                counter.setText((idx[0]+1) + " / " + productImages.size());
                prev.setVisible(productImages.size() > 1);
                next.setVisible(productImages.size() > 1);
            } catch (Exception ignored) {}
        };
        updateImg.run();

        // Scroll zoom cho fullscreen
        javafx.scene.transform.Scale fsScale = new javafx.scene.transform.Scale(1, 1, 500, 375);
        ivFull.getTransforms().add(fsScale);

        prev.setOnAction(e -> { idx[0] = (idx[0]-1+productImages.size())%productImages.size(); fsScale.setX(1); fsScale.setY(1); updateImg.run(); });
        next.setOnAction(e -> { idx[0] = (idx[0]+1)%productImages.size(); fsScale.setX(1); fsScale.setY(1); updateImg.run(); });
        close.setOnAction(e -> viewer.close());

        // Layout
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
        root.setStyle("-fx-background-color:#0a0a0aee;");
        root.setCenter(ivFull);

        // Close button top-right
        javafx.scene.layout.HBox topBar = new javafx.scene.layout.HBox(close);
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.setStyle("-fx-padding:16 20 0 0;");
        root.setTop(topBar);

        // Arrows left/right
        javafx.scene.layout.HBox navBar = new javafx.scene.layout.HBox();
        navBar.setAlignment(Pos.CENTER);
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        navBar.getChildren().addAll(prev, spacer, next);
        navBar.setStyle("-fx-padding:0 20;");
        root.setBottom(navBar);

        // Counter overlay
        javafx.scene.layout.StackPane center = new javafx.scene.layout.StackPane(ivFull, counter);
        StackPane.setAlignment(counter, Pos.BOTTOM_CENTER);
        counter.setStyle(counter.getStyle() + "-fx-translate-y:-20;");
        root.setCenter(center);

        Scene scene = new Scene(root, 1100, 750);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) viewer.close();
            else if (e.getCode() == javafx.scene.input.KeyCode.LEFT) { idx[0]=(idx[0]-1+productImages.size())%productImages.size(); fsScale.setX(1); fsScale.setY(1); updateImg.run(); }
            else if (e.getCode() == javafx.scene.input.KeyCode.RIGHT) { idx[0]=(idx[0]+1)%productImages.size(); fsScale.setX(1); fsScale.setY(1); updateImg.run(); }
        });
        // Scroll zoom trong fullscreen
        center.setOnScroll(e -> {
            double factor = (e.getDeltaY() > 0) ? 1.12 : (1.0 / 1.12);
            double nx = Math.max(0.5, Math.min(5.0, fsScale.getX() * factor));
            double ny = Math.max(0.5, Math.min(5.0, fsScale.getY() * factor));
            fsScale.setPivotX(e.getX()); fsScale.setPivotY(e.getY());
            fsScale.setX(nx); fsScale.setY(ny);
            e.consume();
        });
        // Double-click để reset zoom
        center.setOnMouseClicked(e -> { if (e.getClickCount() == 2) { fsScale.setX(1); fsScale.setY(1); }});
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        viewer.setScene(scene);
        viewer.show();
    }

    private void updateLabels() {
        Platform.runLater(() -> {
            // Thông tin phiên
            if (lblProductName  != null) lblProductName.setText(productName);
            if (lblSeller       != null) lblSeller.setText(sellerId);
            if (lblStatus       != null) lblStatus.setText(statusText);
            if (lblBidCount     != null) lblBidCount.setText(String.valueOf(bidCount));
            if (lblCurrentPrice != null) lblCurrentPrice.setText(formatCurrency(currentPrice));
            if (lblMinIncrement != null) lblMinIncrement.setText(formatCurrency(minIncrement));
            // Leader card
            if (lblLeaderName != null) lblLeaderName.setText(leaderName);
            if (lblLeaderBid  != null) lblLeaderBid.setText(leaderBid > 0 ? formatCurrency(leaderBid) : "—");
        });
    }

    /** Populate bảng lịch sử đặt giá từ JsonArray (dữ liệu từ server) */
    private void populateBidHistory(JsonArray bids) {
        if (tbBidHistory == null) return;

        Platform.runLater(() -> {
            try {
                ObservableList<JsonObject> rows = FXCollections.observableArrayList();
                if (bids != null) {
                    for (int i = 0; i < bids.size(); i++) {
                        try { rows.add(bids.get(i).getAsJsonObject()); } catch (Exception ignored) {}
                    }
                }

                colBidNo.setCellValueFactory(param -> {
                    int idx = tbBidHistory.getItems().indexOf(param.getValue());
                    return new SimpleStringProperty(String.valueOf(idx + 1));
                });
                colBidder.setCellValueFactory(param -> {
                    JsonObject obj = param.getValue();
                    String name = obj.has("bidderName") ? obj.get("bidderName").getAsString()
                        : obj.has("bidder")     ? obj.get("bidder").getAsString() : "—";
                    return new SimpleStringProperty(name);
                });
                colBidAmount.setCellValueFactory(param -> {
                    JsonObject obj = param.getValue();
                    double amount = obj.has("amount") ? obj.get("amount").getAsDouble() : 0;
                    return new SimpleStringProperty(formatCurrency(amount));
                });
                colBidTime.setCellValueFactory(param -> {
                    JsonObject obj = param.getValue();
                    String ts = obj.has("timestamp") ? obj.get("timestamp").getAsString()
                        : obj.has("time")      ? obj.get("time").getAsString() : "";
                    if (ts.contains("T")) ts = ts.replace("T", " ");
                    if (ts.length() > 19) ts = ts.substring(0, 19);
                    return new SimpleStringProperty(ts);
                });

                tbBidHistory.setItems(rows);
                if (lblHistoryCount != null) lblHistoryCount.setText(String.valueOf(rows.size()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /** Countdown timer trong cửa sổ đặt giá */
    private void startCountdown() {
        if (countdownTimer != null) countdownTimer.cancel();
        if (secondsLeft <= 0) {
            Platform.runLater(() -> { if (lblCountdown != null) lblCountdown.setText("ĐÃ KẾT THÚC"); });
            return;
        }
        countdownTimer = new Timer(true);
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (secondsLeft > 0) {
                    secondsLeft--;
                    long h = secondsLeft / 3600;
                    long m = (secondsLeft % 3600) / 60;
                    long s = secondsLeft % 60;
                    Platform.runLater(() -> {
                        if (lblCountdown != null)
                            lblCountdown.setText(String.format("%02d:%02d:%02d", h, m, s));
                    });
                } else {
                    countdownTimer.cancel();
                    Platform.runLater(() -> {
                        if (lblCountdown != null) lblCountdown.setText("ĐÃ KẾT THÚC");
                        if (btnConfirmBid != null) btnConfirmBid.setDisable(true);
                    });
                }
            }
        }, 0, 1000);
    }

    private void updateBidHint() {
        Platform.runLater(() -> {
            double minAllowed = currentPrice + minIncrement;
            lblBidHint.setText("Tối thiểu: " + formatCurrency(minAllowed));
        });
    }

    // ===== Handlers khớp với FXML =====

    @FXML
    void handleQuickBid1(ActionEvent event) {
        applyQuickSteps(1);
    }

    @FXML
    void handleQuickBid2(ActionEvent event) {
        applyQuickSteps(3);
    }

    @FXML
    void handleQuickBid3(ActionEvent event) {
        applyQuickSteps(5);
    }

    private void applyQuickSteps(int steps) {
        double suggested = currentPrice + minIncrement * steps;
        txtBidAmount.setText(String.valueOf(Math.round(suggested)));
    }

    @FXML
    void handleConfirmBid(ActionEvent event) {
        // === Validate INPUT trên JavaFX thread (an toàn) ===
        String input = txtBidAmount.getText();
        if (input == null || input.trim().isEmpty()) {
            AlertUtil.showWarning("Lỗi nhập liệu", "Vui lòng nhập số tiền muốn đặt!");
            return;
        }

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(input.trim().replace(",", ""));
        } catch (NumberFormatException nfe) {
            AlertUtil.showError("Sai định dạng", "Vui lòng chỉ nhập số, không nhập chữ hay ký tự đặc biệt.");
            return;
        }

        double minAllowed = currentPrice + minIncrement;
        if (bidAmount < minAllowed) {
            AlertUtil.showWarning("Giá quá thấp",
                "Bạn phải đặt ít nhất: " + formatCurrency(minAllowed));
            return;
        }

        // === KIỂM TRA SỐ DƯ VÍ ===
        if (walletBalance >= 0 && walletBalance < bidAmount) {
            // Số dư không đủ → mở ví để nạp thêm
            openWalletInsufficientMode(bidAmount);
            return;
        }

        // Disable nút ngay để tránh double-click
        btnConfirmBid.setDisable(true);
        btnConfirmBid.setText("Đang xử lý...");

        // === Chụp lại các biến cần dùng trong thread (phải final/effectively final) ===
        final String capturedAuctionId = this.auctionId;
        final double capturedBidAmount = bidAmount;

        // === Gửi request trên BACKGROUND THREAD ===
        Thread networkThread = new Thread(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("auctionId", capturedAuctionId);
                payload.put("amount", capturedBidAmount);

                // Gọi blocking send() an toàn trên background thread
                JsonObject response = SocketClient.getInstance().send(
                    Actions.PLACE_BID, payload, JsonObject.class);

                // Quay về JavaFX thread để update UI
                Platform.runLater(() -> {
                    if (response != null) {
                        walletBalance -= capturedBidAmount; // Cập nhật số dư tạm

                        // 1. Hiển thị thông báo thành công ngay dưới form (Không dùng popup Alert gây vướng)
                        if (lblMessage != null) {
                            lblMessage.setText("✅ Đặt giá thành công! Đang chờ đối thủ...");
                            lblMessage.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                            lblMessage.setVisible(true);
                        }

                        // 2. Xóa trắng ô nhập tiền
                        txtBidAmount.clear();

                        // 3. Bật lại nút Xác nhận để họ có thể "chiến" tiếp vòng sau
                        resetConfirmButton();

                        // 4. Tự động trỏ chuột (focus) lại vào ô nhập tiền để sẵn sàng gõ
                        txtBidAmount.requestFocus();

                    } else {
                        AlertUtil.showError("Lỗi", "Không nhận được phản hồi từ server.");
                        resetConfirmButton();
                    }
                });

            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định.";
                Platform.runLater(() -> {
                    // Nếu lỗi là INSUFFICIENT_BALANCE → mở ví
                    if (errorMsg.contains("INSUFFICIENT_BALANCE") || errorMsg.contains("Số dư")) {
                        openWalletInsufficientMode(capturedBidAmount);
                    } else {
                        AlertUtil.showError("Lỗi đặt giá", errorMsg);
                    }
                    resetConfirmButton();
                });
            }
        }, "bid-network-thread");

        networkThread.setDaemon(true);
        networkThread.start();
    }

    /** Mở màn hình ví ở chế độ "số dư không đủ" — trên cùng cửa sổ, không mở Stage mới */
    private void openWalletInsufficientMode(double requiredAmount) {
        try {
            ViewLoader.ViewResult<BidderWalletController> result =
                ViewLoader.loadViewWithController("bidder-wallet.fxml");
            if (result == null) return;

            result.getController().setInsufficientMode(requiredAmount);
            result.getController().setReturnAuctionId(this.auctionId); // ← FIX: biết đường quay về

            Stage stage = (Stage) biddingPane.getScene().getWindow();
            stage.getScene().setRoot(result.getView());
            stage.setTitle("💰 Ví Điện Tử – Nạp Tiền");
        } catch (Exception ex) {
            AlertUtil.showWarning("Số dư không đủ",
                String.format("Số dư ví không đủ để đặt %s.\nVui lòng nạp thêm tiền trước khi đặt giá.",
                    formatCurrency(requiredAmount)));
        }
    }

    /** Tải số dư ví ngầm khi mở màn hình đặt giá */
    private void loadWalletBalance() {
        new Thread(() -> {
            try {
                WalletResponse resp = SocketClient.getInstance()
                    .send(Actions.GET_WALLET, new HashMap<>(), WalletResponse.class);
                if (resp != null) {
                    // Dùng getAvailableBalance() để lấy Số dư khả dụng giống hệt trang Chi tiết
                    walletBalance = resp.getAvailableBalance();

                    Platform.runLater(() -> {
                        // 1. Cập nhật Số dư lên góc phải Header
                        if (lblWalletBalance != null) {
                            lblWalletBalance.setText("Số dư: " + formatCurrency(walletBalance));
                        }

                        // 2. Giữ nguyên logic cập nhật thông báo ở Form đặt giá bên dưới
                        if (lblMessage != null) {
                            lblMessage.setText("Số dư khả dụng: " + formatCurrency(walletBalance));
                            lblMessage.setStyle(walletBalance < currentPrice + minIncrement
                                ? "-fx-text-fill: #e74c3c;"
                                : "-fx-text-fill: #27ae60;");
                            lblMessage.setVisible(true);
                        }
                    });
                }
            } catch (Exception ignored) {
                // Không crash nếu không tải được ví
            }
        }, "wallet-preload-thread").start();
    }

    /** Phục hồi trạng thái nút Xác nhận sau khi thất bại */
    private void resetConfirmButton() {
        if (btnConfirmBid != null) {
            btnConfirmBid.setDisable(false);
            btnConfirmBid.setText("XÁC NHẬN ĐẶT GIÁ");
        }
    }

    @FXML
    void handleSetAutoBid(ActionEvent event) {
        String maxBidInput = txtMaxBid.getText();
        String incrementInput = txtIncrement.getText();

        if (maxBidInput == null || maxBidInput.trim().isEmpty()
            || incrementInput == null || incrementInput.trim().isEmpty()) {
            AlertUtil.showWarning("Thiếu thông tin", "Vui lòng nhập đầy đủ Giá tối đa và Bước giá tự động!");
            return;
        }

        double maxBid;
        double increment;
        try {
            maxBid    = Double.parseDouble(maxBidInput.trim().replace(",", ""));
            increment = Double.parseDouble(incrementInput.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            AlertUtil.showError("Sai định dạng", "Vui lòng chỉ nhập số cho Giá tối đa và Bước giá.");
            return;
        }

        double minAllowed = currentPrice + minIncrement;
        if (maxBid < minAllowed) {
            AlertUtil.showWarning("Giá tối đa quá thấp",
                "Giá tối đa phải ít nhất bằng: " + formatCurrency(minAllowed));
            return;
        }

        if (increment <= 0) {
            AlertUtil.showWarning("Bước giá không hợp lệ", "Bước giá tự động phải lớn hơn 0.");
            return;
        }

        btnSetAutoBid.setDisable(true);
        btnSetAutoBid.setText("Đang kích hoạt...");

        final String capturedAuctionId = this.auctionId;
        final double capturedMaxBid    = maxBid;
        final double capturedIncrement = increment;

        Thread networkThread = new Thread(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("auctionId",       capturedAuctionId);
                payload.put("maxBidAmount",     capturedMaxBid);
                payload.put("incrementAmount",  capturedIncrement);

                JsonObject response = SocketClient.getInstance()
                    .send(Actions.SET_AUTO_BID, payload, JsonObject.class);

                Platform.runLater(() -> {
                    btnSetAutoBid.setDisable(false);
                    btnSetAutoBid.setText("Kích hoạt Auto-Bid");
                    if (response != null) {
                        AlertUtil.showInfo("Auto-Bid", "Đã đăng ký đặt giá tự động thành công!");
                    } else {
                        AlertUtil.showError("Lỗi", "Không nhận được phản hồi từ server.");
                    }
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định.";
                Platform.runLater(() -> {
                    btnSetAutoBid.setDisable(false);
                    btnSetAutoBid.setText("Kích hoạt Auto-Bid");
                    AlertUtil.showError("Lỗi Auto-Bid", msg);
                });
            }
        }, "auto-bid-set-thread");
        networkThread.setDaemon(true);
        networkThread.start();
    }

    @FXML
    void handleCancelAutoBid(ActionEvent event) {
        btnCancelAutoBid.setDisable(true);
        btnCancelAutoBid.setText("Đang huỷ...");

        final String capturedAuctionId = this.auctionId;

        Thread networkThread = new Thread(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("auctionId", capturedAuctionId);

                JsonObject response = SocketClient.getInstance()
                    .send(Actions.CANCEL_AUTO_BID, payload, JsonObject.class);

                Platform.runLater(() -> {
                    btnCancelAutoBid.setDisable(false);
                    btnCancelAutoBid.setText("Huỷ Auto-Bid");
                    if (response != null) {
                        AlertUtil.showInfo("Auto-Bid", "Đã huỷ đặt giá tự động.");
                        txtMaxBid.clear();
                        txtIncrement.clear();
                    } else {
                        AlertUtil.showError("Lỗi", "Không nhận được phản hồi từ server.");
                    }
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định.";
                Platform.runLater(() -> {
                    btnCancelAutoBid.setDisable(false);
                    btnCancelAutoBid.setText("Huỷ Auto-Bid");
                    AlertUtil.showError("Lỗi huỷ Auto-Bid", msg);
                });
            }
        }, "auto-bid-cancel-thread");
        networkThread.setDaemon(true);
        networkThread.start();
    }

    @FXML
    void handleBack(ActionEvent event) {
        closeWindow();
    }

    @FXML
    void handleOpenWallet(ActionEvent event) {
        try {
            ViewLoader.ViewResult<BidderWalletController> result =
                ViewLoader.loadViewWithController("bidder-wallet.fxml");
            if (result == null) return;

            result.getController().setReturnAuctionId(this.auctionId); // ← FIX: biết đường quay về

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(result.getView());
            stage.setTitle("💰 Ví Điện Tử");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể mở ví: " + e.getMessage());
        }
    }

    // ===== Real-time update =====
    @Override
    public void onBidUpdated(JsonObject rawData) {
        try {
            if (rawData == null || !rawData.has("data")) return;
            JsonObject data = rawData.getAsJsonObject("data");
            if (data == null || !data.has("auctionId")) return;

            String pushedAuctionId = data.get("auctionId").getAsString();
            if (this.auctionId == null || !this.auctionId.equals(pushedAuctionId)) return;

            double newPrice   = data.has("amount")        ? data.get("amount").getAsDouble()      : this.currentPrice;
            String bidderName = data.has("bidderName")    ? data.get("bidderName").getAsString()  : "Người dùng";
            String timestamp  = data.has("timestamp")     ? data.get("timestamp").getAsString()   : "";

            if (newPrice >= this.currentPrice) {
                this.currentPrice = newPrice;
                this.leaderName   = bidderName;
                this.leaderBid    = newPrice;
            }
            this.bidCount++;

            // Thêm row mới vào đầu bảng
            JsonObject newRow = new JsonObject();
            newRow.addProperty("bidderName", bidderName);
            newRow.addProperty("amount",     newPrice);
            newRow.addProperty("timestamp",  timestamp);

            final double capturedPrice  = this.currentPrice;
            final String capturedLeader = this.leaderName;
            final int    capturedCount  = this.bidCount;

            Platform.runLater(() -> {
                if (lblCurrentPrice != null) {
                    lblCurrentPrice.setText(formatCurrency(capturedPrice));
                    lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
                if (lblLeaderName != null) lblLeaderName.setText(capturedLeader);
                if (lblLeaderBid  != null) lblLeaderBid.setText(formatCurrency(capturedPrice));
                if (lblBidCount   != null) lblBidCount.setText(String.valueOf(capturedCount));
                updateBidHint();

                // Thêm vào đầu bảng lịch sử
                if (tbBidHistory != null) {
                    try {
                        tbBidHistory.getItems().add(0, newRow);
                        if (lblHistoryCount != null) lblHistoryCount.setText(String.valueOf(tbBidHistory.getItems().size()));
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Helpers =====

    private void loadUserInfo() {
        UserSession session = UserSession.getInstance();
        if (lblUser != null) {
            String name = session.getUsername();
            lblUser.setText(name != null && !name.isEmpty() ? name : "Người dùng");
        }
        if (lblRole != null) {
            String role = session.getRole();
            lblRole.setText(role != null && !role.isEmpty() ? role : "");
        }
    }

    private void closeWindow() {
        // Dừng countdown
        if (countdownTimer != null) { countdownTimer.cancel(); countdownTimer = null; }

        // Gỡ listener
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) { handler.removeBidListener(this); }

        // Quay về auction-detail trên cùng cửa sổ (không đóng Stage)
        try {
            Stage stage = (Stage) (txtBidAmount != null
                ? txtBidAmount.getScene().getWindow()
                : btnConfirmBid.getScene().getWindow());

            ViewLoader.ViewResult<AuctionDetailController> result =
                ViewLoader.loadViewWithController("auction-detail.fxml");

            if (result != null && stage != null) {
                result.getController().initData(auctionId);
                stage.getScene().setRoot(result.getView());
                stage.setTitle("Chi tiết phiên đấu giá");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatCurrency(double value) {
        try {
            long rounded = Math.round(value);
            return String.format("%,d VNĐ", rounded);
        } catch (Exception e) {
            return currencyFormat.format(value);
        }
    }

    // Lấy MessageHandler bằng Reflection (giữ nguyên cách bạn dùng)
    private MessageHandler getMessageHandlerSecurely() {
        try {
            Field field = SocketClient.class.getDeclaredField("messageHandler");
            field.setAccessible(true);
            return (MessageHandler) field.get(SocketClient.getInstance());
        } catch (Exception e) {
            return null;
        }
    }

    @FXML
    private void handleToggleTheme(javafx.event.ActionEvent event) {
        com.auction.client.util.ThemeManager tm =
            com.auction.client.util.ThemeManager.getInstance();
        tm.toggle();
        if (btnTheme != null) btnTheme.setText(tm.getToggleIcon());
    }

}