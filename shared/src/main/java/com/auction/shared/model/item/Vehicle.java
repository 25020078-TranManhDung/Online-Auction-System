package com.auction.shared.model.item;

import com.auction.shared.enums.ItemCategory;

public class Vehicle extends Item {
    private String make;           // Protocol: "make"
    private String vehicleModel;   // Protocol: "vehicleModel"
    private int year;
    private int mileage;           // Protocol: "mileage"

    public Vehicle() {
        super("", "", "", ItemCategory.VEHICLE, "");
    }
    public Vehicle(String id, String title, String description, String sellerId, String make, String vehicleModel, int year, int mileage) {
        super(id, title, description, ItemCategory.VEHICLE, sellerId);
        this.make = make;
        this.vehicleModel = vehicleModel;
        this.year = year;
        this.mileage = mileage;
    }

    @Override
    public void printInfo() {
        System.out.println("Vehicle - Make: " + make + ", Model: " + vehicleModel + ", Year: " + year + ", Mileage: " + mileage);
    }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMileage() { return mileage; }
    public void setMileage(int mileage) { this.mileage = mileage; }
}