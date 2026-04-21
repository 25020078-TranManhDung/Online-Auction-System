package com.auction.server.pattern.singleton;



import com.auction.server.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    // 1. Dùng volatile để đảm bảo an toàn tuyệt đối trong môi trường đa luồng (Multi-threading)
    private static volatile DatabaseManager instance;

    // 2. Hồ chứa kết nối siêu tốc (HikariCP)
    private HikariDataSource dataSource;

    // 3. Constructor private để ngăn chặn việc tạo đối tượng bằng từ khóa "new"
    private DatabaseManager() {
        try {
            HikariConfig config = new HikariConfig();

            // Lấy cấu hình từ AppConfig (Đảm bảo tính Đơn nhiệm - Single Responsibility)
            config.setJdbcUrl(AppConfig.getDbUrl());
            config.setUsername(AppConfig.getDbUser());
            config.setPassword(AppConfig.getDbPassword());

            // Thiết lập giới hạn hồ chứa (Chống sập Database trên Cloud)
            config.setMinimumIdle(AppConfig.getPoolMin());
            config.setMaximumPoolSize(AppConfig.getPoolMax());

            // Cấu hình Timeout thông minh
            config.setConnectionTimeout(30000); // Đợi tối đa 30s nếu hồ đang cạn, quá 30s mới báo lỗi
            config.setIdleTimeout(600000);      // Kết nối rảnh rỗi 10 phút sẽ tự trả về DB
            config.setMaxLifetime(1800000);     // 30 phút reset kết nối 1 lần để chống rò rỉ (leak memory)

            // Khởi tạo hồ chứa
            this.dataSource = new HikariDataSource(config);
            System.out.println(" [DatabaseManager] Khởi tạo Connection Pool (HikariCP) thành công!");

            // 4. Shutdown Hook: Trí tuệ nhân tạo dọn rác
            // Tự động đóng hồ chứa một cách êm ái khi ấn nút tắt Server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    System.out.println(" [DatabaseManager] Đã dọn dẹp và ngắt toàn bộ kết nối DB.");
                }
            }));

        } catch (Exception e) {
            System.err.println(" CRITICAL: Lỗi khởi tạo DatabaseManager! Vui lòng kiểm tra file cấu hình.");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // 5. Double-Checked Locking (Chuẩn mực của Singleton Pattern)
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    // 6. Cấp phát kết nối cho các file DAO
    public Connection getConnection() throws SQLException {
        // HikariCP cực kỳ thông minh:
        // - Nếu hồ rỗng và chưa đạt maxSize -> Đẻ thêm.
        // - Nếu hồ đã chạm ngưỡng maxSize -> Bắt luồng (thread) đó đứng chờ (trong 30s).
        return dataSource.getConnection();
    }
}
