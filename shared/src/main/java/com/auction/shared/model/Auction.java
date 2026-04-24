package com.auction.shared.model;

import com.auction.shared.model.entity.Entity;
import com.auction.shared.enums.AuctionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private String itemId;
    private String sellerId;
    private double startPrice;
    private double currentPrice;
    private double minBidIncrement;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private String currentLeader;
    private int bidCount;

    private List<BidTransaction> bidHistory;

    public Auction() {
        super();
    }
    public Auction(String id, String itemId, String sellerId, double startPrice, double minBidIncrement, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.minBidIncrement = minBidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
        this.bidCount = 0;
        this.bidHistory = new ArrayList<>();
    }

    // Vẫn cần synchronized để tránh lỗi cơ bản khi 2 người cùng bid
    public synchronized boolean addBidTransaction(BidTransaction transaction) {
        if (transaction.getAmount() < this.currentPrice + this.minBidIncrement) {
            return false;
        }

        this.bidHistory.add(transaction);
        this.currentPrice = transaction.getAmount();
        this.currentLeader = transaction.getBidderName();
        this.bidCount++;

        // Đã xóa phần gia hạn thời gian Anti-sniping
        return true;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public double getStartPrice() { return startPrice; }
    public void setStartPrice(double startPrice) { this.startPrice = startPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getMinBidIncrement() { return minBidIncrement; }
    public void setMinBidIncrement(double minBidIncrement) { this.minBidIncrement = minBidIncrement; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public String getCurrentLeader() { return currentLeader; }
    public void setCurrentLeader(String currentLeader) { this.currentLeader = currentLeader; }

    public int getBidCount() { return bidCount; }
    public void setBidCount(int bidCount) { this.bidCount = bidCount; }

    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory) { this.bidHistory = bidHistory; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
}
