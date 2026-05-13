package com.auction.server.dao;

import com.auction.shared.model.WalletTransaction;
import java.util.List;

/**
 * DAO interface cho nghiệp vụ ví điện tử.
 */
public interface WalletDAO {
    /**
     * Lấy tổng số dư hiện tại của người dùng (Bao gồm cả tiền đang bị khóa).
     */
    double getBalance(String userId);

    /**
     * Lấy số dư khả dụng (Tổng số dư - Tiền đang bị tạm giữ ở các phiên đấu giá).
     * Dùng để check xem người dùng có đủ tiền đem đi đặt giá tiếp không.
     */
    double getAvailableBalance(String userId);

    /**
     * Cộng tiền vào ví (top-up hoặc nhận doanh thu).
     * @return số dư sau khi cộng
     */
    double credit(String userId, double amount);

    /**
     * Trừ tiền tự do khỏi ví (Dùng cho chức năng rút tiền - withdraw).
     * @return số dư sau khi trừ, hoặc -1 nếu không đủ số dư
     */
    double debit(String userId, double amount);

    /**
     * Tạm giữ một khoản tiền khi người dùng vươn lên dẫn đầu phiên đấu giá.
     * Thao tác: Tăng giá trị cột 'held_amount' trong Database.
     */
    void hold(String userId, double amount);

    /**
     * Hủy tạm giữ tiền khi người dùng bị đối thủ khác vượt giá.
     * Thao tác: Giảm giá trị cột 'held_amount' trong Database.
     */
    void release(String userId, double amount);

    /**
     * Chính thức trừ tiền khi người dùng chiến thắng phiên đấu giá.
     * Thao tác: Trừ đi số tiền thắng ở CẢ 2 CỘT (balance và held_amount).
     */
    void debitHeld(String userId, double amount);

    /** Lưu một bản ghi giao dịch vào bảng wallet_transactions */
    boolean saveTransaction(WalletTransaction tx);

    /** Lấy lịch sử giao dịch của người dùng (sắp xếp mới nhất lên trước) */
    List<WalletTransaction> getTransactions(String userId);
}
