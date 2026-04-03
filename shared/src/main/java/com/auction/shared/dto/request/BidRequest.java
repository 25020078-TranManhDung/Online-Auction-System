package com.auction.shared.dto.request;

import java.io.Serializable;

public class BidRequest implements Serializable {
    // ID của phiên đấu giá mà người dùng đang tham gia
    private Long auctionId;

    // ID của người đặt giá (Bidder)
    private Long bidderId;

    // Mức giá mà họ muốn đặt
    private double bidAmount;

    public BidRequest() {
    }

    // Constructor có tham số để Client tiện khởi tạo
    public BidRequest(Long auctionId, Long bidderId, double bidAmount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
    }

    public Long getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(Long auctionId) {
        this.auctionId = auctionId;
    }

    public Long getBidderId() {
        return bidderId;
    }

    public void setBidderId(Long bidderId) {
        this.bidderId = bidderId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
        this.bidAmount = bidAmount;
    }
}
