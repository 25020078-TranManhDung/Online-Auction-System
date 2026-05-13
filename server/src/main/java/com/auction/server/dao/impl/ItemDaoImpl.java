package com.auction.server.dao.impl;
import com.auction.shared.model.item.*;
import com.auction.shared.enums.ItemCategory;
import com.auction.server.dao.ItemDAO;
import com.auction.server.pattern.factory.ItemFactory;
import com.auction.server.pattern.singleton.DatabaseManager;
import java.sql.*;
import java.util.*;
public class ItemDaoImpl implements ItemDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public Item findById(String id) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapToItem(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findById item thất bại", e);
        }
    }

    @Override
    public List<Item> findBySellerId(String sellerId) {
        String sql = "SELECT * FROM items WHERE seller_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Item> list = new ArrayList<>();
                while (rs.next()) list.add(mapToItem(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findBySellerId thất bại", e);
        }
    }

    @Override
    public List<Item> findByCategory(ItemCategory category) {
        if (category == null) return new ArrayList<>();
        String sql = "SELECT * FROM items WHERE category = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Item> list = new ArrayList<>();
                while (rs.next()) list.add(mapToItem(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByCategory thất bại", e);
        }
    }

    @Override
    public List<Item> searchByName(String keyword) {
        String sql = "SELECT * FROM items WHERE title LIKE ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<Item> list = new ArrayList<>();
                while (rs.next()) list.add(mapToItem(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("searchByName thất bại", e);
        }
    }

    @Override
    public boolean save(Item item) {
        String sql = """
            INSERT INTO items
              (id, title, description, category, seller_id,
               brand, model, warranty_months, 
               make, vehicle_model, year, mileage, 
               artist, medium, year_created)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, item.getTitle());
            ps.setString(3, item.getDescription());
            ps.setString(4, item.getCategory() != null ? item.getCategory().name() : "OTHER");
            ps.setString(5, item.getSellerId());

            for(int i = 6; i <= 15; i++) ps.setObject(i, null);

            if (item instanceof Electronics e) {
                ps.setString(6, e.getBrand());
                ps.setString(7, e.getModel());
                ps.setInt(8, e.getWarrantyMonths());
            } else if (item instanceof Vehicle v) {
                ps.setString(9, v.getMake());
                ps.setString(10, v.getVehicleModel());
                ps.setInt(11, v.getYear());
                ps.setInt(12, v.getMileage());
            } else if (item instanceof Art a) {
                ps.setString(13, a.getArtist());
                ps.setString(14, a.getMedium());
                ps.setInt(15, a.getYearCreated());
            }

            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("save item thất bại", e);
        }
    }

    @Override
    public boolean update(Item item) {
        String sql = """
            UPDATE items
            SET title=?, description=?, brand=?, model=?, warranty_months=?,
                make=?, vehicle_model=?, year=?, mileage=?, artist=?, medium=?, year_created=?
            WHERE id=?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getTitle());
            ps.setString(2, item.getDescription());

            for(int i = 3; i <= 12; i++) ps.setObject(i, null);

            if (item instanceof Electronics e) {
                ps.setString(3, e.getBrand());
                ps.setString(4, e.getModel());
                ps.setInt(5, e.getWarrantyMonths());
            } else if (item instanceof Vehicle v) {
                ps.setString(6, v.getMake());
                ps.setString(7, v.getVehicleModel());
                ps.setInt(8, v.getYear());
                ps.setInt(9, v.getMileage());
            } else if (item instanceof Art a) {
                ps.setString(10, a.getArtist());
                ps.setString(11, a.getMedium());
                ps.setInt(12, a.getYearCreated());
            }

            ps.setString(13, item.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("update item thất bại", e);
        }
    }

    @Override
    public boolean delete(String id) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("delete item thất bại", e);
        }
    }

    private Item mapToItem(ResultSet rs) throws SQLException {
        Map<String, Object> data = new HashMap<>();

        data.put("id",          rs.getString("id"));
        data.put("title",       rs.getString("title"));
        data.put("description", rs.getString("description"));
        // Đẩy thẳng chuỗi String lấy từ DB vào Map, để ItemFactory tự động nhận diện an toàn
        data.put("category",    rs.getString("category"));
        data.put("sellerId",    rs.getString("seller_id"));

        data.put("brand",          rs.getString("brand"));
        data.put("model",          rs.getString("model"));
        data.put("warrantyMonths", rs.getInt("warranty_months"));

        data.put("make",           rs.getString("make"));
        data.put("vehicleModel",   rs.getString("vehicle_model"));
        data.put("year",           rs.getInt("year"));
        data.put("mileage",        rs.getInt("mileage"));

        data.put("artist",         rs.getString("artist"));
        data.put("medium",         rs.getString("medium"));
        data.put("yearCreated",    rs.getInt("year_created"));

        // Gọi hàm bọc createItem(Map) mà chúng ta đã nâng cấp ở ItemFactory
        return ItemFactory.createItem(data);
    }
}
