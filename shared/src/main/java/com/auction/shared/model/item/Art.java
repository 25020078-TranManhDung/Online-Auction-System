package com.auction.shared.model.item;

public class Art extends Item {
    private String artist;
    private String material;

    public Art(int id, String name, String description, double price, String artist, String material) {
        super(id, name, description, price);
        this.artist = artist;
        this.material = material;
    }

    @Override
    public String getCategoryDetails() {
        return "Art - Artist: " + artist + ", Material: " + material;
    }
}
