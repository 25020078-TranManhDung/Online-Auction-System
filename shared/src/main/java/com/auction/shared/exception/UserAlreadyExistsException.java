package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra trong quá trình đăng ký (RegisterRequest).
 * Sử dụng khi username hoặc email người dùng nhập vào đã tồn tại trong cơ sở dữ liệu.
 */
public class UserAlreadyExistsException extends AuctionException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}