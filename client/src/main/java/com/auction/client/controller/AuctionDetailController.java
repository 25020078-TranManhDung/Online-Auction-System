package com.auction.client.controller;

import com.auction.client.network.MessageHandler;
import com.auction.client.network.SocketClient;
import com.auction.client.model.UserSession;
import com.auction.client.observer.AuctionUpdateListener;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ChartUtil;
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
public class AuctionDetailController implements BidUpdateListener, AuctionUpdateListener {

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
    @FXML private Button btnMarkAsPaid;   // FIX #1: nút thanh toán cho winner/admin
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
    private long minIncrementValue;    // FIX: lưu thực để tránh parse lỗi từ label
    private String currentLeaderName = "—";
    private long   currentLeaderBid  = 0;
    private String currentWinnerId   = null;  // FIX #1: lưu winnerId để kiểm tra quyền thanh toán
    // FIX: lưu các field còn thiếu để truyền sang BiddingController
    private String currentProductName = "—";
    private String currentSellerId    = "—";
    private String currentStatus      = "—";
    private int    currentBidCount    = 0;
    private com.google.gson.JsonArray currentBidHistory = null;
    private Timer countdownTimer;

    // ----------------- User info -----------------

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

    // ----------------- Lifecycle -----------------

    @FXML
    public void initialize() {
        loadUserInfo();

        // Initialize chart series early to avoid race with realtime updates
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá theo thời gian");
        if (priceChart != null) {
            priceChart.getData().clear();
            priceChart.getData().add(priceSeries);
        }

        // Default UI state
        if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
        if (btnMarkAsPaid != null) btnMarkAsPaid.setVisible(false);
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

        // Register for realtime bid + auction-status updates
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) {
            handler.addBidListener(this);
            handler.addAuctionListener(this);  // FIX #3: đăng ký nhận AUCTION_CLOSED / PAID
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
            long startPrice = getLongSafe(item, "startPrice",
                getLongSafe(auction, "startingPrice",
                    getLongSafe(auction, "start_price", 0L)));
            long currentPrice = getLongSafe(auction, "currentPrice", getLongSafe(auction, "current_price", startPrice));
            this.currentPriceValue = currentPrice;

            // Basic fields mapping (try multiple key names)
            // Server trả AuctionResponse flat (title, description ở root), không có nested "item"
            // → phải đọc từ auction (root) khi item == null
            this.currentProductName = getSafe(item, "title",
                getSafe(item, "name",
                    getSafe(auction, "title", "—")));
            this.currentSellerId    = getSafe(auction, "sellerId", getSafe(auction, "seller", "—"));
            this.currentStatus      = getSafe(auction, "status", "—");
            this.currentBidCount    = getIntSafe(auction, "bidCount", getIntSafe(data, "bidCount", getIntSafe(auction, "bid_count", 0)));

            lblProductName.setText(this.currentProductName);
            lblSeller.setText(this.currentSellerId);
            lblStatus.setText(this.currentStatus);
            lblStartPrice.setText(formatMoney(startPrice));
            this.minIncrementValue = getLongSafe(auction, "minBidIncrement", getLongSafe(auction, "min_increment", 0L));
            lblMinIncrement.setText(formatMoney(this.minIncrementValue));
            lblCurrentPrice.setText(formatMoney(currentPrice));
            lblBidCount.setText(String.valueOf(this.currentBidCount));
            lblEndTime.setText(getSafe(auction, "endTime", getSafe(auction, "end_time", "—")));
            lblDescription.setText(getSafe(item, "description", getSafe(auction, "description", "—")));

            // Chart: initialize series if needed — dữ liệu sẽ được populate sau khi parse bids
            if (priceChart != null && priceSeries == null) {
                priceSeries = new XYChart.Series<>();
                priceChart.getData().add(priceSeries);
            }
            if (priceSeries != null) {
                priceSeries.getData().clear();
                // FIX BUG JAVAFX: Ép trục X xóa sạch danh mục cũ để nhãn hiển thị đúng vị trí
                if (priceChart.getXAxis() instanceof javafx.scene.chart.CategoryAxis) {
                    ((javafx.scene.chart.CategoryAxis) priceChart.getXAxis()).getCategories().clear();
                }
            }

            // Recent bids -> ListView (server trả về key "recentBids")
            lvBidHistory.getItems().clear();
            JsonArray bids = null;
            if (data.has("recentBids") && data.get("recentBids").isJsonArray()) bids = data.getAsJsonArray("recentBids");
            else if (auction.has("recentBids") && auction.get("recentBids").isJsonArray()) bids = auction.getAsJsonArray("recentBids");
            else if (data.has("bids") && data.get("bids").isJsonArray()) bids = data.getAsJsonArray("bids");
            else if (auction.has("bids") && auction.get("bids").isJsonArray()) bids = auction.getAsJsonArray("bids");

            // Lưu bid history để truyền sang BiddingController (phải gán SAU khi parse xong)
            this.currentBidHistory = bids;

            // Populate chart từ recentBids.
            // Yêu cầu kỹ thuật:
            //  1. Đã ở trên FX thread (gọi từ Platform.runLater) → add trực tiếp, KHÔNG qua
            //     ChartUtil.addDataPoint() để tránh tạo thêm Platform.runLater lồng nhau
            //     (gây race condition nếu onBidUpdated() xen vào giữa các task đang xếp hàng).
            //  2. Pre-limit 10 bid gần nhất ngay tại đây (không để ChartUtil remove lần lượt).
            //  3. recentBids sort DESC: index 0 = bid mới nhất → duyệt ngược để chart
            //     vẽ từ trái sang phải theo thứ tự thời gian (cũ → mới).
            if (priceSeries != null) {
                if (bids != null && bids.size() > 0) {
                    int limit = Math.min(bids.size(), 10); // 10 bid gần nhất
                    for (int i = limit - 1; i >= 0; i--) {
                        try {
                            JsonObject b = bids.get(i).getAsJsonObject();
                            // "timestamp" được serialize bởi JsonUtil thành ISO-8601 string
                            // → ChartUtil.normalizeLabel() cắt lấy HH:mm:ss
                            String raw = getSafe(b, "timestamp",
                                getSafe(b, "time", getSafe(b, "createdAt", "")));
                            String timeLabel = formatTimeOnly(raw); // Gọi hàm vừa tạo thay vì dùng ChartUtil
                            if (timeLabel.equals(raw) && raw.isEmpty()) {
                                timeLabel = "Bid " + (limit - i); // fallback index khi không có timestamp
                            }
                            long amount = getLongSafe(b, "amount", getLongSafe(b, "bidAmount", 0L));
                            priceSeries.getData().add(new XYChart.Data<>(timeLabel, amount));
                        } catch (Exception ignored) {}
                    }
                } else {
                    // Chưa có bid nào → vẽ điểm giá khởi điểm
                    priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", startPrice));
                }
            }

            if (bids != null) {
                for (int i = 0; i < bids.size(); i++) {
                    try {
                        JsonObject b = bids.get(i).getAsJsonObject();
                        String bidder = getSafe(b, "bidderName", getSafe(b, "bidder", "Người dùng"));
                        long amount = getLongSafe(b, "amount", getLongSafe(b, "bidAmount", 0L));
                        String rawTime = getSafe(b, "timestamp", getSafe(b, "time", getSafe(b, "createdAt", "")));
                        String time = ChartUtil.normalizeLabel(rawTime);
                        lvBidHistory.getItems().add(String.format("#%d  %s  — %s  (%s)", i + 1, bidder, formatMoney(amount), time));
                    } catch (Exception ignored) {}
                }
            }
            lblHistoryCount.setText(String.valueOf(lvBidHistory.getItems().size()));
            lblBidCount.setText(lblHistoryCount.getText());

            // Leader info (last bid)
            // Leader: ưu tiên highestBidderName từ AuctionResponse, fallback về bid đầu tiên trong list
            String leaderName = getSafe(auction, "highestBidderName", getSafe(auction, "currentLeader", "—"));
            if (!"—".equals(leaderName) && !leaderName.isEmpty()) {
                this.currentLeaderName = leaderName;
                this.currentLeaderBid  = currentPrice;
                lblLeaderName.setText(leaderName);
                lblLeaderBid.setText(formatMoney(currentPrice));
                if (lblLeaderTime != null) lblLeaderTime.setText("");
            } else if (bids != null && bids.size() > 0) {
                try {
                    JsonObject last = bids.get(0).getAsJsonObject(); // index 0 = giá cao nhất (sort DESC)
                    this.currentLeaderName = getSafe(last, "bidderName", getSafe(last, "bidder", "—"));
                    this.currentLeaderBid  = getLongSafe(last, "amount", 0L);
                    lblLeaderName.setText(this.currentLeaderName);
                    lblLeaderBid.setText(formatMoney(this.currentLeaderBid));
                    if (lblLeaderTime != null) lblLeaderTime.setText(getSafe(last, "timestamp", getSafe(last, "time", "")));
                } catch (Exception ignored) {}
            }

            // Chart info
            lblChartInfo.setText(String.format("Bắt đầu: %s", getSafe(auction, "startTime", getSafe(auction, "start_time", "—"))));

            // Timer + winnerId
            this.secondsRemaining = getLongSafe(data, "timeRemaining", getLongSafe(auction, "timeRemaining", getLongSafe(auction, "time_remaining", 0L)));
            this.currentWinnerId  = getSafe(auction, "winnerId", getSafe(data, "winnerId", null));
            startCountdown();

            // Enable/disable buttons based on status
            boolean isRunning  = "RUNNING".equalsIgnoreCase(lblStatus.getText());
            boolean isFinished = "FINISHED".equalsIgnoreCase(lblStatus.getText());
            btnPlaceBid.setDisable(!isRunning);
            updateMarkAsPaidButton(isFinished);

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
            // Đọc bước giá từ label hiện tại
            // FIX: dùng giá trị đã lưu thay vì parse từ label (tránh lỗi format tiền tệ)
            long minInc = this.minIncrementValue;

            // BUG FIX: Dùng ViewLoader.openInNewWindow() thay vì tự tạo Stage thủ công.
            // ViewLoader tự động load CSS (style.css), tránh lỗi StyleableProperty khi
            // các styleClass trong bidding.fxml không tìm được định nghĩa trong scene.
            BiddingController controller = ViewLoader.openInNewWindow("bidding.fxml", "Tham gia đặt giá");

            if (controller == null) {
                AlertUtil.showError("Lỗi giao diện", "Không thể khởi tạo cửa sổ đặt giá.");
                return;
            }

            // Truyền dữ liệu phiên vào cửa sổ đặt giá
            controller.setAuctionData(
                currentAuctionId,
                currentPriceValue,
                minInc,
                currentLeaderName,
                currentLeaderBid,
                currentProductName,
                currentSellerId,
                currentStatus,
                currentBidCount,
                secondsRemaining,
                currentBidHistory
            );

        } catch (Exception e) {
            // In lỗi chi tiết ra console để debug
            System.err.println("❌ Lỗi mở cửa sổ đặt giá: " + e.getClass().getName() + " — " + e.getMessage());
            e.printStackTrace();
            AlertUtil.showError("Lỗi giao diện", "Không thể mở cửa sổ đặt giá.\nChi tiết: " + e.getMessage());
        }
    }

    @FXML
    void handleBack(ActionEvent event) {
        // 1. Dọn dẹp tài nguyên (Timer và Listener) để tránh rò rỉ bộ nhớ
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        MessageHandler handler = getMessageHandlerByReflection();
        if (handler != null) {
            handler.removeBidListener(this);
            handler.removeAuctionListener(this);  // FIX #3: cleanup
        }

        try {
            // 2. Tải giao diện danh sách dưới dạng một Parent node (không tạo Scene mới)
            // Hàm loadView này đã được chúng ta bổ sung vào ViewLoader trước đó
            Parent listView = ViewLoader.loadView("auction-list.fxml");

            if (listView != null) {
                // 3. Lấy Stage (cửa sổ) hiện tại từ sự kiện click nút
                javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

                // 4. THAY THẾ ROOT: Đây là chìa khóa để giữ nguyên kích thước cửa sổ
                // Chúng ta chỉ thay đổi nội dung bên trong, không thay đổi đối tượng Scene
                stage.getScene().setRoot(listView);

                // Cập nhật lại tiêu đề cửa sổ cho đúng ngữ cảnh
                stage.setTitle("Danh sách phiên đấu giá");
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể quay lại danh sách: " + e.getMessage());
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
                // FIX: dùng "amount" (giá của bid này) thay vì "newCurrentPrice" (đã bị cascade)
                long bidAmount = getLongSafe(data, "amount", getLongSafe(data, "newCurrentPrice", currentPriceValue));
                String bidder  = getSafe(data, "bidderName", getSafe(data, "bidder", "Người dùng"));
                boolean isAuto = data.has("isAutoBid") && data.get("isAutoBid").getAsBoolean();

                // Cập nhật state: chỉ cập nhật currentLeader nếu giá này CAO HƠN giá hiện tại
                if (bidAmount >= this.currentPriceValue) {
                    this.currentPriceValue = bidAmount;
                    this.currentLeaderName = bidder;
                    this.currentLeaderBid  = bidAmount;
                }

                final long   displayPrice  = bidAmount;
                final String displayBidder = bidder;
                final long   leaderPrice   = this.currentPriceValue;
                final String leaderName    = this.currentLeaderName;

                Platform.runLater(() -> {
                    // Cập nhật giá hiện tại (chỉ nếu cao hơn giá đang hiển thị)
                    lblCurrentPrice.setText(formatMoney(leaderPrice));
                    lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

                    // Thêm dòng [MỚI] vào đầu danh sách — mỗi bid là 1 dòng riêng
                    String tag = isAuto ? "[AUTO] " : "[MỚI] ";
                    lvBidHistory.getItems().add(0, tag + displayBidder + ": " + formatMoney(displayPrice));

                    // bidCount tăng 1 mỗi lần có bid
                    this.currentBidCount++;
                    try {
                        lblBidCount.setText(String.valueOf(this.currentBidCount));
                    } catch (Exception ignored) {}

                    // Cập nhật leader card — luôn hiển thị người có giá cao nhất
                    lblLeaderName.setText(leaderName);
                    lblLeaderBid.setText(formatMoney(leaderPrice));
                    if (lblLeaderTime != null) lblLeaderTime.setText("");

                    // Thêm điểm vào biểu đồ
                    if (priceSeries != null) {
                        // BidNotifier gửi timestamp = LocalDateTime.toString() → ISO-8601, length=19
                        // Phải normalize qua ChartUtil.normalizeLabel() để tránh label dài xoay trục X
                        String rawLabel = getSafe(data, "timestamp", getSafe(data, "time", ""));
                        ChartUtil.addDataPoint(priceSeries, formatTimeOnly(rawLabel), displayPrice); // Thay thế ở đây
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ----------------- Mark as Paid -----------------

    /**
     * FIX #4: Gửi MARK_AS_PAID lên server.
     * Chỉ hiển thị khi phiên FINISHED + người dùng là winner hoặc ADMIN.
     */
    @FXML
    void handleMarkAsPaid(ActionEvent event) {
        if (currentAuctionId == null) return;
        boolean confirmed = AlertUtil.showConfirm("Xác nhận thanh toán",
            "Bạn có chắc muốn xác nhận đã thanh toán cho phiên đấu giá này?");
        if (!confirmed) return;

        new Thread(() -> {
            try {
                java.util.Map<String, Object> params = new java.util.HashMap<>();
                params.put("auctionId", currentAuctionId);
                SocketClient.getInstance().send(Actions.MARK_AS_PAID, params, com.google.gson.JsonObject.class);
                Platform.runLater(() -> {
                    lblStatus.setText("PAID");
                    lblStatus.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#8e44ad;");
                    if (btnMarkAsPaid != null) btnMarkAsPaid.setVisible(false);
                    btnPlaceBid.setDisable(true);
                    showMessage("✅ Xác nhận thanh toán thành công!");
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> AlertUtil.showError("Lỗi", "Không thể xác nhận thanh toán: " + e.getMessage()));
            }
        }).start();
    }

    // ----------------- Realtime Auction Status -----------------

    /**
     * FIX #2 + #3: Nhận push AUCTION_CLOSED / AUCTION_STATUS_CHANGED từ server.
     * Cập nhật UI ngay lập tức: disable đặt giá, hiện nút thanh toán nếu là winner.
     */
    @Override
    public void onAuctionStatusChanged(JsonObject eventData) {
        try {
            if (eventData == null) return;
            JsonObject data = eventData.has("data") ? eventData.getAsJsonObject("data") : eventData;
            if (data == null) return;

            // Chỉ xử lý nếu event thuộc phiên này
            String evAuctionId = getSafe(data, "auctionId", "");
            if (!evAuctionId.equals(currentAuctionId)) return;

            String event     = getSafe(eventData, "event", "");
            String newStatus = getSafe(data, "newStatus", getSafe(data, "status", ""));

            if (Actions.AUCTION_CLOSED.equals(event) || "FINISHED".equalsIgnoreCase(newStatus)) {
                // Phiên vừa kết thúc → lưu winnerId từ push
                String wId = getSafe(data, "winnerId", getSafe(data, "winner_id", null));
                if (wId != null) this.currentWinnerId = wId;

                String winnerName  = getSafe(data, "winnerName", getSafe(data, "winner_name", "—"));
                String finalPrice  = formatMoney((long) getLongSafe(data, "finalPrice", getLongSafe(data, "final_price", currentPriceValue)));

                Platform.runLater(() -> {
                    lblStatus.setText("FINISHED");
                    lblStatus.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#e67e22;");
                    btnPlaceBid.setDisable(true);
                    if (countdownTimer != null) { countdownTimer.cancel(); countdownTimer = null; }
                    if (lblTimer != null) lblTimer.setText("ĐÃ KẾT THÚC");
                    lblLeaderName.setText(winnerName);
                    lblLeaderBid.setText(finalPrice);
                    updateMarkAsPaidButton(true);
                    showMessage("🏆 Phiên đấu giá đã kết thúc! Người thắng: " + winnerName + " — " + finalPrice);
                });

            } else if ("PAID".equalsIgnoreCase(newStatus)) {
                Platform.runLater(() -> {
                    lblStatus.setText("PAID");
                    lblStatus.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#8e44ad;");
                    if (btnMarkAsPaid != null) btnMarkAsPaid.setVisible(false);
                    btnPlaceBid.setDisable(true);
                    showMessage("✅ Phiên đấu giá đã được thanh toán.");
                });

            } else if ("CANCELED".equalsIgnoreCase(newStatus)) {
                Platform.runLater(() -> {
                    lblStatus.setText("CANCELED");
                    lblStatus.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#95a5a6;");
                    if (btnMarkAsPaid != null) btnMarkAsPaid.setVisible(false);
                    btnPlaceBid.setDisable(true);
                    showMessage("❌ Phiên đấu giá đã bị hủy.");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper: hiện/ẩn btnMarkAsPaid tùy thuộc phiên FINISHED và quyền người dùng.
     * Chỉ winner hoặc ADMIN mới thấy nút này.
     */
    private void updateMarkAsPaidButton(boolean isFinished) {
        if (btnMarkAsPaid == null) return;
        if (!isFinished) { btnMarkAsPaid.setVisible(false); return; }

        UserSession session = UserSession.getInstance();
        boolean isAdmin  = "ADMIN".equalsIgnoreCase(session.getRole());
        boolean isWinner = session.getUserId() != null && session.getUserId().equals(currentWinnerId);
        btnMarkAsPaid.setVisible(isAdmin || isWinner);
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

    private String formatTimeOnly(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isEmpty()) return "";
        try {
            // Thử parse theo chuẩn ISO-8601 (VD: "2026-05-14T14:47:20")
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(rawTimestamp, java.time.format.DateTimeFormatter.ISO_DATE_TIME);
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e) {
            // Fallback: Cắt thủ công lấy 8 ký tự thời gian nếu parse lỗi (hoặc chuỗi không chuẩn)
            if (rawTimestamp.length() >= 19) {
                return rawTimestamp.substring(11, 19);
            }
            return rawTimestamp;
        }
    }
}