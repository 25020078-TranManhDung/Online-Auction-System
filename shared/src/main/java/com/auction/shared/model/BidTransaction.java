package com.auction.shared.model;

import com.auction.shared.model.entity.Entity;
import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private String auctionId;
    private String bidderId;
    private String bidderName;
    private double amount;       // Protocol: "amount"
    private LocalDateTime timestamp;
    private boolean isAutoBid;   // Vẫn để đây cho hợp form JSON, server cứ set false là xong


    public BidTransaction() {
        super();
    }
    public BidTransaction(String id, String auctionId, String bidderId, String bidderName, double amount, LocalDateTime timestamp, boolean isAutoBid) {
        super(id);
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.amount = amount;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.isAutoBid = isAutoBid;
    }

    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isAutoBid() { return isAutoBid; }
    public void setAutoBid(boolean autoBid) { this.isAutoBid = autoBid; }
}
