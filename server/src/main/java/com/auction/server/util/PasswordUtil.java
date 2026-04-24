package com.auction.server.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class xử lý băm và kiểm tra mật khẩu.
 * Sử dụng BCrypt - một trong những tiêu chuẩn bảo mật tốt nhất hiện nay.
 */
public final class PasswordUtil {

    // Chặn khởi tạo đối tượng vì đây là lớp tiện ích (Utility Class)
    private PasswordUtil() {}

    /**
     * Mã hóa mật khẩu người dùng.
     * BCrypt tự động tạo 'salt' ngẫu nhiên và nhúng vào chuỗi hash kết quả.
     *
     * @param plainText Mật khẩu người dùng nhập vào.
     * @return Chuỗi đã được mã hóa an toàn để lưu vào DB.
     */
    public static String hash(String plainText) {
        // Cost factor 12 là độ phức tạp vừa phải (khoảng 200-300ms xử lý trên máy hiện đại)
        // giúp chống lại các cuộc tấn công Brute Force hiệu quả.
        return BCrypt.hashpw(plainText, BCrypt.gensalt(12));
    }

    /**
     * So sánh mật khẩu người dùng nhập vào với chuỗi hash đã lưu trong DB.
     *
     * @param plainText Mật khẩu từ form đăng nhập.
     * @param hashed    Chuỗi hash lấy từ Database.
     * @return true nếu khớp, false nếu không khớp.
     */
    public static boolean verify(String plainText, String hashed) {
        try {
            return BCrypt.checkpw(plainText, hashed);
        } catch (Exception e) {
            // Trường hợp chuỗi hash không đúng định dạng BCrypt
            return false;
        }
    }
}
