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

    @FXML private javafx.scene.layout.HBox headerUserArea;

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
        if (tbBidHistory != null) tbBidHistory.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (btnTheme != null) btnTheme.setText(com.auction.client.util.ThemeManager.getInstance().getToggleIcon());

        // Bo tròn avatar Leader
        if (imgLeaderAvatar != null) {
            double radius = imgLeaderAvatar.getFitWidth() / 2;
            javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(radius, radius, radius);
            imgLeaderAvatar.setClip(clip);
        }

        loadUserInfo();
        com.auction.client.util.ProfileHeaderUtil.bindHeaderProfile(headerUserArea, imgAvatar);

        // 🌟 [MỚI] Gắn lại sự kiện click mở Lightbox cho khung ảnh ở màn hình Đặt giá
        if (imgMainPane != null) {
            imgMainPane.setCursor(javafx.scene.Cursor.HAND);
            imgMainPane.setOnMouseClicked(e -> {
                handleViewImageFullscreen(e);
            });
        }

        // Đăng ký listener real-time (nếu có)
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) {
            handler.addBidListener(this);
        }

        // Tải số dư ví ngầm
        loadWalletBalance();
    }

    public void setAuctionData(String auctionId, double currentPrice, double minIncrement,
                               String leaderName, double leaderBid,
                               String productName, String sellerId, String statusText,
                               int bidCount, long secondsLeft, com.google.gson.JsonArray bidHistory,
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

        // 🌟 [MỚI BỔ SUNG] Trích xuất và cập nhật Avatar cho người dẫn đầu từ bidHistory
        if (bidHistory != null && bidHistory.size() > 0) {
            try {
                com.google.gson.JsonObject lastBid = bidHistory.get(0).getAsJsonObject();
                String leaderAvatar = null;
                // Kiểm tra xem field "bidderAvatar" có tồn tại và khác null hay không
                if (lastBid.has("bidderAvatar") && !lastBid.get("bidderAvatar").isJsonNull()) {
                    leaderAvatar = lastBid.get("bidderAvatar").getAsString();
                }
                updateLeaderAvatar(leaderAvatar);
            } catch (Exception ignored) {
                System.err.println("[BiddingController] Lỗi khi trích xuất avatar từ bidHistory.");
            }
        } else {
            // Nếu chưa có ai đặt giá, truyền null để load ảnh mặc định
            updateLeaderAvatar(null);
        }
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
            if (imgProductMain != null) imgProductMain.setCursor(javafx.scene.Cursor.HAND);
        });
    }

    private void hidePaneImages() {
        if (paneImages != null) { paneImages.setVisible(false); paneImages.setManaged(false); }
    }

    private void showImage(int index) {
        if (productImages.isEmpty()) return;
        currentImageIndex = Math.max(0, Math.min(index, productImages.size() - 1));
        try {
            Image img = new Image(new ByteArrayInputStream(productImages.get(currentImageIndex)));
            if (imgProductMain != null) imgProductMain.setImage(img);
        } catch (Exception ignored) {}
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

        // 1. Chuyển đổi List<byte[]> sang List<Image> để tương thích với LightboxUtil
        java.util.List<javafx.scene.image.Image> fxImages = new java.util.ArrayList<>();
        for (byte[] bytes : productImages) {
            fxImages.add(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
        }

        // 2. Gọi tiện ích xem ảnh dùng chung
        com.auction.client.util.LightboxUtil.show(fxImages, currentImageIndex);
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
    public void onBidUpdated(com.google.gson.JsonObject rawData) {
        try {
            if (rawData == null || !rawData.has("data")) return;
            com.google.gson.JsonObject data = rawData.getAsJsonObject("data");
            if (data == null || !data.has("auctionId")) return;

            String pushedAuctionId = data.get("auctionId").getAsString();
            if (this.auctionId == null || !this.auctionId.equals(pushedAuctionId)) return;

            double newPrice   = data.has("amount")        ? data.get("amount").getAsDouble()      : this.currentPrice;
            String bidderName = data.has("bidderName")    ? data.get("bidderName").getAsString()  : "Người dùng";
            String timestamp  = data.has("timestamp")     ? data.get("timestamp").getAsString()   : "";

            // 🌟 [MỚI BỔ SUNG 1] Trích xuất avatar từ gói tin Socket do Server đẩy về
            String avatarBase64 = data.has("bidderAvatar") && !data.get("bidderAvatar").isJsonNull()
                ? data.get("bidderAvatar").getAsString() : null;

            boolean isNewLeader = false;
            if (newPrice >= this.currentPrice) {
                this.currentPrice = newPrice;
                this.leaderName   = bidderName;
                this.leaderBid    = newPrice;
                isNewLeader       = true; // Đánh dấu đây là lượt đặt giá lên Top 1
            }
            this.bidCount++;

            // Thêm row mới vào đầu bảng
            com.google.gson.JsonObject newRow = new com.google.gson.JsonObject();
            newRow.addProperty("bidderName", bidderName);
            newRow.addProperty("amount",     newPrice);
            newRow.addProperty("timestamp",  timestamp);

            final double capturedPrice  = this.currentPrice;
            final String capturedLeader = this.leaderName;
            final int    capturedCount  = this.bidCount;

            // 🌟 [MỚI BỔ SUNG 2] Khóa giá trị avatar để luồng giao diện (UI Thread) sử dụng
            final String capturedAvatar = avatarBase64;
            final boolean updateAvatar  = isNewLeader;

            Platform.runLater(() -> {
                if (lblCurrentPrice != null) {
                    lblCurrentPrice.setText(formatCurrency(capturedPrice));
                    lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
                if (lblLeaderName != null) lblLeaderName.setText(capturedLeader);
                if (lblLeaderBid  != null) lblLeaderBid.setText(formatCurrency(capturedPrice));
                if (lblBidCount   != null) lblBidCount.setText(String.valueOf(capturedCount));
                updateBidHint();

                // 🌟 [MỚI BỔ SUNG 3] Gọi hàm update ảnh nếu người này vừa giật top 1 thành công
                if (updateAvatar) {
                    updateLeaderAvatar(capturedAvatar);
                }

                // Thêm vào đầu bảng lịch sử
                if (tbBidHistory != null) {
                    try {
                        tbBidHistory.getItems().add(0, newRow);
                        if (lblHistoryCount != null) lblHistoryCount.setText(String.valueOf(tbBidHistory.getItems().size()));
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            System.err.println("[BiddingController] Lỗi xử lý onBidUpdated: " + e.getMessage());
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

    // ----------------- CẬP NHẬT AVATAR LEADER -----------------
    private void updateLeaderAvatar(String base64Avatar) {
        Platform.runLater(() -> {
            if (imgLeaderAvatar == null) return;
            try {
                String finalAvatar = base64Avatar;

                // Mẹo: Nếu mình đang dẫn đầu thì lấy luôn ảnh nội bộ cho nhanh
                com.auction.client.model.UserSession session = com.auction.client.model.UserSession.getInstance();
                if (lblLeaderName.getText() != null && lblLeaderName.getText().equals(session.getUsername())) {
                    if (session.getAvatarBase64() != null && !session.getAvatarBase64().isBlank()) {
                        finalAvatar = session.getAvatarBase64();
                    }
                }

                if (finalAvatar != null && !finalAvatar.isBlank()) {
                    byte[] imgBytes = java.util.Base64.getDecoder().decode(finalAvatar.trim());
                    imgLeaderAvatar.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imgBytes)));
                } else {
                    imgLeaderAvatar.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png")));
                }
            } catch (Exception e) {
                imgLeaderAvatar.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png")));
            }
        });
    }
}