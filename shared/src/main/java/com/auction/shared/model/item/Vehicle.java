package com.auction.shared.model.item;

public class Vehicle extends Item {
    private String vin;
    private int year;

    public Vehicle(int id, String name, String description, double price, String vin, int year) {
        super(id, name, description, price);
        this.vin = vin;
        this.year = year;
    }

    @Override
    public String getCategoryDetails() {
        return "Vehicle - VIN: " + vin + ", Year: " + year;
    }
}
