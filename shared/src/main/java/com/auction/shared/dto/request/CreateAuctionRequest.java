package com.auction.shared.dto.request;

import com.auction.shared.enums.ItemCategory;
import java.io.Serializable;
import java.time.LocalDateTime;

//DTO đóng gói yêu cầu tạo một phiên đấu giá mới từ phía Seller.
public class CreateAuctionRequest implements Serializable {

    // ID của người bán (Seller) tạo ra phiên đấu giá này
    private Long sellerId;

    // Thông tin cơ bản của sản phẩm
    private String itemName;
    private String description;
    private double startingPrice;

    // Danh mục sản phẩm
    private ItemCategory category;

    // Thời gian bắt đầu và kết thúc đấu giá
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Constructor rỗng cho thư viện JSON!
    public CreateAuctionRequest() {
    }

    // Constructor đầy đủ tham số cho Client dễ khởi tạo
    public CreateAuctionRequest(Long sellerId, String itemName, String description, double startingPrice, ItemCategory category, LocalDateTime startTime, LocalDateTime endTime) {
        this.sellerId = sellerId;
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public void setCategory(ItemCategory category) {
        this.category = category;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
