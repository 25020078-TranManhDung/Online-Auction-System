package com.auction.client.controller;

import com.auction.client.network.MessageHandler;
import com.auction.client.network.SocketClient;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ChartUtil; // Import ChartUtil
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart; // Import LineChart
import javafx.scene.chart.XYChart;   // Import XYChart
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
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimer;
    @FXML private ListView<String> lvBidHistory;

    // Thêm biến quản lý biểu đồ
    @FXML private LineChart<String, Number> priceChart;
    private XYChart.Series<String, Number> priceSeries;

    private String currentAuctionId;
    private long secondsRemaining;
    private long currentPriceValue; // Lưu số tiền thật để truyền sang Popup
    private Timer countdownTimer;

    public void initData(String auctionId) {
        this.currentAuctionId = auctionId;

        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) {
            handler.addBidListener(this);
        }

        loadAuctionDetail();
    }

    private void loadAuctionDetail() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("auctionId", currentAuctionId);

                JsonObject data = SocketClient.getInstance().send(Actions.GET_AUCTION_DETAIL, params, JsonObject.class);

                if (data != null) {
                    Platform.runLater(() -> renderUI(data));
                }
            } catch (Exception e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi kết nối", e.getMessage()));
            }
        }).start();
    }

    private void renderUI(JsonObject data) {
        try {
            JsonObject auction = data.getAsJsonObject("auction");
            JsonObject item = data.getAsJsonObject("item");

            this.currentPriceValue = auction.get("currentPrice").getAsLong();
            long startPrice = item.get("startPrice").getAsLong();

            lblProductName.setText(item.get("title").getAsString());
            lblStartPrice.setText(formatMoney(startPrice));
            lblCurrentPrice.setText(formatMoney(this.currentPriceValue));

            // Khởi tạo biểu đồ đường (LineChart)
            this.priceSeries = ChartUtil.initPriceChart(priceChart, startPrice);

            this.secondsRemaining = data.get("timeRemaining").getAsLong();
            startCountdown();

            lvBidHistory.getItems().clear();
            if (data.has("recentBids") && !data.get("recentBids").isJsonNull()) {
                JsonArray bids = data.getAsJsonArray("recentBids");
                bids.forEach(b -> {
                    JsonObject bid = b.getAsJsonObject();
                    lvBidHistory.getItems().add(bid.get("bidderName").getAsString() + ": " + formatMoney(bid.get("amount").getAsLong()));
                });
            }
        } catch (Exception e) {
            System.err.println("Lỗi render UI: " + e.getMessage());
        }
    }

    @FXML
    void handlePlaceBid(ActionEvent event) {
        if (secondsRemaining <= 0) {
            AlertUtil.showWarning("Thông báo", "Phiên đấu giá này đã kết thúc!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/client/fxml/bidding.fxml"));
            Parent root = loader.load();

            BiddingController controller = loader.getController();
            controller.setAuctionData(currentAuctionId, currentPriceValue, 50000);

            Stage stage = new Stage();
            stage.setTitle("Tham gia đặt giá");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            AlertUtil.showError("Lỗi giao diện", "Không thể mở cửa sổ đặt giá.");
        }
    }

    @Override
    public void onBidUpdated(JsonObject eventData) {
        try {
            String eventName = eventData.get("event").getAsString();
            JsonObject data = eventData.getAsJsonObject("data");

            if (!data.get("auctionId").getAsString().equals(currentAuctionId)) return;

            if ("BID_PLACED".equals(eventName)) {
                long newPrice = data.get("newCurrentPrice").getAsLong();
                String bidder = data.get("bidderName").getAsString();

                this.currentPriceValue = newPrice;

                Platform.runLater(() -> {
                    lblCurrentPrice.setText(formatMoney(newPrice));
                    lvBidHistory.getItems().add(0, "[MỚI] " + bidder + ": " + formatMoney(newPrice));
                    lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

                    // Vẽ thêm điểm giá mới lên biểu đồ
                    if (priceSeries != null) {
                        ChartUtil.addDataPoint(priceSeries, newPrice);
                    }
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
        if (countdownTimer != null) countdownTimer.cancel();
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) handler.removeBidListener(this);

        try {
            ViewLoader.load(event, "auction-list.fxml", "Danh sách đấu giá");
        } catch (Exception e) {
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