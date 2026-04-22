package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra khi chuỗi Token bị sai định dạng, bị giả mạo, hoặc không thể giải mã.
 */
public class TokenInvalidException extends AuctionException {
    public TokenInvalidException(String message) {
        super("TOKEN_INVALID", message);
    }
}