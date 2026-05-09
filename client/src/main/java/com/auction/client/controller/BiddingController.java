package com.auction.client.controller;

import com.auction.client.network.MessageHandler;
import com.auction.client.network.SocketClient;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class BiddingController implements BidUpdateListener {

    // ===== Left column (info) =====
    @FXML private Label lblProductName;
    @FXML private Label lblSeller;
    @FXML private Label lblStatus;
    @FXML private Label lblBidCount;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblCountdown;
    @FXML private Label lblMinIncrement;

    // Bid history table
    @FXML private TableView<?> tbBidHistory;
    @FXML private Label lblHistoryCount;

    // ===== Right column (form) =====
    @FXML private TextField txtBidAmount;
    @FXML private Label lblBidHint;
    @FXML private Button btnQuick1;
    @FXML private Button btnQuick2;
    @FXML private Button btnQuick3;
    @FXML private Button btnConfirmBid;
    @FXML private Label lblMessage;

    // ===== Leader card =====
    @FXML private Label lblLeaderName;
    @FXML private Label lblLeaderBid;

    // Internal state
    private String auctionId;
    private double currentPrice;
    private double minIncrement;

    private final DecimalFormat currencyFormat = new DecimalFormat("#,### VNĐ");

    @FXML
    public void initialize() {
        // Đăng ký listener real-time (nếu có)
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) {
            handler.addBidListener(this);
        }
    }

    /**
     * Được gọi từ AuctionDetailController trước khi mở popup.
     */
    public void setAuctionData(String auctionId, double currentPrice, double minIncrement) {
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.minIncrement = minIncrement;

        updateLabels();
        updateBidHint();
    }

    private void updateLabels() {
        Platform.runLater(() -> {
            lblCurrentPrice.setText(formatCurrency(currentPrice));
            lblMinIncrement.setText(formatCurrency(minIncrement));
            lblBidCount.setText(lblBidCount.getText() == null ? "0" : lblBidCount.getText());
            lblLeaderBid.setText(lblLeaderBid.getText() == null ? "—" : lblLeaderBid.getText());
        });
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
        try {
            String input = txtBidAmount.getText();
            if (input == null || input.trim().isEmpty()) {
                AlertUtil.showWarning("Lỗi nhập liệu", "Vui lòng nhập số tiền muốn đặt!");
                return;
            }

            double bidAmount;
            try {
                bidAmount = Double.parseDouble(input.trim());
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

            // Disable nút để tránh click nhiều lần
            btnConfirmBid.setDisable(true);
            btnConfirmBid.setText("Đang xử lý...");

            // Gửi request đồng bộ (theo thiết kế hiện tại)
            Map<String, Object> payload = new HashMap<>();
            payload.put("auctionId", this.auctionId);
            payload.put("amount", bidAmount);

            JsonObject response = SocketClient.getInstance().send(Actions.PLACE_BID, payload, JsonObject.class);

            if (response != null) {
                String msg = response.has("message") ? response.get("message").getAsString() : "Đặt giá thành công!";
                AlertUtil.showInfo("Chúc mừng", msg);
                closeWindow();
            } else {
                AlertUtil.showError("Lỗi", "Không nhận được phản hồi từ server.");
            }
        } catch (Exception e) {
            AlertUtil.showError("Lỗi đặt giá", e.getMessage());
        } finally {
            // Luôn bật lại nút (nếu cửa sổ chưa đóng)
            Platform.runLater(() -> {
                if (btnConfirmBid != null) {
                    btnConfirmBid.setDisable(false);
                    btnConfirmBid.setText("XÁC NHẬN ĐẶT GIÁ");
                }
            });
        }
    }

    @FXML
    void handleBack(ActionEvent event) {
        closeWindow();
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

            double newPrice = data.has("newCurrentPrice") ? data.get("newCurrentPrice").getAsDouble() : this.currentPrice;
            String bidderName = data.has("bidderName") ? data.get("bidderName").getAsString() : "Người dùng";

            this.currentPrice = newPrice;

            Platform.runLater(() -> {
                lblCurrentPrice.setText(formatCurrency(newPrice));
                // Cập nhật leader info nếu có
                lblLeaderName.setText(bidderName);
                lblLeaderBid.setText(formatCurrency(newPrice));
                // Cập nhật hint và input gợi ý
                updateBidHint();
                // Optionally highlight
                lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Helpers =====

    private void closeWindow() {
        // Gỡ listener
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) {
            handler.removeBidListener(this);
        }

        // Đóng Stage
        try {
            Stage stage = (Stage) (txtBidAmount != null ? txtBidAmount.getScene().getWindow() : btnConfirmBid.getScene().getWindow());
            if (stage != null) stage.close();
        } catch (Exception ignored) {}
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
