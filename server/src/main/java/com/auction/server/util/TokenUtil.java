package com.auction.server.util;

import com.auction.server.config.AppConfig; // Giả định bạn có class này như trong cây thư mục

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility class quản lý Token xác thực (In-Memory Store).
 *
 * [THIẾT KẾ CỐ Ý] Token được lưu trong ConcurrentHashMap trên RAM, KHÔNG persist xuống DB.
 * Hệ quả đã biết: Khi server restart, toàn bộ token bị xóa và user phải đăng nhập lại.
 * Đây là hành vi chấp nhận được cho phạm vi bài tập lớn (demo/học tập).
 *
 * [NÂNG CẤP PRODUCTION] Nếu cần token sống qua restart, thay thế bằng stateless JWT
 * dùng thư viện JJWT (đã có sẵn trong server/pom.xml):
 *   - generate() → Jwts.builder().subject(userId).claim("role", role).signWith(key).compact()
 *   - isValid()   → Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
 * Lợi ích: Không cần lưu trữ, server có thể scale ngang (horizontal scaling).
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

    // FIX: Reverse index — userId → Set<token> để có thể thu hồi toàn bộ token của 1 user
    private static final Map<String, Set<String>> userTokens = new ConcurrentHashMap<>();

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

        // Lấy thời gian sống từ AppConfig (token.expiry.ms trong application.properties)
        long expiryMs = AppConfig.getTokenExpiry();
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(expiryMs / 1000);

        tokenStore.put(token, new TokenPayload(userId, role, expiry));
        // FIX: đăng ký vào reverse index để có thể thu hồi sau khi khoá tài khoản
        // FIX: Dùng ConcurrentHashMap.newKeySet() thay cho HashSet không thread-safe.
        // HashSet bị corrupt nếu generate() và invalidateAllForUserExcept() chạy đồng thời.
        userTokens.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(token);
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
        TokenPayload p = tokenStore.remove(token);
        // FIX: dọn reverse index khi invalidate đơn lẻ (logout thủ công)
        if (p != null) {
            Set<String> tokens = userTokens.get(p.userId);
            if (tokens != null) tokens.remove(token);
        }
    }

    /**
     * FIX: Thu hồi TẤT CẢ token của một user — gọi ngay khi admin khoá tài khoản.
     * Đảm bảo user không thể tiếp tục thao tác dù đang online với session cũ.
     *
     * @param userId ID của user bị khoá
     */
    public static void invalidateAllForUser(String userId) {
        Set<String> tokens = userTokens.remove(userId);
        if (tokens != null) {
            tokens.forEach(tokenStore::remove);
            System.out.println("[TokenUtil] Đã thu hồi " + tokens.size()
                + " token của user: " + userId);
        }
    }

    /**
     * Thu hồi tất cả token cũ của user NGOẠI TRỪ token mới vừa cấp.
     * Dùng khi phát hiện đăng nhập trùng (máy 2 login cùng tài khoản với máy 1):
     *   - Token của máy 1 bị xóa → máy 1 không thể tiếp tục dùng
     *   - Token của máy 2 (exceptToken) được giữ lại → máy 2 dùng bình thường
     *
     * @param userId      ID user
     * @param exceptToken Token mới vừa cấp cho phiên login thứ hai — KHÔNG xóa cái này
     */
    public static void invalidateAllForUserExcept(String userId, String exceptToken) {
        Set<String> tokens = userTokens.get(userId);
        if (tokens == null) return;

        // Xóa token cũ khỏi tokenStore nhưng giữ lại exceptToken
        tokens.removeIf(t -> {
            if (!t.equals(exceptToken)) {
                tokenStore.remove(t);
                return true;  // xóa khỏi set
            }
            return false;     // giữ lại exceptToken trong set
        });
        System.out.println("[TokenUtil] Đã thu hồi token cũ của user: " + userId
            + " (giữ lại token mới)");
    }
}