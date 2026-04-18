package com.auction.shared.dto.request;

import com.auction.shared.enums.ItemCategory;
import java.io.Serializable;
import java.time.LocalDateTime;

//DTO đóng gói yêu cầu tạo một phiên đấu giá mới từ phía Seller.
public class CreateAuctionRequest implements Serializable {

    // ID của người bán (Seller) tạo ra phiên đấu giá này
    private String sellerId;       // SỬA: Long -> String

    // Thông tin cơ bản của sản phẩm
    private String title;          // SỬA: itemName -> title (Đồng bộ với Item Model)
    private String description;
    private double startingPrice;

    // Danh mục sản phẩm
    private ItemCategory category;

    // Thời gian bắt đầu và kết thúc đấu giá
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Constructor rỗng cho thư viện JSON!
    public CreateAuctionRequest() {}

    public CreateAuctionRequest(String sellerId, String title, String description, double startingPrice, ItemCategory category, LocalDateTime startTime, LocalDateTime endTime) {
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
