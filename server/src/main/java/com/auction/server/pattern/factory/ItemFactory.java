package com.auction.server.pattern.factory;

import com.auction.shared.enums.ItemCategory;
import com.auction.shared.model.item.*;
import java.util.Map;
import java.util.UUID;

public class ItemFactory {

    /**
     * HÀM MỚI (Khuyên dùng): Tự động trích xuất category từ Map data và tạo đúng object.
     * Service và DAO chỉ cần gọi hàm này, không cần tự parse Enum nữa.
     */
    public static Item createItem(Map<String, Object> data) {
        ItemCategory category = extractCategorySafely(data.get("category"));
        return createItem(category, data);
    }

    /**
     * Tạo Item đúng subclass từ category.
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
        Electronics e = new Electronics();
        setCommonFields(e, data);
        e.setBrand((String) data.getOrDefault("brand", ""));
        e.setModel((String) data.getOrDefault("model", ""));
        e.setWarrantyMonths(convertToInt(data.get("warrantyMonths")));
        return e;
    }

    private static Art buildArt(Map<String, Object> data) {
        Art a = new Art();
        setCommonFields(a, data);
        a.setArtist((String) data.getOrDefault("artist", ""));
        a.setMedium((String) data.getOrDefault("medium", ""));
        a.setYearCreated(convertToInt(data.get("yearCreated")));
        return a;
    }

    private static Vehicle buildVehicle(Map<String, Object> data) {
        Vehicle v = new Vehicle();
        setCommonFields(v, data);
        v.setMake((String) data.getOrDefault("make", ""));
        v.setVehicleModel((String) data.getOrDefault("vehicleModel", ""));
        v.setYear(convertToInt(data.get("year")));
        v.setMileage(convertToInt(data.get("mileage")));
        return v;
    }

    private static Item buildGenericItem(Map<String, Object> data) {
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

        item.setTitle((String) data.getOrDefault("title", "Sản phẩm chưa có tên"));
        item.setDescription((String) data.getOrDefault("description", ""));

        // Sử dụng hàm an toàn để gán Category
        item.setCategory(extractCategorySafely(data.get("category")));

        item.setSellerId((String) data.get("sellerId"));
    }

    /**
     * CHỐT CHẶN AN TOÀN: Xử lý triệt để mọi trường hợp sai chính tả, khác chữ hoa/thường
     * hoặc client vô tình gửi nguyên text của ComboBox (VD: "🎨 Nghệ thuật (Art)").
     */
    private static ItemCategory extractCategorySafely(Object catObj) {
        if (catObj == null) return null;
        if (catObj instanceof ItemCategory) return (ItemCategory) catObj;

        // Xóa khoảng trắng thừa và viết hoa toàn bộ để so sánh
        String catStr = String.valueOf(catObj).toUpperCase().trim();

        // Cứu hộ dữ liệu: Nhận dạng thông minh qua từ khóa
        if (catStr.contains("ART") || catStr.contains("NGHỆ THUẬT")) return ItemCategory.ART;
        if (catStr.contains("ELECTRONICS") || catStr.contains("ĐIỆN TỬ")) return ItemCategory.ELECTRONICS;
        if (catStr.contains("VEHICLE") || catStr.contains("PHƯƠNG TIỆN") || catStr.contains("XE")) return ItemCategory.VEHICLE;
        if (catStr.contains("OTHER") || catStr.contains("TÀI SẢN KHÁC") || catStr.contains("KHÁC")) return ItemCategory.OTHER;
        // Nếu là mã Enum chuẩn (ART, ELECTRONICS...)
        try {
            return ItemCategory.valueOf(catStr);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ [ItemFactory] Không nhận diện được danh mục: '" + catStr + "'. Khởi tạo dưới dạng Generic Item.");
            return null;
        }
    }

    // Hàm hỗ trợ ép kiểu số an toàn từ Map
    private static int convertToInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}