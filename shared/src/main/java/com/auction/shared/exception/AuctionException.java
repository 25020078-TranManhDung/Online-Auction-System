package com.auction.shared.exception;

/**
 * Lớp ngoại lệ cơ sở (Base Exception) cho toàn bộ hệ thống đấu giá.
 * Thay vì phải bắt (catch) từng lỗi lắt nhắt, Server chỉ cần catch (AuctionException e)
 * là có thể tóm được toàn bộ lỗi nghiệp vụ và lấy e.getMessage() để gửi JSON về cho Client.
 */
public class AuctionException extends RuntimeException {
    public AuctionException(String message) {
        super(message);
    }
}