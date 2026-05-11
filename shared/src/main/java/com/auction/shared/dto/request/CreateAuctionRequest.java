package com.auction.shared.dto.request;

import com.auction.shared.enums.ItemCategory;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map; // THÊM IMPORT NÀY

public class CreateAuctionRequest implements Serializable {

    private String sellerId;
    private String title;
    private String description;
    private double startingPrice;
    private double minBidIncrement;   // Bước giá tối thiểu từ Client
    private int durationMinutes = 60; // Thời lượng phiên (phút), mặc định 60
    private ItemCategory category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // THÊM CÁI NÀY: Dùng để chứa các thuộc tính riêng (brand, model, artist, mileage...)
    private Map<String, Object> itemAttributes;

    public CreateAuctionRequest() {}

    public CreateAuctionRequest(String sellerId, String title, String description, double startingPrice, ItemCategory category, LocalDateTime startTime, LocalDateTime endTime, Map<String, Object> itemAttributes) {
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
        this.itemAttributes = itemAttributes; // LƯU LẠI
    }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getMinBidIncrement() { return minBidIncrement; }
    public void setMinBidIncrement(double minBidIncrement) { this.minBidIncrement = minBidIncrement; }

    public int getDurationMinutes() { return durationMinutes > 0 ? durationMinutes : 60; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Map<String, Object> getItemAttributes() { return itemAttributes; }
    public void setItemAttributes(Map<String, Object> itemAttributes) { this.itemAttributes = itemAttributes; }
}