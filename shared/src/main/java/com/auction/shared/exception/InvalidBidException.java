package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra khi có sai sót trong quá trình đặt giá (Bid).
 * Ví dụ: Người bán (Seller) tự đấu giá sản phẩm của chính mình,
 * hoặc giá đặt mới không tuân thủ quy tắc bước giá.
 */
public class InvalidBidException extends AuctionException {
    public InvalidBidException(String message) {
        super(message);
    }
}