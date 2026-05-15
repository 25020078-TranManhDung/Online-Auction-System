package com.auction.server.dao.impl;
import com.auction.shared.model.Auction;
import com.auction.shared.enums.AuctionStatus;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.pattern.singleton.DatabaseManager;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
public class AuctionDaoImpl implements AuctionDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public Auction findById(String id) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapToAuction(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findById auction thất bại", e);
        }
    }

    @Override
    public List<Auction> findByStatus(AuctionStatus status) {
        if (status == null) return new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ? ORDER BY end_time ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Auction> list = new ArrayList<>();
                while (rs.next()) list.add(mapToAuction(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByStatus thất bại", e);
        }
    }

    @Override
    public List<Auction> findBySellerId(String sellerId) {
        String sql = "SELECT * FROM auctions WHERE seller_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Auction> list = new ArrayList<>();
                while (rs.next()) list.add(mapToAuction(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findBySellerId thất bại", e);
        }
    }

    @Override
    public List<Auction> findExpiringBefore(LocalDateTime deadline) {
        if (deadline == null) return new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'RUNNING' AND end_time <= ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, toTs(deadline));
            try (ResultSet rs = ps.executeQuery()) {
                List<Auction> list = new ArrayList<>();
                while (rs.next()) list.add(mapToAuction(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findExpiringBefore thất bại", e);
        }
    }

    @Override
    public boolean save(Auction auction) {
        String sql = """
            INSERT INTO auctions
              (id, item_id, seller_id, start_price, current_price, min_bid_increment,
               start_time, end_time, status, current_leader, bid_count, current_leader_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auction.getId());
            ps.setString(2, auction.getItemId());
            ps.setString(3, auction.getSellerId());
            ps.setDouble(4, auction.getStartPrice());
            ps.setDouble(5, auction.getCurrentPrice());
            ps.setDouble(6, auction.getMinBidIncrement());
            ps.setTimestamp(7, toTs(auction.getStartTime()));
            ps.setTimestamp(8, toTs(auction.getEndTime()));
            ps.setString(9, auction.getStatus() != null ? auction.getStatus().name() : "OPEN");
            ps.setString(10, auction.getCurrentLeader());
            ps.setInt(11, auction.getBidCount());
            ps.setString(12, auction.getCurrentLeaderId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("save auction thất bại", e);
        }
    }

    @Override
    public boolean update(Auction auction) {
        String sql = """

            UPDATE auctions
            SET current_price=?, status=?, current_leader=?, bid_count=?,
                end_time=?, winner_id=?, current_leader_id=?
            WHERE id=?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, auction.getCurrentPrice());
            ps.setString(2, auction.getStatus() != null ? auction.getStatus().name() : "OPEN");
            ps.setString(3, auction.getCurrentLeader());
            ps.setInt(4, auction.getBidCount());
            ps.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            ps.setString(6, auction.getWinnerId());
            ps.setString(7, auction.getCurrentLeaderId());
            ps.setString(8, auction.getId());

            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("update auction thất bại", e);
        }
    }

    /**
     * [MỚI] Cập nhật thông tin cơ bản (giá, thời gian, bước giá) cho phiên đang OPEN.
     * WHERE ... AND status='OPEN' đảm bảo tự fail nếu phiên đã chuyển sang RUNNING trở lên.
     */
    @Override
    public boolean updateBasicInfo(Auction auction) {
        String sql = """
            UPDATE auctions
            SET start_price = ?,
                current_price = ?,
                min_bid_increment = ?,
                start_time = ?,
                end_time = ?
            WHERE id = ? AND status = 'OPEN'
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, auction.getStartPrice());
            ps.setDouble(2, auction.getStartPrice());   // current_price = start_price vì chưa có bid
            ps.setDouble(3, auction.getMinBidIncrement());
            ps.setTimestamp(4, toTs(auction.getStartTime()));
            ps.setTimestamp(5, toTs(auction.getEndTime()));
            ps.setString(6, auction.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("updateBasicInfo auction thất bại", e);
        }
    }

    private Auction mapToAuction(ResultSet rs) throws SQLException {
        Auction a = new Auction();
        a.setId(rs.getString("id"));
        a.setItemId(rs.getString("item_id"));
        a.setSellerId(rs.getString("seller_id"));
        a.setStartPrice(rs.getDouble("start_price"));
        a.setCurrentPrice(rs.getDouble("current_price"));
        a.setMinBidIncrement(rs.getDouble("min_bid_increment"));
        Timestamp st = rs.getTimestamp("start_time");
        if (st != null) a.setStartTime(st.toLocalDateTime());
        Timestamp et = rs.getTimestamp("end_time");
        if (et != null) a.setEndTime(et.toLocalDateTime());
        a.setStatus(AuctionStatus.valueOf(rs.getString("status")));
        a.setCurrentLeader(rs.getString("current_leader"));
        a.setBidCount(rs.getInt("bid_count"));

        // Đọc defensive — các cột này có thể chưa tồn tại trên DB cũ
        try { a.setWinnerId(rs.getString("winner_id")); }
        catch (SQLException ignored) { /* cột chưa được ALTER TABLE */ }
        try { a.setCurrentLeaderId(rs.getString("current_leader_id")); }
        catch (SQLException ignored) { /* cột chưa được ALTER TABLE */ }
        try { a.setCurrentLeaderAmount(rs.getDouble("current_leader_amount")); }
        catch (SQLException ignored) { a.setCurrentLeaderAmount(rs.getDouble("current_price")); }

        return a;
    }

    private Timestamp toTs(LocalDateTime ldt) {
        return ldt == null ? null : Timestamp.valueOf(ldt);
    }

    private LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public List<Auction> findAuctions(AuctionStatus status, int offset, int limit) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM auctions ");

        if (status != null) {
            sqlBuilder.append("WHERE status = ? ");
        }
        sqlBuilder.append("ORDER BY end_time ASC LIMIT ? OFFSET ?");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;

            if (status != null) {
                ps.setString(paramIndex++, status.name());
            }

            ps.setInt(paramIndex++, limit);
            ps.setInt(paramIndex, offset);

            try (ResultSet rs = ps.executeQuery()) {
                List<Auction> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapToAuction(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAuctions (phân trang) thất bại", e);
        }
    }
}