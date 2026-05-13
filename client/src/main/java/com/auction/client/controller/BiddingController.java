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
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class BiddingController implements BidUpdateListener {

    // ===== Root pane =====
    @FXML private AnchorPane biddingPane;

    // ===== Header =====
    @FXML private ImageView imgAvatar;
    @FXML private Label lblUser;
    @FXML private Label lblRole;
    @FXML private Button btnBack;

    // ===== Left column (info) =====
    @FXML private Label lblProductName;
    @FXML private Label lblSeller;
    @FXML private Label lblStatus;
    @FXML private Label lblBidCount;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblCountdown;
    @FXML private Label lblMinIncrement;

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

    // Internal state
    private String auctionId;
    private double currentPrice;
    private double minIncrement;
    private String leaderName    = "—";
    private double leaderBid     = 0;
    private String productName   = "—";
    private String sellerId      = "—";
    private String statusText    = "—";
    private int    bidCount      = 0;
    private long   secondsLeft   = 0;
    private Timer  countdownTimer;

    // Wallet state
    private double walletBalance = -1; // -1 = chưa tải

    private final DecimalFormat currencyFormat = new DecimalFormat("#,### VNĐ");

    @FXML
    public void initialize() {
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
                               int bidCount, long secondsLeft, JsonArray bidHistory) {
        this.auctionId   = auctionId;
        this.currentPrice = currentPrice;
        this.minIncrement = minIncrement;
        this.leaderName   = (leaderName != null && !leaderName.isBlank()) ? leaderName : "—";
        this.leaderBid    = leaderBid;
        this.productName  = (productName  != null && !productName.isBlank()) ? productName : "—";
        this.sellerId     = (sellerId     != null && !sellerId.isBlank())    ? sellerId    : "—";
        this.statusText   = (statusText   != null && !statusText.isBlank())  ? statusText  : "—";
        this.bidCount     = bidCount;
        this.secondsLeft  = secondsLeft;

        updateLabels();
        updateBidHint();
        populateBidHistory(bidHistory);
        startCountdown();
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
                        AlertUtil.showInfo("Chúc mừng", "Đặt giá thành công!");
                        closeWindow();
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
                    walletBalance = resp.getBalance();
                    Platform.runLater(() -> {
                        if (lblMessage != null) {
                            lblMessage.setText("Số dư ví: " + formatCurrency(walletBalance));
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
}