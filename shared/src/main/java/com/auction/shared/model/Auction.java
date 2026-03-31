package com.auction.shared.model;

import com.auction.shared.model.entity.Entity;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private Item item;
    private Seller seller;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private double currentHighestBid;
    private Bidder currentWinner;

    private List<BidTransaction> bidHistory;

    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        this.item = item;
        this.seller = seller;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
        this.currentHighestBid = item.getStartingPrice();
        this.bidHistory = new ArrayList<>();
    }

    public void addBidTransaction(BidTransaction transaction) {
        this.bidHistory.add(transaction);
        this.currentHighestBid = transaction.getBidAmount();
        this.currentWinner = transaction.getBidder();
    }

    public Item getItem() { return item; }

    public Seller getSeller() { return seller; }

    public LocalDateTime getStartTime() { return startTime; }

    public LocalDateTime getEndTime() { return endTime; }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() { return status; }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public double getCurrentHighestBid() { return currentHighestBid; }

    public Bidder getCurrentWinner() { return currentWinner; }

    public List<BidTransaction> getBidHistory() { return bidHistory; }
}
