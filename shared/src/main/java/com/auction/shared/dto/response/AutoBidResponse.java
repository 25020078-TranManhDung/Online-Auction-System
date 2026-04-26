package com.auction.shared.dto.response;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) phản hồi kết quả cài đặt Đặt giá tự động.
 * Tối ưu hóa: Trả về kèm các chỉ số hiện tại để Client cập nhật UI ngay lập tức.
 */
public class AutoBidResponse implements Serializable {

    // ID phiên bản để đảm bảo tính tương thích trong quá trình Serialization qua Socket
    private static final long serialVersionUID = 1L;

    // Trạng thái cơ bản
    private boolean success;
    private String message;

    // Dữ liệu phục vụ cập nhật Giao diện (UX)
    private String auctionId;
    private double maxBid;         // Mức giá tối đa user vừa cài đặt
    private double currentPrice;   // Giá hiện tại của sản phẩm để refresh màn hình
    private boolean alreadyWinning; // Cờ báo hiệu: User có đang dẫn đầu luôn ngay lúc này không?

    // 1. Constructor rỗng (Bắt buộc cho các thư viện Parse JSON/Byte stream)
    public AutoBidResponse() {}

    // 2. Constructor rút gọn (Thường dùng khi thất bại, chỉ cần báo lỗi)
    public AutoBidResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // 3. Constructor đầy đủ chức năng (Dùng khi cài đặt Auto-bid thành công)
    public AutoBidResponse(boolean success, String message, String auctionId, double maxBid, double currentPrice, boolean alreadyWinning) {
        this.success = success;
        this.message = message;
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.currentPrice = currentPrice;
        this.alreadyWinning = alreadyWinning;
    }

    // =================================================================
    // GETTER VÀ SETTER
    // =================================================================

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public boolean isAlreadyWinning() { return alreadyWinning; }
    public void setAlreadyWinning(boolean alreadyWinning) { this.alreadyWinning = alreadyWinning; }
}