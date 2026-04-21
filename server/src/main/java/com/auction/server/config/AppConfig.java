package com.auction.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    // Khởi tạo đối tượng Properties dùng chung
    private static final Properties props = new Properties();

    // Khối static chạy 1 lần duy nhất khi khởi động Server
    static {
        try (InputStream is = AppConfig.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {

            // 1. CHỐNG LỖI NULL (NullPointerException)
            // Nếu không tìm thấy file, is sẽ bằng null.
            if (is == null) {
                throw new RuntimeException(" CRITICAL: Không tìm thấy file application.properties trong thư mục resources!");
            }

            // Nếu có file thì mới tiến hành đọc
            props.load(is);
            System.out.println(" Đã tải cấu hình hệ thống thành công.");

        } catch (IOException e) {
            // lỗi đọc file -> sập luôn
            throw new RuntimeException(" CRITICAL: Lỗi I/O khi đọc file cấu hình", e);
        }
    }

    // --- CÁC HÀM GETTER LẤY CẤU HÌNH ---

    // Cấu hình Database (Không có giá trị mặc định, bắt buộc phải điền trong file)
    public static String getDbUrl() {
        return props.getProperty("db.url");
    }

    public static String getDbUser() {
        return props.getProperty("db.user");
    }

    public static String getDbPassword() {
        return props.getProperty("db.password");
    }

    // Cấu hình Connection Pool (Có giá trị mặc định để chống sập)
    public static int getPoolMin() {
        return Integer.parseInt(props.getProperty("db.pool.min", "5"));
    }

    public static int getPoolMax() {
        return Integer.parseInt(props.getProperty("db.pool.max", "20"));
    }

    // Cấu hình Server (Có giá trị mặc định là port 8080)
    public static int getServerPort() {
        return Integer.parseInt(props.getProperty("server.port", "8080"));
    }

    // 2. CHỐNG LỖI PARSE SỐ (NumberFormatException)
    // Cấp giá trị mặc định là 86400000 ms (tương đương 24 giờ) nếu lỡ quên không ghi trong file
    public static long getTokenExpiry() {
        return Long.parseLong(props.getProperty("token.expiry.ms", "86400000"));
    }
}