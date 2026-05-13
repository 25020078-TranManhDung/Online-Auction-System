package com.auction.server.dao.impl;

import com.auction.server.dao.WalletDAO;
import com.auction.server.pattern.singleton.DatabaseManager;
import com.auction.shared.model.WalletTransaction;
import com.auction.shared.model.WalletTransaction.TransactionType;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * WalletDaoImpl – Tầng truy xuất DB cho ví điện tử.
 *
 * Tất cả các thao tác credit/debit/hold đều dùng UPDATE nguyên tử để tránh
 * race condition khi nhiều luồng ghi đồng thời vào cùng một tài khoản.
 */
public class WalletDaoImpl implements WalletDAO {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ──────────────────────────────────────────────────────────────────────
    //  Đọc số dư (Tổng tiền)
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public double getBalance(String userId) {
        String sql = "SELECT wallet_balance FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("wallet_balance");
            }
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("getBalance thất bại", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Đọc số dư khả dụng (Tổng tiền - Tiền đang tạm giữ)
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public double getAvailableBalance(String userId) {
        // Sử dụng COALESCE để biến giá trị NULL thành 0 (đề phòng db chưa set default 0)
        String sql = "SELECT (wallet_balance - COALESCE(held_amount, 0)) AS available FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("available");
            }
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("getAvailableBalance thất bại", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Cộng tiền (credit)
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public double credit(String userId, double amount) {
        String sql = "UPDATE users SET wallet_balance = wallet_balance + ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            ps.executeUpdate();
            return getBalance(userId);
        } catch (SQLException e) {
            throw new RuntimeException("credit thất bại cho userId=" + userId, e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Trừ tiền tự do (debit)
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public double debit(String userId, double amount) {
        // Chỉ cho phép trừ nếu số dư KHẢ DỤNG >= amount
        String sql = """
            UPDATE users
            SET wallet_balance = wallet_balance - ?
            WHERE id = ? AND (wallet_balance - COALESCE(held_amount, 0)) >= ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            ps.setDouble(3, amount);
            int rows = ps.executeUpdate();
            if (rows == 0) return -1;  // Không đủ số dư khả dụng
            return getBalance(userId);
        } catch (SQLException e) {
            throw new RuntimeException("debit thất bại cho userId=" + userId, e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Tạm giữ tiền (hold) khi dẫn đầu phiên đấu giá
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public void hold(String userId, double amount) {
        String sql = "UPDATE users SET held_amount = COALESCE(held_amount, 0) + ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("hold thất bại cho userId=" + userId, e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Hủy tạm giữ (release) khi bị người khác outbid
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public void release(String userId, double amount) {
        // Đảm bảo không bị âm held_amount
        String sql = "UPDATE users SET held_amount = GREATEST(COALESCE(held_amount, 0) - ?, 0) WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("release thất bại cho userId=" + userId, e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Trừ tiền chính thức (debitHeld) khi kết thúc phiên
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public void debitHeld(String userId, double amount) {
        // Vừa trừ tổng tiền, vừa trừ tiền đang bị giam
        String sql = """
            UPDATE users 
            SET wallet_balance = wallet_balance - ?, 
                held_amount = GREATEST(COALESCE(held_amount, 0) - ?, 0) 
            WHERE id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setDouble(2, amount);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("debitHeld thất bại cho userId=" + userId, e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Lưu bản ghi giao dịch
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public boolean saveTransaction(WalletTransaction tx) {
        String sql = """
            INSERT INTO wallet_transactions
              (id, user_id, type, amount, balance_after, description, auction_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tx.getId());
            ps.setString(2, tx.getUserId());
            ps.setString(3, tx.getType().name());
            ps.setDouble(4, tx.getAmount());
            ps.setDouble(5, tx.getBalanceAfter());
            ps.setString(6, tx.getDescription());
            ps.setString(7, tx.getAuctionId());
            ps.setTimestamp(8, tx.getCreatedAt() != null
                    ? Timestamp.valueOf(tx.getCreatedAt())
                    : Timestamp.valueOf(LocalDateTime.now()));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("saveTransaction thất bại", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Lấy lịch sử giao dịch
    // ──────────────────────────────────────────────────────────────────────
    @Override
    public List<WalletTransaction> getTransactions(String userId) {
        String sql = """
            SELECT * FROM wallet_transactions
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT 50
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<WalletTransaction> list = new ArrayList<>();
                while (rs.next()) list.add(mapToTransaction(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTransactions thất bại", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Hàm tiện ích
    // ──────────────────────────────────────────────────────────────────────
    private WalletTransaction mapToTransaction(ResultSet rs) throws SQLException {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(rs.getString("id"));
        tx.setUserId(rs.getString("user_id"));
        tx.setType(TransactionType.valueOf(rs.getString("type")));
        tx.setAmount(rs.getDouble("amount"));
        tx.setBalanceAfter(rs.getDouble("balance_after"));
        tx.setDescription(rs.getString("description"));
        tx.setAuctionId(rs.getString("auction_id"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) tx.setCreatedAt(ts.toLocalDateTime());
        return tx;
    }
}