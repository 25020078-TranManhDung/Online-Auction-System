package com.auction.shared.exception;

/**
 * Ngoại lệ về phân quyền (Authorization) dựa trên UserRole.
 * Các trường hợp sử dụng:
 * - Client gửi yêu cầu mà không đính kèm Token hoặc Token không hợp lệ.
 * - Bidder (người mua) cố gắng gửi request tạo phiên đấu giá (chức năng của Seller).
 * - User bình thường cố gắng thực hiện lệnh của Admin.
 */
public class UnauthorizedException extends AuctionException {
    public UnauthorizedException(String message) {
        super(message);
    }
}