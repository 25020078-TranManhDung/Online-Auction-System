package com.auction.shared.model.item;

public class Electronics extends Item {
    private String brand;
    private int warrantyMonths;

    public Electronics(int id, String name, String description, double price, String brand, int warranty) {
        super(id, name, description, price);
        this.brand = brand;
        this.warrantyMonths = warranty;
    }

    @Override
    public String getCategoryDetails() {
        return "Electronics - Brand: " + brand + ", Warranty: " + warrantyMonths + " months";
    }
}
