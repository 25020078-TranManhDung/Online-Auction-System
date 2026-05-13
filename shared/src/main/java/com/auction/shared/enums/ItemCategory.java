package com.auction.shared.enums;

/**
 * Định nghĩa danh mục của các sản phẩm đấu giá.
 * Phiên bản nâng cấp: Tích hợp sẵn tên hiển thị cho UI (Giao diện)
 */
public enum ItemCategory {

    ART("🎨 Nghệ thuật (Art)"),
    ELECTRONICS("💻 Đồ điện tử (Electronics)"),
    VEHICLE("🚗 Phương tiện (Vehicle)"),
    OTHER("📦 Tài sản khác");

    // Biến lưu trữ tên hiển thị trên giao diện
    private final String displayName;

    // Constructor của Enum
    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    // Hàm Getter để Client (JavaFX) gọi ra dùng
    public String getDisplayName() {
        return displayName;
    }
}