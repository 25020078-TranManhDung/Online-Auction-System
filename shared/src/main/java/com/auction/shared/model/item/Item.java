package com.auction.shared.model.item;

import com.auction.shared.model.entity.Entity;
import com.auction.shared.enums.ItemCategory;

public abstract class Item extends Entity {
    private String title;          // Protocol: "title"
    private String description;
    private ItemCategory category; // Protocol: "category"
    private String sellerId;       // Protocol: "sellerId"
    private double startingPrice;

    public Item() {
        super();
    }
    public Item(String id, String title, String description, ItemCategory category, String sellerId) {
        super(id); // ID là String
        this.title = title;
        this.description = description;
        this.category = category;
        this.sellerId = sellerId;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    // Phương thức trừu tượng để đảm bảo tính đa hình cơ bản
    public abstract void printInfo();
}
