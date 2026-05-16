package com.auction.server.dao.impl;

import com.auction.shared.model.user.*;
import com.auction.shared.enums.UserRole;
import com.auction.server.dao.UserDAO;
import com.auction.server.pattern.singleton.DatabaseManager;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
public class UserDaoImpl implements UserDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public User findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        // Đưa Connection vào thẳng try để tự động close() và bắt SQLException
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapToUser(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findById thất bại", e);
        }
    }

    @Override
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapToUser(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername thất bại", e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<User> list = new ArrayList<>();
            while (rs.next()) list.add(mapToUser(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("findAll thất bại", e);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean save(User user) {
        String sql = """
            INSERT INTO users
              (id, username, password, email, role, admin_level, reputation_score, full_name, avatar)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getRole().name());
            ps.setInt(6, (user instanceof Admin) ? ((Admin)user).getAdminLevel() : 0);
            ps.setDouble(7, (user instanceof Seller) ? ((Seller)user).getReputationScore() : 5.0);
            ps.setString(8, user.getFullname()); // Họ và tên
            ps.setString(9, user.getAvatar());   // [MỚI] Thêm lưu Avatar
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("save user thất bại", e);
        }
    }

    @Override
    public boolean update(User user) {
        String sql = """
            UPDATE users
            SET username=?, password=?, email=?, full_name=?, admin_level=?, reputation_score=?,
                status=?, violation_count=?, locked_until=?, avatar=?
            WHERE id=?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getFullname());
            ps.setInt(5, (user instanceof Admin) ? ((Admin)user).getAdminLevel() : 0);
            ps.setDouble(6, (user instanceof Seller) ? ((Seller)user).getReputationScore() : 5.0);
            ps.setString(7, user.getStatus());
            ps.setInt(8, user.getViolationCount());
            if (user.getLockedUntil() != null) {
                ps.setTimestamp(9, Timestamp.valueOf(user.getLockedUntil()));
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }
            ps.setString(10, user.getAvatar()); // [MỚI] Cập nhật Avatar
            ps.setString(11, user.getId());     // [LƯU Ý] ID bị đẩy xuống vị trí 11
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("update user thất bại", e);
        }
    }
    @Override
    public boolean delete(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("delete user thất bại", e);
        }
    }

    private User mapToUser(ResultSet rs) throws SQLException {
        UserRole role = UserRole.valueOf(rs.getString("role"));

        User user = switch (role) {
            case BIDDER -> new Bidder();
            case SELLER -> {
                Seller s = new Seller();
                s.setReputationScore(rs.getDouble("reputation_score"));
                yield s;
            }
            case ADMIN  -> {
                Admin a = new Admin();
                a.setAdminLevel(rs.getInt("admin_level"));
                yield a;
            }
        };

        user.setId(rs.getString("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setEmail(rs.getString("email"));
        user.setFullname(rs.getString("full_name"));

        // [MỚI] Đọc chuỗi Avatar từ CSDL
        user.setAvatar(rs.getString("avatar"));

        user.setRole(role);

        String status = rs.getString("status");
        user.setStatus(status != null ? status : "ACTIVE");

        user.setViolationCount(rs.getInt("violation_count"));

        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        user.setLockedUntil(lockedUntil != null ? lockedUntil.toLocalDateTime() : null);

        return user;
    }
}