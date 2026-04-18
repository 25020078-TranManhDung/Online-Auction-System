package com.auction.shared.dto.request;

import java.io.Serializable;

//DTO đóng gói yêu cầu thiết lập chế độ đấu giá tự động (Auto-Bid) từ phía Bidder.
public class AutoBidRequest implements Serializable {
    // ID của phiên đấu giá mà người dùng muốn cài đặt tự động
    private String auctionId; // SỬA: Long -> String

    // ID của người đặt giá (Bidder)
    private String bidderId;  // SỬA: Long -> String

    // Mức giá TỐI ĐA mà người dùng sẵn sàng trả cho sản phẩm này
    private double maxBidAmount;

    // Bước giá tự động (số tiền hệ thống sẽ tự động cộng thêm mỗi khi có người khác trả cao hơn)
    private double incrementAmount;

    // Constructor rỗng cho thư viện JSON!
    public AutoBidRequest() {}

    public AutoBidRequest(String auctionId, String bidderId, double maxBidAmount, double incrementAmount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxBidAmount = maxBidAmount;
        this.incrementAmount = incrementAmount;
    }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }

    public double getMaxBidAmount() { return maxBidAmount; }
    public void setMaxBidAmount(double maxBidAmount) { this.maxBidAmount = maxBidAmount; }

    public double getIncrementAmount() { return incrementAmount; }
    public void setIncrementAmount(double incrementAmount) { this.incrementAmount = incrementAmount; }
}
