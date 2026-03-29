package com.auction.shared.model.user;

public class Seller extends User {

    private double reputationScore;

    public Seller(String username, String password, String email) {
        super(username, password, email);
        this.reputationScore = 5.0;
    }

    @Override
    public void showRole() {
        System.out.println("Vai trò: Người bán hàng (Seller)");
    }

    public double getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(double reputationScore) {
        this.reputationScore = reputationScore;
    }
}

