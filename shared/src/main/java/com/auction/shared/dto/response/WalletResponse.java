package com.auction.shared.dto.response;

import com.auction.shared.model.WalletTransaction;
import java.util.List;

/**
 * Response trả về thông tin ví điện tử.
 * Dùng chung cho: xem số dư, nạp tiền, rút tiền.
 */
public class WalletResponse {

    private String userId;
    private String username;
    private double balance;               // Tổng số dư hiện tại (Bao gồm cả tiền đang bị hold)
    private double availableBalance;      // [MỚI BỔ SUNG] Số dư khả dụng (Tổng tiền - Tiền đang hold)
    private List<WalletTransaction> transactions; // Lịch sử giao dịch (nullable)

    // Kết quả giao dịch vừa thực hiện
    private double transactionAmount;
    private String transactionType;
    private String message;

    public WalletResponse() {}

    // ──── Getters & Setters ────────────────────────────────────────────────

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public double getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(double availableBalance) { this.availableBalance = availableBalance; }

    public List<WalletTransaction> getTransactions() { return transactions; }
    public void setTransactions(List<WalletTransaction> transactions) { this.transactions = transactions; }

    public double getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(double transactionAmount) { this.transactionAmount = transactionAmount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

}