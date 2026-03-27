package com.auction.shared.model.item;

import com.auction.shared.model.entity.Entity;

public abstract class Item extends Entity {
    private String name;
    private String description;
    private double startingPrice;

    public Item(int id, String name, String description, double startingPrice) {
        super(id);
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    // Encapsulation: Sử dụng getter/setter để quản lý truy cập
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    // Polymorphism: Phương thức trừu tượng để các lớp con ghi đè [cite: 121, 122]
    public abstract String getCategoryDetails();
}
