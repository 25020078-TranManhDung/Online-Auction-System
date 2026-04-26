package com.auction.shared.model;

import com.auction.shared.model.entity.Entity;
import java.time.LocalDateTime;

/**
 * Thực thể lưu trữ cấu hình Đặt giá tự động (Auto-Bid) của người dùng.
 * Áp dụng tư duy Rich Domain Model để tự đóng gói logic nghiệp vụ.
 */
public class AutoBidSetting extends Entity {

    private String bidderId;
    private String auctionId;
    private double maxBid;
    private double increment;
    private boolean active;
    private LocalDateTime registeredAt;

    // Bắt buộc phải có constructor rỗng để phục vụ Framework (như Jackson, Hibernate/JDBC)
    public AutoBidSetting() {
        super();
    }

    public AutoBidSetting(String id, String bidderId, String auctionId, double maxBid, double increment, boolean active, LocalDateTime registeredAt) {
        super(id);
        this.bidderId = bidderId;
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.active = active;
        // Đảm bảo registeredAt không bao giờ null để lúc lấy ra khỏi DB không bị lỗi
        this.registeredAt = (registeredAt != null) ? registeredAt : LocalDateTime.now();
    }

    // =================================================================
    // TỐI ƯU HÓA: CÁC HÀM LOGIC NGHIỆP VỤ (RICH DOMAIN MODEL)
    // =================================================================

    /**
     * Kiểm tra xem cấu hình này còn khả năng đặt giá tiếp hay không.
     * @param currentPrice Giá hiện tại của phiên đấu giá
     * @return true nếu (Giá hiện tại + Bước giá) <= Ngân sách tối đa, và setting đang active
     */
    public boolean canBid(double currentPrice) {
        return this.active && (currentPrice + this.increment <= this.maxBid);
    }

    /**
     * Tính toán mức giá tiếp theo sẽ đặt.
     * @param currentPrice Giá hiện tại của phiên đấu giá
     * @return Mức giá mới
     */
    public double calculateNextBid(double currentPrice) {
        return currentPrice + this.increment;
    }

    /**
     * Hàm tiện ích giúp tắt cấu hình nhanh chóng khi người dùng hết ngân sách.
     */
    public void deactivate() {
        this.active = false;
    }

    // =================================================================
    // GETTER VÀ SETTER TIÊU CHUẨN
    // =================================================================

    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getIncrement() { return increment; }
    public void setIncrement(double increment) { this.increment = increment; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}