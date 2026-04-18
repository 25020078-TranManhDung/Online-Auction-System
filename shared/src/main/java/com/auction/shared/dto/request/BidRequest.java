package com.auction.shared.dto.request;

import java.io.Serializable;

public class BidRequest implements Serializable {

    // ID của phiên đấu giá mà người dùng đang tham gia
    private String auctionId; // SỬA: Long -> String

    // ID của người đặt giá (Bidder)
    private String bidderId;  // SỬA: Long -> String

    // Mức giá mà họ muốn đặt
    private double amount;    // SỬA: bidAmount -> amount

    public BidRequest() {}

    public BidRequest(String auctionId, String bidderId, double amount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}