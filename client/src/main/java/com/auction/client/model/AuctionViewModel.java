package com.auction.client.model;

import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import javafx.beans.property.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ViewModel cho một phiên đấu giá, dùng JavaFX Properties để binding UI.
 * - Dùng ObjectProperty<Double> cho giá để cho phép giá có thể null (chưa có lượt đặt).
 * - Cung cấp phương thức setFromResponse(AuctionResponse) an toàn với null.
 */
public class AuctionViewModel {

    private final StringProperty auctionId = new SimpleStringProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();
    private final StringProperty category = new SimpleStringProperty();
    private final StringProperty sellerId = new SimpleStringProperty();

    // Dùng ObjectProperty<Double> để có thể biểu diễn giá null (chưa có lượt đặt)
    private final ObjectProperty<Double> startingPrice = new SimpleObjectProperty<>();
    private final ObjectProperty<Double> currentPrice = new SimpleObjectProperty<>();
    private final StringProperty highestBidderName = new SimpleStringProperty();

    private final ObjectProperty<AuctionStatus> status = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> startTime = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> endTime = new SimpleObjectProperty<>();
    private final IntegerProperty bidCount = new SimpleIntegerProperty(0);

    public AuctionViewModel() { }

    /**
     * Nạp dữ liệu an toàn từ DTO server.
     * Không unbox trực tiếp Double -> double để tránh NPE.
     */
    public void setFromResponse(AuctionResponse response) {
        if (response == null) return;

        this.auctionId.set(safeString(response.getAuctionId()));
        this.title.set(safeString(response.getTitle()));
        this.description.set(safeString(response.getDescription()));
        this.category.set(safeString(response.getCategory()));
        this.sellerId.set(safeString(response.getSellerId()));

        // startingPrice và currentPrice có thể null từ server -> giữ null nếu không có
        this.startingPrice.set(response.getStartingPrice() == null ? null : response.getStartingPrice());
        this.currentPrice.set(response.getCurrentPrice() == null ? null : response.getCurrentPrice());

        this.highestBidderName.set(response.getHighestBidderName() == null ? "Chưa có người đặt giá" : response.getHighestBidderName());
        this.status.set(response.getStatus());
        this.startTime.set(response.getStartTime());
        this.endTime.set(response.getEndTime());

        // Nếu AuctionResponse có trường bidCount (nếu không có, giữ nguyên hoặc 0)
        try {
            // Nếu AuctionResponse có getter getBidCount (int/Integer), dùng reflection an toàn
            // Nếu không có, bạn có thể set bidCount từ nơi khác khi nhận dữ liệu chi tiết
            // (để tránh phụ thuộc, ta không ép buộc ở đây)
        } catch (Exception ignored) { }
    }

    // --- Property getters (dùng cho binding) ---
    public StringProperty auctionIdProperty() { return auctionId; }
    public StringProperty titleProperty() { return title; }
    public StringProperty descriptionProperty() { return description; }
    public StringProperty categoryProperty() { return category; }
    public StringProperty sellerIdProperty() { return sellerId; }

    public ObjectProperty<Double> startingPriceProperty() { return startingPrice; }
    public ObjectProperty<Double> currentPriceProperty() { return currentPrice; }
    public StringProperty highestBidderNameProperty() { return highestBidderName; }

    public ObjectProperty<AuctionStatus> statusProperty() { return status; }
    public ObjectProperty<LocalDateTime> startTimeProperty() { return startTime; }
    public ObjectProperty<LocalDateTime> endTimeProperty() { return endTime; }
    public IntegerProperty bidCountProperty() { return bidCount; }

    // --- Convenience getters/setters ---
    public String getAuctionId() { return auctionId.get(); }
    public void setAuctionId(String id) { this.auctionId.set(id); }

    public String getTitle() { return title.get(); }
    public void setTitle(String t) { this.title.set(t); }

    public String getDescription() { return description.get(); }
    public void setDescription(String d) { this.description.set(d); }

    public String getCategory() { return category.get(); }
    public void setCategory(String c) { this.category.set(c); }

    public String getSellerId() { return sellerId.get(); }
    public void setSellerId(String s) { this.sellerId.set(s); }

    /**
     * Trả về giá khởi điểm; có thể null nếu server không cung cấp.
     */
    public Double getStartingPrice() { return startingPrice.get(); }
    public void setStartingPrice(Double p) { this.startingPrice.set(p); }

    /**
     * Trả về giá hiện tại; có thể null nếu chưa có lượt đặt.
     */
    public Double getCurrentPrice() { return currentPrice.get(); }
    public void setCurrentPrice(Double p) { this.currentPrice.set(p); }

    public String getHighestBidderName() { return highestBidderName.get(); }
    public void setHighestBidderName(String name) { this.highestBidderName.set(name); }

    public AuctionStatus getStatus() { return status.get(); }
    public void setStatus(AuctionStatus s) { this.status.set(s); }

    public LocalDateTime getStartTime() { return startTime.get(); }
    public void setStartTime(LocalDateTime t) { this.startTime.set(t); }

    public LocalDateTime getEndTime() { return endTime.get(); }
    public void setEndTime(LocalDateTime t) { this.endTime.set(t); }

    public int getBidCount() { return bidCount.get(); }
    public void setBidCount(int count) { this.bidCount.set(count); }

    // --- Helpers ---
    private String safeString(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return "AuctionViewModel{" +
                "auctionId=" + getAuctionId() +
                ", title=" + getTitle() +
                ", currentPrice=" + getCurrentPrice() +
                ", status=" + getStatus() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuctionViewModel)) return false;
        AuctionViewModel that = (AuctionViewModel) o;
        return Objects.equals(getAuctionId(), that.getAuctionId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAuctionId());
    }
}
