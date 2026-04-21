package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra khi có sai sót trong quá trình đặt giá (Bid).
 * Các trường hợp sử dụng:
 * - Giá đặt mới thấp hơn hoặc bằng (giá hiện hành + bước giá).
 * - Người bán (Seller) cố tình tự đặt giá cho sản phẩm của chính mình.
 */
public class InvalidBidException extends AuctionException {
    public InvalidBidException(String message) {
        super(message);
    }
}