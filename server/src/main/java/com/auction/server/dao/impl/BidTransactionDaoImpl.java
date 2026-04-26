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
        String insertBid = "INSERT INTO bid_transactions (id, auction_id, bidder_name, amount, is_auto_bid, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        String updateAuction = "UPDATE auctions SET current_price = ?, current_leader = ?, bid_count = bid_count + 1 WHERE id = ? AND current_price < ?";

        Connection conn = null;
        try {
            conn = db.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(insertBid)) {
                ps.setString   (1, bid.getId());
                ps.setString   (2, bid.getAuctionId());
                ps.setString   (3, bid.getBidderId());
                ps.setDouble   (4, bid.getAmount());
                ps.setBoolean  (5, bid.isAutoBid());
                ps.setTimestamp(6, Timestamp.valueOf(bid.getTimestamp() != null ? bid.getTimestamp() : LocalDateTime.now()));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateAuction)) {
                ps.setDouble(1, bid.getAmount());
                ps.setString(2, bid.getBidderId());
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
            throw new RuntimeException("save bid thất bại", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public List<BidTransaction> findByAuctionId(String auctionId) {
        String sql = """
            SELECT bt.*, u.username as real_bidder_name
            FROM bid_transactions bt
            LEFT JOIN users u ON bt.bidder_name = u.id
            WHERE bt.auction_id = ?
            ORDER BY bt.amount DESC, bt.timestamp DESC
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
        String sql = """
            SELECT bt.*, u.username as real_bidder_name
            FROM bid_transactions bt
            LEFT JOIN users u ON bt.bidder_name = u.id
            WHERE bt.bidder_name = ?
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
        String sql = """
            SELECT bt.*, u.username as real_bidder_name
            FROM bid_transactions bt
            LEFT JOIN users u ON bt.bidder_name = u.id
            WHERE bt.auction_id = ?
            ORDER BY bt.amount DESC
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
        bid.setBidderId  (rs.getString  ("bidder_name"));
        bid.setAmount    (rs.getDouble  ("amount"));
        bid.setAutoBid   (rs.getBoolean ("is_auto_bid"));
        bid.setTimestamp   (rs.getTimestamp("timestamp").toLocalDateTime());

        try {
            bid.setBidderName(rs.getString("real_bidder_name"));
        } catch (SQLException ignored) {}

        return bid;
    }
}
