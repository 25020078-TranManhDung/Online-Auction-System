package com.auction.shared.dto.response;

import com.auction.shared.enums.AuctionStatus;
import java.io.Serializable;
import java.time.LocalDateTime;

/*
 Data Transfer Object (DTO) chứa thông tin phản hồi về một phiên đấu giá.
 Lớp này được sử dụng để truyền tải dữ liệu từ Server tới Client qua Socket.
 */
public class AuctionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String auctionId;
    private String title;
    private String description;
    private String category;

    // Thông tin về giá (sử dụng wrapper Double để có thể null)
    private Double startingPrice;
    private Double currentPrice;
    private String highestBidderName;

    private String sellerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    public AuctionResponse() {}

    public AuctionResponse(String auctionId, String title, String description, String category,
                           Double startingPrice, Double currentPrice, String highestBidderName,
                           LocalDateTime startTime, LocalDateTime endTime, AuctionStatus status) {
        this.auctionId = auctionId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.highestBidderName = highestBidderName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getTitle() { return title ; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(Double startingPrice) { this.startingPrice = startingPrice; }

    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }

    public String getHighestBidderName() { return highestBidderName; }
    public void setHighestBidderName(String highestBidderName) { this.highestBidderName = highestBidderName; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
}
