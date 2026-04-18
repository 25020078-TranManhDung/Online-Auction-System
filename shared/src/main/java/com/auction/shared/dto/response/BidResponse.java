package com.auction.shared.dto.response;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) đại diện cho phản hồi từ Server sau khi xử lý một yêu cầu đặt giá.
 * Lớp này chứa thông tin về trạng thái thành công/thất bại của lượt đặt giá,
 * cùng với giá hiện tại và người dẫn đầu để Client cập nhật giao diện (Realtime).
 */
public class BidResponse implements Serializable {
    private String auctionId;     // SỬA: Long -> String
    private String bidderId;      // Thêm để client nhận diện
    private String bidderName;
    private double amount;        // SỬA: bidAmount -> amount
    private double newCurrentPrice;
    private LocalDateTime timestamp;

    public BidResponse() {}

    public BidResponse(String auctionId, String bidderId, String bidderName, double amount, double newCurrentPrice) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.amount = amount;
        this.newCurrentPrice = newCurrentPrice;
        this.timestamp = LocalDateTime.now();
    }

    // Getters và Setters...
    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }

    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getNewCurrentPrice() { return newCurrentPrice; }
    public void setNewCurrentPrice(double newCurrentPrice) { this.newCurrentPrice = newCurrentPrice; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}