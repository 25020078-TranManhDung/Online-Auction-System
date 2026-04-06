package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra trong quá trình đăng nhập (LoginRequest).
 * Sử dụng khi tên đăng nhập đúng nhưng mật khẩu không chính xác,
 * hoặc thông tin xác thực không hợp lệ.
 */
public class InvalidCredentialsException extends AuctionException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}