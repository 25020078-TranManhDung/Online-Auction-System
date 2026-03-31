package com.auction.shared.model;

import com.auction.shared.model.entity.Entity;
import com.auction.shared.model.user.Bidder;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private String auctionId;
    private Bidder bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction(String auctionId, Bidder bidder, double bidAmount, LocalDateTime timestamp) {
        this.auctionId = auctionId;
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public String getAuctionId() {
        return auctionId;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
