package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.network.MessageHandler;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AuctionDetailController implements BidUpdateListener {

    @FXML private Label lblProductName;
    @FXML private Label lblDescription;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimer;
    @FXML private TextField txtBidAmount;
    @FXML private ListView<String> lvBidHistory;

    private String currentAuctionId;
    private long secondsRemaining;
    private Timer countdownTimer;

    /**
     * Khởi tạo dữ liệu từ AuctionListController truyền sang
     */
    public void initData(String auctionId) {
        this.currentAuctionId = auctionId;

        // 1. Đăng ký nhận thông báo đẩy (Push) về giá mới qua Observer Pattern
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) {
            handler.addBidListener(this);
        }

        // 2. Lấy thông tin chi tiết ban đầu từ Server
        loadAuctionDetail();
    }

    private void loadAuctionDetail() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("auctionId", currentAuctionId);

                // Theo Protocol: Gửi request GET_AUCTION_DETAIL
                JsonObject response = SocketClient.getInstance().send(Actions.GET_AUCTION_DETAIL, params, JsonObject.class);

                if (response.get("success").getAsBoolean()) {
                    // Cấu trúc Response: { "success": true, "data": { "auction": {...}, "item": {...}, "timeRemaining": 3600 } }
                    JsonObject data = response.getAsJsonObject("data");
                    Platform.runLater(() -> renderUI(data));
                } else {
                    String errorMsg = response.getAsJsonObject("error").get("message").getAsString();
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", errorMsg));
                }
            } catch (Exception e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi kết nối", "Không thể tải chi tiết sản phẩm."));
            }
        }).start();
    }

    private void renderUI(JsonObject data) {
        try {
            JsonObject auction = data.getAsJsonObject("auction");
            JsonObject item = data.getAsJsonObject("item");

            lblProductName.setText(item.get("title").getAsString());
            lblDescription.setText(item.get("description").getAsString());
            lblStartPrice.setText(formatMoney(item.get("startPrice").getAsLong()));
            lblCurrentPrice.setText(formatMoney(auction.get("currentPrice").getAsLong()));

            // Xử lý đếm ngược (timeRemaining tính bằng giây theo Protocol)
            this.secondsRemaining = data.get("timeRemaining").getAsLong();
            startCountdown();

            // Hiển thị lịch sử đặt giá
            lvBidHistory.getItems().clear();
            if (data.has("recentBids") && !data.get("recentBids").isJsonNull()) {
                JsonArray bids = data.getAsJsonArray("recentBids");
                bids.forEach(b -> {
                    JsonObject bid = b.getAsJsonObject();
                    lvBidHistory.getItems().add(bid.get("bidderName").getAsString() + ": " + formatMoney(bid.get("amount").getAsLong()));
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handlePlaceBid(ActionEvent event) {
        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty()) return;

        try {
            long amount = Long.parseLong(amountText.replace(",", ""));

            new Thread(() -> {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("auctionId", currentAuctionId);
                    params.put("amount", amount);

                    // Gửi request PLACE_BID theo Protocol
                    JsonObject response = SocketClient.getInstance().send(Actions.PLACE_BID, params, JsonObject.class);

                    if (response.get("success").getAsBoolean()) {
                        Platform.runLater(() -> {
                            txtBidAmount.clear();
                            // Lưu ý: Không cần cập nhật lblCurrentPrice ở đây vì Server sẽ PUSH onBidUpdated về
                        });
                    } else {
                        String errorMsg = response.getAsJsonObject("error").get("message").getAsString();
                        Platform.runLater(() -> AlertUtil.showError("Lỗi đặt giá", errorMsg));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", "Không thể gửi yêu cầu đặt giá."));
                }
            }).start();
        } catch (NumberFormatException e) {
            AlertUtil.showError("Lỗi nhập liệu", "Vui lòng nhập số tiền hợp lệ.");
        }
    }

    /**
     * Nhận sự kiện Push BID_PLACED từ Server (Real-time)
     */
    @Override
    public void onBidUpdated(JsonObject eventData) {
        try {
            // Theo Protocol: Push { "type": "PUSH", "event": "BID_PLACED", "data": { "auctionId": "...", "newCurrentPrice": 100 } }
            String eventName = eventData.get("event").getAsString();
            JsonObject data = eventData.getAsJsonObject("data");

            if (!data.get("auctionId").getAsString().equals(currentAuctionId)) return;

            if ("BID_PLACED".equals(eventName)) {
                long newPrice = data.get("newCurrentPrice").getAsLong();
                String bidder = data.get("bidderName").getAsString();

                Platform.runLater(() -> {
                    lblCurrentPrice.setText(formatMoney(newPrice));
                    lvBidHistory.getItems().add(0, "[MỚI] " + bidder + ": " + formatMoney(newPrice));
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCountdown() {
        if (countdownTimer != null) countdownTimer.cancel();
        countdownTimer = new Timer(true);
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (secondsRemaining > 0) {
                    secondsRemaining--;
                    long h = secondsRemaining / 3600;
                    long m = (secondsRemaining % 3600) / 60;
                    long s = secondsRemaining % 60;
                    Platform.runLater(() -> lblTimer.setText(String.format("%02d:%02d:%02d", h, m, s)));
                } else {
                    Platform.runLater(() -> lblTimer.setText("ĐÃ KẾT THÚC"));
                    countdownTimer.cancel();
                }
            }
        }, 0, 1000);
    }

    @FXML
    void goBack(ActionEvent event) {
        // Hủy đăng ký listener và dừng timer trước khi thoát
        if (countdownTimer != null) countdownTimer.cancel();
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) handler.removeBidListener(this);

        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/client/fxml/auction-list.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MessageHandler getMessageHandlerByReflection() {
        try {
            Field field = SocketClient.class.getDeclaredField("messageHandler");
            field.setAccessible(true);
            return (MessageHandler) field.get(SocketClient.getInstance());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }
}