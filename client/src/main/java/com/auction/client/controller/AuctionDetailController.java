package com.auction.client.controller;

import com.auction.client.network.MessageHandler;
import com.auction.client.network.SocketClient;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ChartUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * AuctionDetailController — hiển thị chi tiết phiên đấu giá.
 * - An toàn với null, log payload để debug.
 * - Khởi tạo chart trong initialize để tránh race condition.
 * - Mapping mềm dẻo (camelCase / snake_case / wrapper object).
 * - Đăng ký/huỷ đăng ký listener realtime qua reflection.
 */
public class AuctionDetailController implements BidUpdateListener {

    // ===== Header / Navigation =====
    @FXML private ImageView imgAvatar;
    @FXML private Label lblUser;
    @FXML private Label lblRole;
    @FXML private Button btnBack;

    // ===== Left column (product + bid) =====
    @FXML private Label lblProductName;
    @FXML private Label lblSeller;
    @FXML private Label lblStatus;
    @FXML private Label lblStartPrice;
    @FXML private Label lblMinIncrement;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimer;
    @FXML private Label lblBidCount;
    @FXML private Label lblEndTime;
    @FXML private Label lblDescription;
    @FXML private Button btnPlaceBid;
    @FXML private Label lblMessage;

    // ===== Middle column (chart) =====
    @FXML private LineChart<String, Number> priceChart;
    @FXML private Label lblChartInfo;
    private XYChart.Series<String, Number> priceSeries;

    // ===== Right column (history) =====
    @FXML private ListView<String> lvBidHistory;
    @FXML private Label lblHistoryCount;
    @FXML private ImageView imgLeaderAvatar;
    @FXML private Label lblLeaderName;
    @FXML private Label lblLeaderBid;
    @FXML private Label lblLeaderTime;

    // ===== Internal state =====
    private String currentAuctionId;
    private long secondsRemaining;
    private long currentPriceValue;
    private Timer countdownTimer;

    // ----------------- Lifecycle -----------------

    @FXML
    public void initialize() {
        // Initialize chart series early to avoid race with realtime updates
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá theo thời gian");
        if (priceChart != null) {
            priceChart.getData().clear();
            priceChart.getData().add(priceSeries);
        }

        // Default UI state
        if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
        if (lblMessage != null) lblMessage.setVisible(false);
        if (lblTimer != null) lblTimer.setText("--:--:--");

        // Load default avatar if resource available
        try (InputStream is = getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png")) {
            if (is != null && imgLeaderAvatar != null) imgLeaderAvatar.setImage(new Image(is));
        } catch (Exception ignored) {}

        // Debug: check message handler presence
        MessageHandler handler = getMessageHandlerByReflection();
        System.out.println("MessageHandler via reflection: " + (handler == null ? "null" : handler.getClass().getName()));
    }

    /**
     * Called by caller (e.g., AuctionListController) after loading FXML.
     */
    public void initData(String auctionId) {
        this.currentAuctionId = auctionId;

        // Register for realtime bid updates
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) {
            handler.addBidListener(this);
        }

        // Load detail from server
        loadAuctionDetail();
    }

    // ----------------- Server interaction -----------------

    private void loadAuctionDetail() {
        if (currentAuctionId == null || currentAuctionId.isEmpty()) {
            showMessage("ID phiên không hợp lệ.");
            return;
        }

        if (lblMessage != null) lblMessage.setVisible(false);

        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("auctionId", currentAuctionId);

                JsonObject data = SocketClient.getInstance().send(Actions.GET_AUCTION_DETAIL, params, JsonObject.class);

                // Debug log
                System.out.println("GET_AUCTION_DETAIL -> " + (data == null ? "null" : data.toString()));

                if (data != null) {
                    Platform.runLater(() -> renderUI(data));
                } else {
                    Platform.runLater(() -> showMessage("Không nhận được dữ liệu phiên đấu giá từ Server."));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showMessage("Lỗi kết nối: " + e.getMessage()));
            }
        }).start();
    }

    // ----------------- Render UI -----------------

    private void renderUI(JsonObject data) {
        try {
            // Debug payload
            System.out.println("renderUI payload: " + (data == null ? "null" : data.toString()));

            // Normalize structure: server có thể trả { auction, item, recentBids, priceHistory, timeRemaining }
            JsonObject auction = data.has("auction") && !data.get("auction").isJsonNull() ? data.getAsJsonObject("auction") : data;
            JsonObject item = null;
            if (data.has("item") && !data.get("item").isJsonNull()) item = data.getAsJsonObject("item");
            else if (auction != null && auction.has("item") && !auction.get("item").isJsonNull()) item = auction.getAsJsonObject("item");

            if (auction == null) {
                showMessage("Dữ liệu phiên đấu giá không hợp lệ.");
                return;
            }

            // Determine startPrice and currentPrice with multiple key fallbacks
            long startPrice = getLongSafe(item, "startPrice", getLongSafe(auction, "startingPrice", getLongSafe(auction, "start_price", 0L)));
            long currentPrice = getLongSafe(auction, "currentPrice", getLongSafe(auction, "current_price", startPrice));
            this.currentPriceValue = currentPrice;

            // Basic fields mapping (try multiple key names)
            lblProductName.setText(getSafe(item, "title", getSafe(item, "name", "—")));
            lblSeller.setText(getSafe(auction, "sellerId", getSafe(auction, "seller", "—")));
            lblStatus.setText(getSafe(auction, "status", "—"));
            lblStartPrice.setText(formatMoney(startPrice));
            lblMinIncrement.setText(formatMoney(getLongSafe(auction, "minBidIncrement", getLongSafe(auction, "min_increment", 0L))));
            lblCurrentPrice.setText(formatMoney(currentPrice));
            lblBidCount.setText(String.valueOf(getIntSafe(auction, "bidCount", getIntSafe(auction, "bid_count", 0))));
            lblEndTime.setText(getSafe(auction, "endTime", getSafe(auction, "end_time", "—")));
            lblDescription.setText(getSafe(item, "description", "—"));

            // Chart: initialize series if needed and populate
            if (priceChart != null && priceSeries == null) {
                priceSeries = new XYChart.Series<>();
                priceChart.getData().add(priceSeries);
            }
            if (priceSeries != null) priceSeries.getData().clear();

            JsonArray history = null;
            if (data.has("priceHistory")) history = data.getAsJsonArray("priceHistory");
            else if (data.has("history")) history = data.getAsJsonArray("history");
            else if (auction.has("priceHistory")) history = auction.getAsJsonArray("priceHistory");

            if (history != null && history.size() > 0) {
                for (int i = 0; i < history.size(); i++) {
                    try {
                        JsonElement el = history.get(i);
                        if (!el.isJsonObject()) continue;
                        JsonObject p = el.getAsJsonObject();
                        String timeLabel = getSafe(p, "time", getSafe(p, "createdAt", String.valueOf(i + 1)));
                        long price = getLongSafe(p, "price", getLongSafe(p, "amount", 0L));
                        ChartUtil.addDataPoint(priceSeries, timeLabel, price);
                    } catch (Exception ignored) {}
                }
            } else {
                // fallback: add current price as single point
                ChartUtil.addDataPoint(priceSeries, "Now", currentPrice);
            }

            // Recent bids -> ListView
            lvBidHistory.getItems().clear();
            JsonArray bids = null;
            if (data.has("recentBids")) bids = data.getAsJsonArray("recentBids");
            else if (data.has("bids")) bids = data.getAsJsonArray("bids");
            else if (auction.has("recentBids")) bids = auction.getAsJsonArray("recentBids");

            if (bids != null) {
                for (int i = 0; i < bids.size(); i++) {
                    try {
                        JsonObject b = bids.get(i).getAsJsonObject();
                        String bidder = getSafe(b, "bidderName", getSafe(b, "bidder", "Người dùng"));
                        long amount = getLongSafe(b, "amount", getLongSafe(b, "bidAmount", 0L));
                        String time = getSafe(b, "time", getSafe(b, "createdAt", ""));
                        lvBidHistory.getItems().add(String.format("#%d  %s  — %s  (%s)", i + 1, bidder, formatMoney(amount), time));
                    } catch (Exception ignored) {}
                }
            }
            lblHistoryCount.setText(String.valueOf(lvBidHistory.getItems().size()));
            lblBidCount.setText(lblHistoryCount.getText());

            // Leader info (last bid)
            if (lvBidHistory.getItems().size() > 0) {
                try {
                    JsonObject last = bids != null && bids.size() > 0 ? bids.get(bids.size() - 1).getAsJsonObject() : null;
                    if (last != null) {
                        lblLeaderName.setText(getSafe(last, "bidderName", getSafe(last, "bidder", "—")));
                        lblLeaderBid.setText(formatMoney(getLongSafe(last, "amount", 0L)));
                        lblLeaderTime.setText(getSafe(last, "time", ""));
                    }
                } catch (Exception ignored) {}
            }

            // Chart info
            lblChartInfo.setText(String.format("Bắt đầu: %s", getSafe(auction, "startTime", getSafe(auction, "start_time", "—"))));

            // Timer
            this.secondsRemaining = getLongSafe(data, "timeRemaining", getLongSafe(auction, "timeRemaining", getLongSafe(auction, "time_remaining", 0L)));
            startCountdown();

            // Enable place bid if RUNNING
            boolean isRunning = "RUNNING".equalsIgnoreCase(lblStatus.getText());
            btnPlaceBid.setDisable(!isRunning);

        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Lỗi khi hiển thị dữ liệu phiên đấu giá.");
        }
    }

    // ----------------- User actions -----------------

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
            long minInc = 0;
            try {
                String minStr = lblMinIncrement.getText().replaceAll("[^0-9]", "");
                minInc = minStr.isEmpty() ? 0 : Long.parseLong(minStr);
            } catch (Exception ignored) {}

            controller.setAuctionData(currentAuctionId, currentPriceValue, minInc);

            Stage stage = new Stage();
            stage.setTitle("Tham gia đặt giá");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi giao diện", "Không thể mở cửa sổ đặt giá.");
        }
    }

    @FXML
    void handleBack(ActionEvent event) {
        // cleanup
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) handler.removeBidListener(this);

        try {
            ViewLoader.load(event, "auction-list.fxml", "Danh sách phiên đấu giá");
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể quay lại danh sách.");
        }
    }

    // ----------------- Realtime updates -----------------

    @Override
    public void onBidUpdated(JsonObject eventData) {
        try {
            if (eventData == null || !eventData.has("data")) return;
            String eventName = eventData.has("event") ? eventData.get("event").getAsString() : "";
            JsonObject data = eventData.getAsJsonObject("data");
            if (data == null || !data.has("auctionId")) return;
            if (!data.get("auctionId").getAsString().equals(currentAuctionId)) return;

            if ("BID_PLACED".equals(eventName) || "BID_UPDATED".equals(eventName)) {
                long newPrice = getLongSafe(data, "newCurrentPrice", getLongSafe(data, "amount", currentPriceValue));
                String bidder = getSafe(data, "bidderName", getSafe(data, "bidder", "Người dùng"));
                this.currentPriceValue = newPrice;

                Platform.runLater(() -> {
                    lblCurrentPrice.setText(formatMoney(newPrice));
                    lvBidHistory.getItems().add(0, "[MỚI] " + bidder + ": " + formatMoney(newPrice));
                    lblBidCount.setText(String.valueOf(lvBidHistory.getItems().size()));
                    lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

                    if (priceSeries != null) {
                        String timeLabel = getSafe(data, "time", String.valueOf(System.currentTimeMillis()));
                        ChartUtil.addDataPoint(priceSeries, timeLabel, newPrice);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ----------------- Countdown -----------------

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
                    Platform.runLater(() -> {
                        if (lblTimer != null) lblTimer.setText(String.format("%02d:%02d:%02d", h, m, s));
                    });
                } else {
                    Platform.runLater(() -> {
                        if (lblTimer != null) lblTimer.setText("ĐÃ KẾT THÚC");
                        if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
                    });
                    countdownTimer.cancel();
                }
            }
        }, 0, 1000);
    }

    // ----------------- Utilities -----------------

    private MessageHandler getMessageHandlerByReflection() {
        try {
            Field field = SocketClient.class.getDeclaredField("messageHandler");
            field.setAccessible(true);
            return (MessageHandler) field.get(SocketClient.getInstance());
        } catch (Exception e) {
            return null;
        }
    }

    private String getSafe(JsonObject obj, String key, String fallback) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : fallback;
    }

    private long getLongSafe(JsonObject obj, String key, long fallback) {
        try {
            return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsLong() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private int getIntSafe(JsonObject obj, String key, int fallback) {
        try {
            return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }

    private void showMessage(String msg) {
        if (lblMessage != null) {
            lblMessage.setText(msg);
            lblMessage.setVisible(true);
        } else {
            AlertUtil.showWarning("Thông báo", msg);
        }
    }
}
