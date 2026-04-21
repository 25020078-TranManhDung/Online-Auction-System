package com.auction.shared.exception;

/**
 * Ngoại lệ chung khi truy vấn dữ liệu nhưng không tìm thấy (Not Found).
 * Các trường hợp sử dụng:
 * - Tìm User theo ID không thấy.
 * - Tìm phiên đấu giá (Auction) hoặc sản phẩm (Item) theo ID không tồn tại.
 */
public class ResourceNotFoundException extends AuctionException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}