package com.auction.shared.dto.request;

public class TopUpRequest {
    private double amount;   // Số tiền muốn nạp (VND)

    public TopUpRequest() {}

    public TopUpRequest(double amount) {
        this.amount = amount;
    }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
