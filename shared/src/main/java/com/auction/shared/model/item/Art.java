package com.auction.shared.model.item;

import com.auction.shared.enums.ItemCategory;

public class Art extends Item {
    private String artist;
    private String medium;         // Protocol: "medium"
    private int yearCreated;       // Protocol: "yearCreated"

    public Art(String id, String title, String description, String sellerId, String artist, String medium, int yearCreated) {
        super(id, title, description, ItemCategory.ART, sellerId);
        this.artist = artist;
        this.medium = medium;
        this.yearCreated = yearCreated;
    }

    @Override
    public void printInfo() {
        System.out.println("Art - Artist: " + artist + ", Medium: " + medium + ", Year: " + yearCreated);
    }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }

    public int getYearCreated() { return yearCreated; }
    public void setYearCreated(int yearCreated) { this.yearCreated = yearCreated; }
}