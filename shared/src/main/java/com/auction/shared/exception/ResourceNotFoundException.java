package com.auction.shared.exception;

/**
 * Ngoại lệ chung khi truy vấn dữ liệu nhưng không tìm thấy (Not Found).
 * Các trường hợp sử dụng:
 * - Tìm User theo ID không thấy.
 * - Tìm phiên đấu giá (Auction) hoặc sản phẩm (Item) theo ID không tồn tại.
 */
public class ResourceNotFoundException extends AuctionException {
    // Cho phép truyền mã linh hoạt vì theo protocol có thể là USER_NOT_FOUND hoặc AUCTION_NOT_FOUND
    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}