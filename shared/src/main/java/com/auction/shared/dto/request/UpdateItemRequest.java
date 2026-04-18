package com.auction.shared.dto.request;

import java.io.Serializable;

//Data Transfer Object (DTO) yêu cầu cập nhật thông tin sản phẩm đấu giá.
public class UpdateItemRequest implements Serializable {
    private String auctionId; // SỬA: Long -> String
    private String title;     // SỬA: itemName -> title
    private String description;
    private double startingPrice;

    public UpdateItemRequest() {}

    public UpdateItemRequest(String auctionId, String title, String description, double startingPrice) {
        this.auctionId = auctionId;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
}
