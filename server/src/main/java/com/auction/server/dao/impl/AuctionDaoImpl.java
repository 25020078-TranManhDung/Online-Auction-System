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
        String sql = "SELECT * FROM auctions WHERE status = 'RUNNING' AND end_time <= ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(deadline));
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
              (id, item_id, seller_id, start_price, current_price,
               min_bid_increment, start_time, end_time,
               status, current_leader)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString   (1,  auction.getId());
            ps.setString   (2,  auction.getItemId());
            ps.setString   (3,  auction.getSellerId());
            ps.setDouble   (4,  auction.getStartPrice());
            ps.setDouble   (5,  auction.getCurrentPrice());
            ps.setDouble   (6,  auction.getMinBidIncrement());
            ps.setTimestamp(7,  toTs(auction.getStartTime()));
            ps.setTimestamp(8,  toTs(auction.getEndTime()));
            ps.setString   (9,  auction.getStatus().name());
            ps.setString   (10, auction.getCurrentLeader());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("save auction thất bại", e);
        }
    }

    @Override
    public boolean update(Auction auction) {
        String sql = """
            UPDATE auctions
            SET current_price = ?, status = ?, end_time = ?, current_leader = ?
            WHERE id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble   (1, auction.getCurrentPrice());
            ps.setString   (2, auction.getStatus().name());
            ps.setTimestamp(3, toTs(auction.getEndTime()));
            ps.setString   (4, auction.getCurrentLeader());
            ps.setString   (5, auction.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("update auction thất bại", e);
        }
    }

    private Auction mapToAuction(ResultSet rs) throws SQLException {
        Auction a = new Auction();
        a.setId              (rs.getString  ("id"));
        a.setItemId          (rs.getString  ("item_id"));
        a.setSellerId        (rs.getString  ("seller_id"));
        a.setStartPrice      (rs.getDouble  ("start_price"));
        a.setCurrentPrice    (rs.getDouble  ("current_price"));
        a.setMinBidIncrement (rs.getDouble  ("min_bid_increment"));
        a.setStartTime       (toLdt(rs.getTimestamp("start_time")));
        a.setEndTime         (toLdt(rs.getTimestamp("end_time")));
        a.setStatus          (AuctionStatus.valueOf(rs.getString("status")));
        a.setCurrentLeader       (rs.getString  ("current_leader"));
        return a;
    }

    private Timestamp toTs (LocalDateTime ldt) {
        return ldt == null ? null : Timestamp.valueOf(ldt);
    }
    private LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public List<Auction> findAuctions(AuctionStatus status, int offset, int limit) {
        // Sử dụng LIMIT và OFFSET để hỗ trợ phân trang ở mức Database
        String sql = "SELECT * FROM auctions WHERE status = ? ORDER BY end_time ASC LIMIT ? OFFSET ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setInt(2, limit);
            ps.setInt(3, offset);

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
