package com.auction.shared.exception;

/**
 * Ngoại lệ ném ra trong quá trình đăng ký tài khoản (Register).
 * Các trường hợp sử dụng:
 * - Username mà người dùng muốn đăng ký đã có người khác sử dụng.
 * - Email đã được liên kết với một tài khoản khác trong hệ thống.
 */
public class UserAlreadyExistsException extends AuctionException {
    public UserAlreadyExistsException(String message) {
        super("BAD_REQUEST", message); // Gán mã lỗi chung cho lỗi dữ liệu đầu vào
    }
}