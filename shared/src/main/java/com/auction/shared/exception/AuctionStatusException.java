package com.auction.shared.exception;

/**
 * Ngoại lệ liên quan đến vòng đời và trạng thái của phiên đấu giá (AuctionStatus).
 * Các trường hợp sử dụng:
 * - Người dùng cố đặt giá nhưng phiên đấu giá đã kết thúc (FINISHED) hoặc bị hủy (CANCELED).
 * - Phiên đấu giá chưa tới giờ bắt đầu (nếu hệ thống hỗ trợ lên lịch trước).
 */
public class AuctionStatusException extends AuctionException {
    public AuctionStatusException(String message) {
        super("BAD_REQUEST", message);
    }
}