package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra khi chuỗi Token bảo mật đã quá thời gian sử dụng.
 * Bắt buộc Client phải thực hiện hành động Login lại.
 */
public class TokenExpiredException extends AuctionException {
    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message);
    }
}