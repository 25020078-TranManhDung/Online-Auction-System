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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class BiddingController implements BidUpdateListener {

    @FXML private Label lblCurrentPrice;
    @FXML private Label lblMinIncrement;
    @FXML private TextField txtBidInput;

    private String auctionId;
    private double currentPrice;
    private double minIncrement;

    // Trình định dạng tiền tệ (Ví dụ: 1.500.000 VNĐ)
    private final DecimalFormat currencyFormat = new DecimalFormat("#,### VNĐ");

    @FXML
    public void initialize() {
        // Đăng ký nhận sự kiện Real-time qua Reflection
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) {
            handler.addBidListener(this);
        }
    }

    /**
     * Hàm này được gọi từ màn hình Detail để truyền dữ liệu sang Popup Bidding.
     */
    public void setAuctionData(String auctionId, double currentPrice, double minIncrement) {
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.minIncrement = minIncrement;

        updateLabels();
    }

    private void updateLabels() {
        lblCurrentPrice.setText(currencyFormat.format(currentPrice));
        lblMinIncrement.setText(currencyFormat.format(minIncrement));
    }

    @FXML
    void confirmBid(ActionEvent event) {
        try {
            // 1. Lấy dữ liệu và kiểm tra hợp lệ
            String input = txtBidInput.getText().trim();
            if (input.isEmpty()) {
                AlertUtil.showWarning("Lỗi nhập liệu", "Vui lòng nhập số tiền muốn đặt!");
                return;
            }

            double bidAmount = Double.parseDouble(input);

            // 2. Validate phía Client trước (Giúp giảm tải cho Server)
            if (bidAmount < (currentPrice + minIncrement)) {
                AlertUtil.showWarning("Giá quá thấp",
                        "Bạn phải đặt ít nhất: " + currencyFormat.format(currentPrice + minIncrement));
                return;
            }

            // 3. Đóng gói payload theo chuẩn Protocol
            Map<String, Object> data = new HashMap<>();
            data.put("auctionId", this.auctionId);
            data.put("amount", bidAmount);

            // 4. Gửi Request lên Server
            // Theo Protocol, PLACE_BID trả về Object chứa newCurrentPrice, rank, message
            JsonObject response = SocketClient.getInstance().send(Actions.PLACE_BID, data, JsonObject.class);

            if (response != null) {
                String msg = response.has("message") ? response.get("message").getAsString() : "Đặt giá thành công!";
                AlertUtil.showInfo("Chúc mừng", msg);
                closeWindow(); // Đặt thành công thì đóng popup
            }

        } catch (NumberFormatException e) {
            AlertUtil.showError("Sai định dạng", "Vui lòng chỉ nhập số, không nhập chữ hay ký tự đặc biệt.");
        } catch (Exception e) {
            // Hiển thị lỗi từ Server (Ví dụ: INVALID_BID, AUCTION_CLOSED, v.v...)
            AlertUtil.showError("Lỗi đặt giá", e.getMessage());
        }
    }

    @FXML
    void cancelBid(ActionEvent event) {
        closeWindow();
    }

    /**
     * Tự động cập nhật giá hiện tại nếu có người khác bid trong lúc đang mở popup
     */
    @Override
    public void onBidUpdated(JsonObject rawData) {
        if (rawData.has("data")) {
            JsonObject data = rawData.getAsJsonObject("data");
            String pushedAuctionId = data.get("auctionId").getAsString();

            // Nếu đúng là sản phẩm đang xem
            if (this.auctionId != null && this.auctionId.equals(pushedAuctionId)) {
                double newPrice = data.get("newCurrentPrice").getAsDouble();

                Platform.runLater(() -> {
                    this.currentPrice = newPrice;
                    updateLabels();
                    // Có thể nháy màu đỏ Label để gây chú ý
                    lblCurrentPrice.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                });
            }
        }
    }

    private void closeWindow() {
        // Hủy đăng ký listener trước khi đóng để giải phóng bộ nhớ
        MessageHandler handler = getMessageHandlerSecurely();
        if (handler != null) {
            handler.removeBidListener(this);
        }

        // Lấy Stage hiện tại và đóng
        Stage stage = (Stage) txtBidInput.getScene().getWindow();
        stage.close();
    }

    // Helper method lấy MessageHandler bằng Reflection
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