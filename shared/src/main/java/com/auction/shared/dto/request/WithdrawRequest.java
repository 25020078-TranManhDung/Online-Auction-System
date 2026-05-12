package com.auction.shared.dto.request;

/**
 * Request rút tiền khỏi ví (dành cho Seller).
 */
public class WithdrawRequest {
    private double amount;   // Số tiền muốn rút (VND)

    public WithdrawRequest() {}

    public WithdrawRequest(double amount) {
        this.amount = amount;
    }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
