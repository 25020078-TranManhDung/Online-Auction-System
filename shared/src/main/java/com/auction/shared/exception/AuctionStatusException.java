package com.auction.shared.exception;

/**
 * Ngoại lệ liên quan đến trạng thái của phiên đấu giá (AuctionStatus Enum).
 * Ném ra khi người dùng thao tác trên phiên đấu giá không ở trạng thái phù hợp.
 * Ví dụ: Đặt giá khi phiên đã FINISHED hoặc CANCELED.
 */
public class AuctionStatusException extends AuctionException {
    public AuctionStatusException(String message) {
        super(message);
    }
}