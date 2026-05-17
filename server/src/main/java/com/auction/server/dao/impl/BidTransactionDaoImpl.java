package com.auction.server.dao.impl;

import com.auction.shared.model.BidTransaction;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.pattern.singleton.DatabaseManager;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
public class BidTransactionDaoImpl implements BidTransactionDAO{
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public boolean save(BidTransaction bid) {
        // BUG FIX: SQL trước thiếu cột bidder_id (NOT NULL, FK → users.id) → MySQL throw SQLException
        // BUG FIX: bidder_name phải lưu username (display name), không phải bidderId (UUID)
        String insertBid = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bidder_name, amount, is_auto_bid, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";

        // BUG FIX: current_leader trong auctions lưu username (display name), không phải bidderId
        String updateAuction = "UPDATE auctions SET current_price = ?, current_leader = ?, bid_count = bid_count + 1 WHERE id = ? AND current_price < ?";

        Connection conn = null;
        try {
            conn = db.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(insertBid)) {
                ps.setString   (1, bid.getId());
                ps.setString   (2, bid.getAuctionId());
                ps.setString   (3, bid.getBidderId());           // bidder_id = UUID (FK)
                ps.setString   (4, bid.getBidderName());         // bidder_name = username (display)
                ps.setDouble   (5, bid.getAmount());
                ps.setBoolean  (6, bid.isAutoBid());
                ps.setTimestamp(7, Timestamp.valueOf(bid.getTimestamp() != null ? bid.getTimestamp() : LocalDateTime.now()));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateAuction)) {
                ps.setDouble(1, bid.getAmount());
                ps.setString(2, bid.getBidderName());            // BUG FIX: lưu username, không phải bidderId
                ps.setString(3, bid.getAuctionId());
                ps.setDouble(4, bid.getAmount());
                ps.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw new RuntimeException("save bid thất bại: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Admin: lấy toàn bộ lịch sử đặt giá, JOIN items để hiện tên sản phẩm.
     * Kết quả sắp xếp theo thời gian mới nhất trước (DESC).
     */
    @Override
    public List<BidTransaction> findAll() {
        String sql = """
            SELECT bt.*,
                   u.username  AS real_bidder_name,
                   i.title     AS product_title
            FROM   bid_transactions bt
            LEFT JOIN users    u ON bt.bidder_id  = u.id
            LEFT JOIN auctions a ON bt.auction_id = a.id
            LEFT JOIN items    i ON a.item_id     = i.id
            ORDER BY bt.timestamp DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<BidTransaction> list = new ArrayList<>();
            while (rs.next()) {
                BidTransaction bid = mapToBid(rs);
                // Gán tên sản phẩm từ JOIN (chỉ có trong admin view)
                try { bid.setProductTitle(rs.getString("product_title")); }
                catch (SQLException ignored) {}
                list.add(bid);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("findAll bid transactions thất bại", e);
        }
    }

    @Override
    public List<BidTransaction> findByAuctionId(String auctionId) {
        // 🌟 [MỚI] Thêm u.avatar as bidder_avatar vào lệnh SELECT
        String sql = """
            SELECT bt.*, u.username as real_bidder_name, u.avatar as bidder_avatar
            FROM bid_transactions bt
            LEFT JOIN users u ON bt.bidder_id = u.id
            WHERE bt.auction_id = ?
            ORDER BY bt.amount DESC, bt.timestamp ASC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<BidTransaction> list = new ArrayList<>();
                while (rs.next()) list.add(mapToBid(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByAuctionId thất bại", e);
        }
    }

    @Override
    public List<BidTransaction> findByBidderId(String bidderId) {
        // BUG FIX: WHERE và JOIN phải dùng bidder_id (UUID), không phải bidder_name
        String sql = """
            SELECT bt.*, u.username as real_bidder_name
            FROM bid_transactions bt
            LEFT JOIN users u ON bt.bidder_id = u.id
            WHERE bt.bidder_id = ?
            ORDER BY bt.timestamp DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<BidTransaction> list = new ArrayList<>();
                while (rs.next()) list.add(mapToBid(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByBidderId thất bại", e);
        }
    }

    @Override
    public Optional<BidTransaction> findHighestBid(String auctionId) {
        // FIX: Thêm tiebreaker bt.timestamp ASC → người đặt SỚM HƠN thắng khi cùng giá
        // Đây là quy tắc chuẩn của Proxy Bidding: "first bidder wins on tie"
        String sql = """
            SELECT bt.*, u.username as real_bidder_name
            FROM bid_transactions bt
            LEFT JOIN users u ON bt.bidder_id = u.id
            WHERE bt.auction_id = ?
            ORDER BY bt.amount DESC, bt.timestamp ASC
            LIMIT 1
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapToBid(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("findHighestBid thất bại", e);
        }
    }

    @Override
    public double getCurrentPrice(String auctionId) {
        String sql = "SELECT COALESCE(MAX(amount), 0) FROM bid_transactions WHERE auction_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private BidTransaction mapToBid(ResultSet rs) throws SQLException {
        BidTransaction bid = new BidTransaction();
        bid.setId        (rs.getString  ("id"));
        bid.setAuctionId (rs.getString  ("auction_id"));
        bid.setBidderId  (rs.getString  ("bidder_id"));
        bid.setAmount    (rs.getDouble  ("amount"));
        bid.setAutoBid   (rs.getBoolean ("is_auto_bid"));
        bid.setTimestamp (rs.getTimestamp("timestamp").toLocalDateTime());

        // bidder_name: ưu tiên username từ JOIN, fallback về cột bidder_name trong bảng
        String realName = null;
        try { realName = rs.getString("real_bidder_name"); } catch (SQLException ignored) {}
        if (realName == null || realName.isBlank()) {
            try { realName = rs.getString("bidder_name"); } catch (SQLException ignored) {}
        }
        bid.setBidderName(realName);

        // 🌟 [MỚI BỔ SUNG] Lấy avatar từ Database map vào Object
        try {
            String avatar = rs.getString("bidder_avatar");
            bid.setBidderAvatar(avatar);
        } catch (SQLException ignored) {}

        return bid;
    }
}