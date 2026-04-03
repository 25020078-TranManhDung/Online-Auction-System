package com.auction.shared.dto.request;

import java.io.Serializable;

//Data Transfer Object (DTO) yêu cầu cập nhật thông tin sản phẩm đấu giá.
public class UpdateItemRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long auctionId;
    private String itemName;
    private String description;
    private double startingPrice;

    public UpdateItemRequest() {}

    public UpdateItemRequest(Long auctionId, String itemName, String description, double startingPrice) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    public Long getAuctionId() { return auctionId; }
    public void setAuctionId(Long auctionId) { this.auctionId = auctionId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
}
