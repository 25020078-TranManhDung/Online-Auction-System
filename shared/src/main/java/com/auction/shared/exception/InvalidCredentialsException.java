package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra trong quá trình xác thực người dùng (Login).
 * Các trường hợp sử dụng:
 * - Sai tên đăng nhập (Username không tồn tại).
 * - Sai mật khẩu.
 * - Tài khoản bị khóa (nếu hệ thống có chức năng ban user).
 */
public class InvalidCredentialsException extends AuctionException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}