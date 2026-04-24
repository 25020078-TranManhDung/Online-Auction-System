package com.auction.shared.model.item;

import com.auction.shared.enums.ItemCategory;

public class Electronics extends Item {
    private String brand;
    private String model;          // Protocol: "model"
    private int warrantyMonths;    // Protocol: "warrantyMonths"

    public Electronics() {
        super("", "", "", ItemCategory.ELECTRONICS, "");
    }
    public Electronics(String id, String title, String description, String sellerId, String brand, String model, int warrantyMonths) {
        super(id, title, description, ItemCategory.ELECTRONICS, sellerId);
        this.brand = brand;
        this.model = model;
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public void printInfo() {
        System.out.println("Electronics - Brand: " + brand + ", Model: " + model + ", Warranty: " + warrantyMonths + " months");
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
}