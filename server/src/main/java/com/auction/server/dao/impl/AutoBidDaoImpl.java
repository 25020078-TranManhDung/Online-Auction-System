package com.auction.server.dao.impl;

import com.auction.server.dao.AutoBidDAO;
import com.auction.server.pattern.singleton.DatabaseManager;
import com.auction.shared.model.AutoBidSetting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp thực thi các thao tác CSDL cho AutoBidSetting.
 * Sử dụng Try-with-resources để tự động đóng kết nối (Connection, PreparedStatement, ResultSet)
 * giúp chống rò rỉ bộ nhớ (Memory Leak) tuyệt đối.
 */
public class AutoBidDaoImpl implements AutoBidDAO {

    // Singleton DatabaseManager quản lý Connection Pool
    private final DatabaseManager dbManager = DatabaseManager.getInstance();

    @Override
    public boolean save(AutoBidSetting setting) {
        String sql = "INSERT INTO auto_bid_settings (id, bidder_id, auction_id, max_bid, increment, is_active, registered_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, setting.getId());
            pstmt.setString(2, setting.getBidderId());
            pstmt.setString(3, setting.getAuctionId());
            pstmt.setDouble(4, setting.getMaxBid());
            pstmt.setDouble(5, setting.getIncrement());
            pstmt.setBoolean(6, setting.isActive());
            // Chuyển đổi LocalDateTime trong Java sang Timestamp của SQL
            pstmt.setTimestamp(7, Timestamp.valueOf(setting.getRegisteredAt()));

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lưu AutoBidSetting: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean update(AutoBidSetting setting) {
        String sql = "UPDATE auto_bid_settings SET max_bid = ?, increment = ?, is_active = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, setting.getMaxBid());
            pstmt.setDouble(2, setting.getIncrement());
            pstmt.setBoolean(3, setting.isActive());
            pstmt.setString(4, setting.getId());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi cập nhật AutoBidSetting: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<AutoBidSetting> findActiveByAuction(String auctionId) {
        List<AutoBidSetting> activeSettings = new ArrayList<>();

        // TỐI ƯU NHẤT: Lọc các bản ghi đang Active và sắp xếp theo thời gian đăng ký (Ai đến trước phục vụ trước)
        // Việc ORDER BY ở tầng DB sẽ giúp Server đỡ phải sort lại bằng Java, tiết kiệm CPU.
        String sql = "SELECT * FROM auto_bid_settings WHERE auction_id = ? AND is_active = true ORDER BY registered_at ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AutoBidSetting setting = new AutoBidSetting(
                            rs.getString("id"),
                            rs.getString("bidder_id"),
                            rs.getString("auction_id"),
                            rs.getDouble("max_bid"),
                            rs.getDouble("increment"),
                            rs.getBoolean("is_active"),
                            rs.getTimestamp("registered_at").toLocalDateTime()
                    );
                    activeSettings.add(setting);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi truy vấn danh sách AutoBid: " + e.getMessage());
        }

        return activeSettings;
    }
}
