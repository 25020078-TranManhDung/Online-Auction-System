package com.auction.shared.exception;

/**
 * Lỗi nghiệp vụ chuyên biệt: Đặt giá thấp hơn mức tối thiểu (giá hiện hành + bước giá tối thiểu).
 */
public class InsufficientBidException extends AuctionException {
    public InsufficientBidException(String message) {
        super("INSUFFICIENT_BID", message);
    }
}
