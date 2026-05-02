package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.network.MessageHandler;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.observer.AuctionUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellerDashboardController implements BidUpdateListener, AuctionUpdateListener {

    @FXML private TextField txtProductName;
    @FXML private TextArea txtDescription;
    @FXML private TextField txtStartPrice;
    @FXML private TextField txtDuration;

    @FXML private TableView<AuctionResponse> tvSellerItems;
    @FXML private TableColumn<AuctionResponse, String> colName;
    @FXML private TableColumn<AuctionResponse, Double> colPrice;
    @FXML private TableColumn<AuctionResponse, String> colStatus;
    @FXML private TableColumn<AuctionResponse, Void> colAction;

    private final ObservableList<AuctionResponse> sellerAuctions = FXCollections.observableArrayList();

    // Dữ liệu tổng trả về từ Server
    public static class GetAuctionsResponse {
        public List<AuctionResponse> auctions;
        public int total;
    }

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("title"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        setupActionColumn();

        // LẤY MESSAGE HANDLER MÀ KHÔNG SỬA SOCKETCLIENT (Dùng Reflection)
        try {
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.addBidListener(this);
                handler.addAuctionListener(this);
            }
        } catch (Exception e) {
            System.err.println("Cảnh báo: Không thể đăng ký Real-time update do giới hạn truy cập.");
        }

        loadMyAuctions();
    }

    /**
     * Hàm "lách luật": Dùng Reflection để lấy field private 'messageHandler' từ SocketClient
     */
    private MessageHandler getMessageHandlerSecurely() throws Exception {
        Field field = SocketClient.class.getDeclaredField("messageHandler");
        field.setAccessible(true); // Cấp quyền đọc field private
        return (MessageHandler) field.get(SocketClient.getInstance());
    }

    @FXML
    void handleAddItem(ActionEvent event) {
        try {
            String title = txtProductName.getText().trim();
            double startPrice = Double.parseDouble(txtStartPrice.getText());
            int duration = Integer.parseInt(txtDuration.getText());

            Map<String, Object> data = new HashMap<>();
            data.put("title", title);
            data.put("description", txtDescription.getText());
            data.put("startPrice", startPrice);
            data.put("minBidIncrement", startPrice * 0.05);
            data.put("durationMinutes", duration);
            data.put("category", ItemCategory.ELECTRONICS.name());

            AuctionResponse response = SocketClient.getInstance().send(Actions.CREATE_AUCTION, data, AuctionResponse.class);

            if (response != null) {
                AlertUtil.showInfo("Thành công", "Đã đăng đấu giá mới.");
                clearFields();
                loadMyAuctions();
            }
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
            });
        }
    }

    @Override
    public void onAuctionStatusChanged(JsonObject rawData) {
        Platform.runLater(this::loadMyAuctions);
    }

    private void loadMyAuctions() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("status", "ALL");

            // BẮT BẰNG CÁI "HỘP" THAY VÌ MẢNG
            GetAuctionsResponse response = SocketClient.getInstance().send(Actions.GET_AUCTIONS, params, GetAuctionsResponse.class);

            if (response != null && response.auctions != null) {
                String myId = UserSession.getInstance().getUserId();

                // Mọi giao diện (như clear bảng, add data) phải chạy trên JavaFX Application Thread
                Platform.runLater(() -> {
                    sellerAuctions.clear();
                    for (AuctionResponse a : response.auctions) {
                        // Lọc chỉ lấy sản phẩm của người bán này
                        if (myId != null && myId.equals(a.getSellerId())) {
                            sellerAuctions.add(a);
                        }
                    }
                    tvSellerItems.setItems(sellerAuctions);
                });
            }
        } catch (Exception e) {
            System.err.println("Lỗi load danh sách: " + e.getMessage());
            e.printStackTrace(); // Bật cái này lên để lỡ có lỗi nó in đỏ ra console dễ check
        }
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

    @FXML
    void goBack(ActionEvent event) {
        try {
            // 1. Gỡ listener
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.removeBidListener(this);
                handler.removeAuctionListener(this);
            }

            // 2. Xóa phiên đăng nhập (Đăng xuất)
            UserSession.getInstance().cleanUserSession();

            // 3. FIX LỖI LOCATION IS NOT SET: Chỉ truyền mỗi tên file thôi!
            // Vì ViewLoader đã tự động ghép "/com/auction/client/fxml/" vào rồi.
            String loginPath = "login.fxml";

            ViewLoader.load(event, loginPath, "Đăng nhập hệ thống");

        } catch (Exception e) {
            System.err.println("Lỗi khi quay lại: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearFields() {
        txtProductName.clear();
        txtDescription.clear();
        txtStartPrice.clear();
        txtDuration.clear();
    }
}