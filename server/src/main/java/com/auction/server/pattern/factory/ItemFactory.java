package com.auction.server.pattern.factory;

import com.auction.shared.enums.ItemCategory;
import com.auction.shared.model.item.*;
import java.util.Map;
import java.util.UUID;

public class ItemFactory {

    /**
     * Tạo Item đúng subclass từ category.
     * Dùng khi: tạo item mới từ request (JSON -> Map) và khi đọc item từ DB (ResultSet -> Map).
     */
    public static Item createItem(ItemCategory category, Map<String, Object> data) {
        if (category == null) return buildGenericItem(data);

        return switch (category) {
            case ELECTRONICS -> buildElectronics(data);
            case ART         -> buildArt(data);
            case VEHICLE     -> buildVehicle(data);
            default          -> buildGenericItem(data);
        };
    }

    private static Electronics buildElectronics(Map<String, Object> data) {
        // Dùng luôn constructor rỗng, code cực kỳ sạch sẽ
        Electronics e = new Electronics();
        setCommonFields(e, data);
        e.setBrand((String) data.getOrDefault("brand", ""));
        e.setModel((String) data.getOrDefault("model", ""));
        e.setWarrantyMonths(convertToInt(data.get("warrantyMonths")));
        return e;
    }

    private static Art buildArt(Map<String, Object> data) {
        // Dùng luôn constructor rỗng
        Art a = new Art();
        setCommonFields(a, data);
        a.setArtist((String) data.getOrDefault("artist", ""));
        a.setMedium((String) data.getOrDefault("medium", ""));
        a.setYearCreated(convertToInt(data.get("yearCreated")));
        return a;
    }

    private static Vehicle buildVehicle(Map<String, Object> data) {
        // Dùng luôn constructor rỗng
        Vehicle v = new Vehicle();
        setCommonFields(v, data);
        v.setMake((String) data.getOrDefault("make", ""));
        v.setVehicleModel((String) data.getOrDefault("vehicleModel", ""));
        v.setYear(convertToInt(data.get("year")));
        v.setMileage(convertToInt(data.get("mileage")));
        return v;
    }

    private static Item buildGenericItem(Map<String, Object> data) {
        // Dùng constructor rỗng cho lớp ẩn danh
        Item item = new Item() {
            @Override
            public void printInfo() {
                System.out.println("Other Item: " + getTitle());
            }
        };
        setCommonFields(item, data);
        return item;
    }

    private static void setCommonFields(Item item, Map<String, Object> data) {
        // Ưu tiên lấy ID từ data (nếu đọc từ DB), nếu không có (tạo mới) thì gen UUID
        item.setId(data.containsKey("id")
                ? String.valueOf(data.get("id"))
                : UUID.randomUUID().toString());

        item.setTitle((String) data.get("title"));
        item.setDescription((String) data.getOrDefault("description", ""));

        // Gán category
        Object cat = data.get("category");
        if (cat instanceof String) {
            item.setCategory(ItemCategory.valueOf((String) cat));
        } else {
            item.setCategory((ItemCategory) cat);
        }

        item.setSellerId((String) data.get("sellerId"));
    }

    // Hàm hỗ trợ ép kiểu số an toàn từ Map
    private static int convertToInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return 0;
        }
    }
}