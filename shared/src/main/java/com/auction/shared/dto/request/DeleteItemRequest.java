package com.auction.shared.dto.request;

import java.io.Serializable;

//Data Transfer Object (DTO) yêu cầu xóa một sản phẩm/phiên đấu giá.

public class DeleteItemRequest implements Serializable {
    private String auctionId; // SỬA: Long -> String

    public DeleteItemRequest() {}

    public DeleteItemRequest(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
}
