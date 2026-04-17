package com.auction.shared.model.entity;

import java.io.Serializable;
import java.util.Objects; // Cần import thư viện này cho equals và hashCode

public abstract class Entity implements Serializable {
    // serialVersionUID giúp đảm bảo tính đồng bộ khi mã hóa/giải mã đối tượng giữa Client và Server
    private static final long serialVersionUID = 1L;

    // ĐÃ SỬA: Chuyển int sang String để lưu UUID theo đúng giao thức protocol.md
    protected String id;

    // Constructor mặc định
    public Entity() {}

    // Constructor khởi tạo với ID
    public Entity(String id) {
        this.id = id;
    }

    // Getter và Setter cho ID
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // Cập nhật equals() để so sánh chuỗi String thay vì int
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        // Sử dụng Objects.equals để tránh lỗi NullPointerException nếu id bị null
        return Objects.equals(id, entity.id);
    }

    // Cập nhật hashCode() cho String
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}