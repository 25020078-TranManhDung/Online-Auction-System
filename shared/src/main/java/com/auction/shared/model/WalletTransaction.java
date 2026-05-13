package com.auction.shared.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một giao dịch ví điện tử.
 * Đặt tại shared/ để cả client và server đều dùng được.
 */
public class WalletTransaction {

    public enum TransactionType {
        TOP_UP,         // Bidder nạp tiền
        BID_DEDUCT,     // Trừ tiền khi đặt giá (Luồng cũ)
        BID_REFUND,     // Hoàn tiền khi bị outbid (Luồng cũ)
        BID_HOLD,       // Tạm giữ tiền khi đặt giá (Luồng mới - Hold Balance)
        BID_RELEASE,    // Hủy tạm giữ khi bị vượt giá (Luồng mới - Hold Balance)
        AUCTION_WIN,    // Log: winner thanh toán xong
        SELLER_RECEIVE, // Seller nhận 95% tiền
        COMMISSION,     // Admin nhận 5% hoa hồng
        WITHDRAW        // Seller rút tiền
    }

    private String id;
    private String userId;
    private TransactionType type;
    private double amount;
    private double balanceAfter;
    private String description;
    private String auctionId;        // Nullable – chỉ có khi liên quan đến phiên đấu giá
    private LocalDateTime createdAt;

    public WalletTransaction() {}

    public WalletTransaction(String id, String userId, TransactionType type,
                             double amount, double balanceAfter,
                             String description, String auctionId) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.auctionId = auctionId;
        this.createdAt = LocalDateTime.now();
    }

    // ──── Getters & Setters ────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(double balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
