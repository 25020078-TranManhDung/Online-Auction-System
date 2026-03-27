package com.auction.shared.model.user;

public class Bidder extends User {

    private double maxBid;
    private double increment;
    private boolean autoBiddingEnabled;

    public Bidder(String username, String password, String email) {
        super(username, password, email);
        this.autoBiddingEnabled = false;
    }

    @Override
    public void showRole() {
        System.out.println("Vai trò: Người tham gia đấu giá (Bidder)");
    }

    public void setupAutoBidding(double maxBid, double increment) {
        this.maxBid = maxBid;
        this.increment = increment;
        this.autoBiddingEnabled = true;
    }

    public void disableAutoBidding() {
        this.autoBiddingEnabled = false;
    }

    public double getMaxBid() { return maxBid; }

    public double getIncrement() { return increment; }

    public boolean isAutoBiddingEnabled() { return autoBiddingEnabled; }
}
