package com.auction.server.util;

import com.auction.server.config.AppConfig; // Giả định bạn có class này như trong cây thư mục

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility class quản lý Token (In-memory).
 * Tối ưu: Gộp dữ liệu, Lazy Cleanup và Daemon Thread dọn dẹp rác.
 */
public final class TokenUtil {

    // 1. TỐI ƯU: Gộp UserId, Role và Expiry vào chung 1 object để quản lý
    private static class TokenPayload {
        final String userId;
        final String role;
        final LocalDateTime expiry;

        TokenPayload(String userId, String role, LocalDateTime expiry) {
            this.userId = userId;
            this.role = role;
            this.expiry = expiry;
        }
    }

    // Chỉ dùng 1 ConcurrentHashMap duy nhất để tối ưu bộ nhớ và hiệu suất
    private static final Map<String, TokenPayload> tokenStore = new ConcurrentHashMap<>();

    // 2. TỐI ƯU (Nâng cao): Khởi tạo một luồng chạy ngầm (Daemon Thread)
    // để dọn dẹp các token đã hết hạn mỗi 1 giờ, chống Memory Leak.
    static {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Token-Cleanup-Thread");
            t.setDaemon(true); // Thread tự tắt khi server dừng
            return t;
        });
        cleaner.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now();
            tokenStore.entrySet().removeIf(entry -> entry.getValue().expiry.isBefore(now));
        }, 1, 1, TimeUnit.HOURS);
    }

    // Chặn khởi tạo đối tượng
    private TokenUtil() {}

    /**
     * Khớp với hàm generate() được gọi trong UserService của bạn
     */
    public static String generate(String userId, String role) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""); // Gọn gàng hơn

        // Lấy thời gian sống từ AppConfig (Hoặc fix cứng ví dụ 24h = 86400000L)
        long expiryMs = 86400000L; // Tạm dùng số cứng, thay bằng AppConfig.getTokenExpiry() nếu có
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(expiryMs / 1000);

        tokenStore.put(token, new TokenPayload(userId, role, expiry));
        return token;
    }

    public static boolean isValid(String token) {
        TokenPayload payload = tokenStore.get(token);
        if (payload == null) return false;

        if (LocalDateTime.now().isBefore(payload.expiry)) {
            return true;
        } else {
            // 3. TỐI ƯU: Lazy cleanup (Xóa ngay token rác khi vô tình truy cập trúng)
            tokenStore.remove(token);
            return false;
        }
    }

    public static String getUserId(String token) {
        if (!isValid(token)) return null;
        return tokenStore.get(token).userId;
    }

    public static String getRole(String token) {
        if (!isValid(token)) return null;
        return tokenStore.get(token).role;
    }

    public static void invalidate(String token) {
        tokenStore.remove(token);
    }
}