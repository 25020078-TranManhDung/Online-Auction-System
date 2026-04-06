package com.auction.shared.exception;

/**
 * Lớp ngoại lệ cơ sở (Base Exception) cho toàn bộ hệ thống đấu giá.
 * Tất cả các ngoại lệ nghiệp vụ (Business Exceptions) đều phải kế thừa lớp này
 * để Server có thể bắt và xử lý lỗi tập trung.
 */
public class AuctionException extends RuntimeException {
    public AuctionException(String message) {
        super(message);
    }
}