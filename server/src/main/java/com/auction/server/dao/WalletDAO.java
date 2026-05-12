package com.auction.server.dao;

import com.auction.shared.model.WalletTransaction;
import java.util.List;

/**
 * DAO interface cho nghiệp vụ ví điện tử.
 */
public interface WalletDAO {
    /** Lấy số dư hiện tại của người dùng */
    double getBalance(String userId);

    /**
     * Cộng tiền vào ví (top-up hoặc refund).
     * @return số dư sau khi cộng
     */
    double credit(String userId, double amount);

    /**
     * Trừ tiền khỏi ví (bid hoặc withdraw).
     * @return số dư sau khi trừ, hoặc -1 nếu không đủ số dư
     */
    double debit(String userId, double amount);

    /** Lưu một bản ghi giao dịch vào wallet_transactions */
    boolean saveTransaction(WalletTransaction tx);

    /** Lấy lịch sử giao dịch của người dùng (mới nhất trước) */
    List<WalletTransaction> getTransactions(String userId);
}
