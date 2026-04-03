package com.auction.shared.exception;

/**
 * Ngoại lệ chung khi không tìm thấy tài nguyên trong hệ thống.
 * Sử dụng khi tìm kiếm theo ID (User ID, Auction ID, Item ID) nhưng không có dữ liệu trả về.
 */
public class ResourceNotFoundException extends AuctionException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}