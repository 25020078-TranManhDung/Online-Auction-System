package com.auction.shared.exception;

/**
 * Ngoại lệ về phân quyền, liên quan đến UserRole Enum.
 * Ném ra khi người dùng cố gắng thực hiện hành động không được phép.
 * Ví dụ: Người dùng vai trò BIDDER nhưng lại gửi yêu cầu CreateAuctionRequest.
 */
public class UnauthorizedException extends AuctionException {
    public UnauthorizedException(String message) {
        super(message);
    }
}