package com.auction.shared.dto.request;

import java.io.Serializable;

//Data Transfer Object (DTO) yêu cầu xóa một sản phẩm/phiên đấu giá.
public class DeleteItemRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long auctionId;

    public DeleteItemRequest() {}

    public DeleteItemRequest(Long auctionId) {
        this.auctionId = auctionId;
    }

    public Long getAuctionId() { return auctionId; }
    public void setAuctionId(Long auctionId) { this.auctionId = auctionId; }
}
