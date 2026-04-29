package com.auction.client.model;

import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import javafx.beans.property.*;

import java.time.LocalDateTime;

/**
 * Lớp bao bọc (Wrapper) dữ liệu phiên đấu giá thành các JavaFX Properties.
 * Giúp tự động cập nhật giao diện (Data Binding) khi có thông tin mới từ Server.
 */
public class AuctionViewModel {

    private final StringProperty auctionId = new SimpleStringProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();
    private final DoubleProperty startingPrice = new SimpleDoubleProperty();

    // Thuộc tính quan trọng nhất: Giá hiện tại và Người giữ giá cao nhất (Sẽ thay đổi liên tục)
    private final DoubleProperty currentPrice = new SimpleDoubleProperty();
    private final StringProperty highestBidderName = new SimpleStringProperty();

    private final ObjectProperty<AuctionStatus> status = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> endTime = new SimpleObjectProperty<>();

    public AuctionViewModel() {
    }

    // Hàm tiện ích để nạp dữ liệu từ DTO của Server vào ViewModel
    public void setFromResponse(AuctionResponse response) {
        if (response == null) return;

        this.auctionId.set(response.getAuctionId());
        this.title.set(response.getTitle());
        this.description.set(response.getDescription());
        this.startingPrice.set(response.getStartingPrice());
        this.currentPrice.set(response.getCurrentPrice());
        this.highestBidderName.set(response.getHighestBidderName() != null ? response.getHighestBidderName() : "Chưa có người đặt giá");
        this.status.set(response.getStatus());
        this.endTime.set(response.getEndTime());
    }

    // --- Các hàm Getter cho Properties (Dùng để Binding vào UI) ---

    public StringProperty titleProperty() { return title; }
    public StringProperty descriptionProperty() { return description; }
    public DoubleProperty currentPriceProperty() { return currentPrice; }
    public StringProperty highestBidderNameProperty() { return highestBidderName; }
    public ObjectProperty<AuctionStatus> statusProperty() { return status; }
    public ObjectProperty<LocalDateTime> endTimeProperty() { return endTime; }

    // --- Các hàm Getter/Setter thông thường ---

    public String getAuctionId() { return auctionId.get(); }
    public double getCurrentPrice() { return currentPrice.get(); }
    public void setCurrentPrice(double price) { this.currentPrice.set(price); }

    public void setHighestBidderName(String name) { this.highestBidderName.set(name); }
    public void setStatus(AuctionStatus newStatus) { this.status.set(newStatus); }
}