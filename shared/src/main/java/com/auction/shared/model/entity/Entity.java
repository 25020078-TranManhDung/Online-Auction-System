package com.auction.shared.model.entity;

import java.io.Serializable;

public abstract class Entity implements Serializable {
    // serialVersionUID giúp đảm bảo tính đồng bộ khi mã hóa/giải mã đối tượng giữa Client và Server
    private static final long serialVersionUID = 1L;
    // Encapsulation: Thuộc tính được bảo vệ, chỉ các lớp con (Item, User) mới truy cập trực tiếp được
    protected int id;

    // Constructor mặc định
    public Entity() {}

    // Constructor khởi tạo với ID
    public Entity(int id) {
        this.id = id;
    }

    // Getter và Setter cho ID
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return id == entity.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
