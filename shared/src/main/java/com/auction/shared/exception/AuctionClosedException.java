package com.auction.shared.exception;

/**
 * Lỗi nghiệp vụ chuyên biệt: Đặt giá khi phiên đấu giá đã bị đóng/kết thúc.
 * Trả về đúng mã AUCTION_CLOSED để Client (JavaFX) vô hiệu hóa nút đặt giá.
 */
public class AuctionClosedException extends AuctionException {
    public AuctionClosedException(String message) {
        super("AUCTION_CLOSED", message);
    }
}