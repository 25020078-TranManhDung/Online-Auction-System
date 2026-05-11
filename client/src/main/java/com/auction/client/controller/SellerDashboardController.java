package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.network.MessageHandler;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.observer.AuctionUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellerDashboardController implements BidUpdateListener, AuctionUpdateListener {

    // ===== Form đăng sản phẩm (cột trái) =====
    @FXML private TextField txtProductName;
    @FXML private TextArea txtDescription;
    @FXML private TextField txtStartPrice;
    @FXML private TextField txtMinIncrement; // có trong FXML
    @FXML private TextField txtDuration;
    @FXML private Button btnAddItem;
    @FXML private Label lblFormMessage;

    // ===== Header =====
    @FXML private Label lblUser;
    @FXML private Label lblRole;
    @FXML private Button btnLogout;

    // ===== Thống kê nhanh =====
    @FXML private Label lblTotalItems;
    @FXML private Label lblActiveItems;
    @FXML private Label lblClosedItems;
    @FXML private Label lblTotalRevenue;

    // ===== Danh sách sản phẩm (cột phải) =====
    @FXML private TableView<AuctionResponse> tvSellerItems;
    @FXML private TableColumn<AuctionResponse, String> colName;
    @FXML private TableColumn<AuctionResponse, Double> colPrice;
    @FXML private TableColumn<AuctionResponse, String> colStatus;
    @FXML private TableColumn<AuctionResponse, Void> colAction;

    @FXML private ComboBox<String> cboStatusFilter;
    @FXML private Button btnRefresh;
    @FXML private Label lblItemCount;
    @FXML private Label lblTableMessage;

    // Data
    private final ObservableList<AuctionResponse> sellerAuctions = FXCollections.observableArrayList();
    private FilteredList<AuctionResponse> filteredSellerAuctions;

    // Dữ liệu tổng trả về từ Server (giữ nguyên như cũ)
    public static class GetAuctionsResponse {
        public List<AuctionResponse> auctions;
        public int total;
    }

    @FXML
    public void initialize() {
        // Thiết lập cột
        colName.setCellValueFactory(new PropertyValueFactory<>("title"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        setupActionColumn();

        // Wrap list với FilteredList + SortedList để dễ filter/sort
        filteredSellerAuctions = new FilteredList<>(sellerAuctions, p -> true);
        SortedList<AuctionResponse> sorted = new SortedList<>(filteredSellerAuctions);
        sorted.comparatorProperty().bind(tvSellerItems.comparatorProperty());
        tvSellerItems.setItems(sorted);

        // Populate status filter
        if (cboStatusFilter != null) {
            cboStatusFilter.getItems().clear();
            cboStatusFilter.getItems().addAll("Tất cả", "RUNNING", "CLOSED", "DRAFT");
            cboStatusFilter.setValue("Tất cả");
        }

        // Đăng ký listener real-time bằng Reflection (giữ logic cũ)
        try {
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.addBidListener(this);
                handler.addAuctionListener(this);
            }
        } catch (Exception e) {
            System.err.println("Cảnh báo: Không thể đăng ký Real-time update do giới hạn truy cập.");
        }

        // Load dữ liệu ban đầu
        loadMyAuctions();
    }

    /**
     * Lấy messageHandler từ SocketClient bằng Reflection (giữ nguyên logic cũ)
     */
    private MessageHandler getMessageHandlerSecurely() throws Exception {
        Field field = SocketClient.class.getDeclaredField("messageHandler");
        field.setAccessible(true);
        return (MessageHandler) field.get(SocketClient.getInstance());
    }

    @FXML
    void handleAddItem(ActionEvent event) {
        try {
            String title = txtProductName.getText().trim();
            double startPrice = Double.parseDouble(txtStartPrice.getText().trim());
            // Nếu người dùng nhập min increment thủ công, ưu tiên; nếu rỗng, tính mặc định
            double minIncrement = 0;
            try {
                minIncrement = Double.parseDouble(txtMinIncrement.getText().trim());
            } catch (Exception ex) {
                minIncrement = Math.max(1, Math.round(startPrice * 0.05));
            }
            int duration = Integer.parseInt(txtDuration.getText().trim());

            Map<String, Object> data = new HashMap<>();
            data.put("title", title);
            data.put("description", txtDescription.getText());
            data.put("startingPrice", startPrice);       // Khớp với field CreateAuctionRequest.startingPrice
            data.put("minBidIncrement", minIncrement);   // Khớp với field CreateAuctionRequest.minBidIncrement
            data.put("durationMinutes", duration);       // Khớp với field CreateAuctionRequest.durationMinutes
            data.put("category", ItemCategory.ELECTRONICS.name());

            AuctionResponse response = SocketClient.getInstance().send(Actions.CREATE_AUCTION, data, AuctionResponse.class);

            if (response != null) {
                AlertUtil.showInfo("Thành công", "Đã đăng đấu giá mới.");
                clearFields();
                loadMyAuctions();
            }
        } catch (NumberFormatException nfe) {
            AlertUtil.showError("Lỗi", "Vui lòng nhập đúng định dạng số cho giá và thời lượng.");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", e.getMessage());
        }
    }

    @Override
    public void onBidUpdated(JsonObject rawData) {
        if (rawData.has("data")) {
            JsonObject data = rawData.getAsJsonObject("data");
            String auctionId = data.get("auctionId").getAsString();
            double newPrice = data.get("newCurrentPrice").getAsDouble();

            Platform.runLater(() -> {
                for (AuctionResponse item : sellerAuctions) {
                    if (item.getAuctionId().equals(auctionId)) {
                        item.setCurrentPrice(newPrice);
                        tvSellerItems.refresh();
                        break;
                    }
                }
                updateStats();
            });
        }
    }

    @Override
    public void onAuctionStatusChanged(JsonObject rawData) {
        Platform.runLater(this::loadMyAuctions);
    }

    private void loadMyAuctions() {
        // Giữ nguyên logic gọi server, nhưng đảm bảo cập nhật UI trên FX thread
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");

                GetAuctionsResponse response = SocketClient.getInstance().send(Actions.GET_AUCTIONS, params, GetAuctionsResponse.class);

                if (response != null && response.auctions != null) {
                    String myId = UserSession.getInstance().getUserId();

                    Platform.runLater(() -> {
                        sellerAuctions.clear();
                        for (AuctionResponse a : response.auctions) {
                            if (myId != null && myId.equals(a.getSellerId())) {
                                sellerAuctions.add(a);
                            }
                        }
                        updateStats();
                        lblTableMessage.setVisible(sellerAuctions.isEmpty());
                    });
                } else {
                    Platform.runLater(() -> {
                        sellerAuctions.clear();
                        updateStats();
                        lblTableMessage.setText("Không tìm thấy dữ liệu sản phẩm.");
                        lblTableMessage.setVisible(true);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblTableMessage.setText("Lỗi tải dữ liệu: " + e.getMessage());
                    lblTableMessage.setVisible(true);
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void setupActionColumn() {
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btn = new Button("Đóng");
            {
                btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                btn.setOnAction(e -> handleCloseAuction(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private void handleCloseAuction(AuctionResponse item) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("auctionId", item.getAuctionId());
            SocketClient.getInstance().send(Actions.CLOSE_AUCTION, data, Void.class);
            loadMyAuctions();
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", e.getMessage());
        }
    }

    // ===== Handlers được FXML gọi (đã bổ sung để tránh lỗi resolving) =====
    @FXML
    private void handleStatusFilter(ActionEvent event) {
        applyStatusFilter();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadMyAuctions();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Gỡ listener trước khi thoát
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.removeBidListener(this);
                handler.removeAuctionListener(this);
            }
        } catch (Exception ignored) {}

        // Xóa session và quay về login
        UserSession.getInstance().cleanUserSession();
        try {
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    // ===== Helpers =====
    @FXML
    private void applyStatusFilter() {
        String status = (cboStatusFilter == null || cboStatusFilter.getValue() == null) ? "Tất cả" : cboStatusFilter.getValue();
        filteredSellerAuctions.setPredicate(a -> {
            if (a == null) return false;
            if ("Tất cả".equals(status)) return true;

            // Nếu getStatus() trả về AuctionStatus (enum)
            AuctionStatus st = a.getStatus();
            String s = (st == null) ? "" : st.name(); // name() trả về "RUNNING", "CLOSED", ...
            return s.contains(status.toUpperCase());
        });
        updateStats();
    }


    private void updateStats() {
        int total = sellerAuctions.size();

        long active = sellerAuctions.stream()
            .filter(a -> {
                AuctionStatus st = a.getStatus();
                return st != null && AuctionStatus.RUNNING.equals(st);
            })
            .count();

        long closed = sellerAuctions.stream()
            .filter(a -> {
                AuctionStatus st = a.getStatus();
                return st != null && (AuctionStatus.FINISHED.equals(st) || AuctionStatus.PAID.equals(st));
            })
            .count();

        long revenue = sellerAuctions.stream()
            .filter(a -> a.getCurrentPrice() != null)   // nếu currentPrice là Double
            .mapToLong(a -> Math.round(a.getCurrentPrice()))
            .sum();

        if (lblTotalItems != null) lblTotalItems.setText(String.valueOf(total));
        if (lblActiveItems != null) lblActiveItems.setText(String.valueOf(active));
        if (lblClosedItems != null) lblClosedItems.setText(String.valueOf(closed));
        if (lblTotalRevenue != null) lblTotalRevenue.setText(String.format("%,d VNĐ", revenue));
        if (lblItemCount != null) lblItemCount.setText(String.format("%d sản phẩm", filteredSellerAuctions == null ? total : filteredSellerAuctions.size()));
    }



    private void clearFields() {
        txtProductName.clear();
        txtDescription.clear();
        txtStartPrice.clear();
        txtMinIncrement.clear();
        txtDuration.clear();
        lblFormMessage.setVisible(false);
    }
}