package com.auction.server.service;

import com.auction.server.dao.UserDAO;
import com.auction.server.dao.WalletDAO;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.TopUpRequest;
import com.auction.shared.dto.request.WithdrawRequest;
import com.auction.shared.dto.response.WalletResponse;
import com.auction.shared.enums.UserRole;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.model.WalletTransaction;
import com.auction.shared.model.WalletTransaction.TransactionType;
import com.auction.shared.model.user.User;

import java.util.List;
import java.util.UUID;

/**
 * WalletService – Xử lý toàn bộ nghiệp vụ ví điện tử.
 *
 * Luồng tiền khi đấu giá kết thúc:
 *   1. BID_DEDUCT  : tiền bị khóa ngay khi bidder đặt giá thắng
 *   2. BID_REFUND  : hoàn tiền cho người bị outbid
 *   3. SELLER_RECEIVE : seller nhận 95% giá thắng
 *   4. COMMISSION     : admin nhận 5% hoa hồng
 */
public class WalletService {

    private static final double COMMISSION_RATE = 0.05; // 5% hoa hồng cho admin

    private final WalletDAO walletDao;
    private final UserDAO userDao;

    public WalletService(WalletDAO walletDao, UserDAO userDao) {
        this.walletDao = walletDao;
        this.userDao   = userDao;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Xem số dư & lịch sử giao dịch
    // ──────────────────────────────────────────────────────────────────────

    public WalletResponse getWallet(String token) {
        String userId = validateToken(token);
        User user = getUser(userId);

        WalletResponse resp = new WalletResponse();
        resp.setUserId(userId);
        resp.setUsername(user.getUsername());
        resp.setBalance(walletDao.getBalance(userId));

        resp.setAvailableBalance(walletDao.getAvailableBalance(userId));

        List<WalletTransaction> history = walletDao.getTransactions(userId);
        resp.setTransactions(history);
        resp.setMessage("Thông tin ví thành công.");
        return resp;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Bidder nạp tiền
    // ──────────────────────────────────────────────────────────────────────

    public WalletResponse topUp(TopUpRequest req, String token) {
        String userId = validateToken(token);
        User user = getUser(userId);

        if (user.getRole() != UserRole.BIDDER) {
            throw new AuctionException("FORBIDDEN", "Chỉ Bidder mới có thể nạp tiền vào ví.");
        }
        if (req.getAmount() <= 0) {
            throw new AuctionException("INVALID_AMOUNT", "Số tiền nạp phải lớn hơn 0.");
        }

        double balanceAfter = walletDao.credit(userId, req.getAmount());

        saveTransaction(userId, TransactionType.TOP_UP, req.getAmount(), balanceAfter,
                "Nạp tiền vào ví: " + formatVnd(req.getAmount()), null);

        WalletResponse resp = new WalletResponse();
        resp.setUserId(userId);
        resp.setUsername(user.getUsername());
        resp.setBalance(balanceAfter);
        resp.setTransactionAmount(req.getAmount());
        resp.setTransactionType("TOP_UP");
        resp.setMessage("Nạp tiền thành công. Số dư mới: " + formatVnd(balanceAfter));
        return resp;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Seller rút tiền
    // ──────────────────────────────────────────────────────────────────────

    public WalletResponse withdraw(WithdrawRequest req, String token) {
        String userId = validateToken(token);
        User user = getUser(userId);

        if (user.getRole() != UserRole.SELLER) {
            throw new AuctionException("FORBIDDEN", "Chỉ Seller mới có thể rút tiền từ ví doanh thu.");
        }
        if (req.getAmount() <= 0) {
            throw new AuctionException("INVALID_AMOUNT", "Số tiền rút phải lớn hơn 0.");
        }

        double currentBalance = walletDao.getBalance(userId);
        if (req.getAmount() > currentBalance) {
            throw new AuctionException("INSUFFICIENT_BALANCE",
                    "Số dư không đủ. Hiện có: " + formatVnd(currentBalance)
                            + ", yêu cầu rút: " + formatVnd(req.getAmount()));
        }

        double balanceAfter = walletDao.debit(userId, req.getAmount());
        if (balanceAfter < 0) {
            throw new AuctionException("INSUFFICIENT_BALANCE", "Không đủ số dư để rút tiền.");
        }

        saveTransaction(userId, TransactionType.WITHDRAW, req.getAmount(), balanceAfter,
                "Rút tiền từ ví doanh thu: " + formatVnd(req.getAmount()), null);

        WalletResponse resp = new WalletResponse();
        resp.setUserId(userId);
        resp.setUsername(user.getUsername());
        resp.setBalance(balanceAfter);
        resp.setTransactionAmount(req.getAmount());
        resp.setTransactionType("WITHDRAW");
        resp.setMessage("Rút tiền thành công. Số dư còn lại: " + formatVnd(balanceAfter));
        return resp;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Tạm giữ (Hold) số dư khi Bidder đặt giá
    // ──────────────────────────────────────────────────────────────────────
    public void holdBalanceForBid(String bidderId, double amount, String auctionId) {
        // Lấy số dư khả dụng (Số dư tổng - Tiền đang bị hold ở các phiên khác)
        double availableBalance = walletDao.getAvailableBalance(bidderId);
        if (availableBalance < amount) {
            throw new AuctionException("INSUFFICIENT_BALANCE",
                    String.format("Số dư khả dụng không đủ. Khả dụng: %s, Cần: %s. Vui lòng nạp thêm.",
                            formatVnd(availableBalance), formatVnd(amount)));
        }

        // Cập nhật Database: Tăng giá trị cột 'held_amount'
        walletDao.hold(bidderId, amount);

        // Lấy lại tổng số dư (không đổi) để ghi log
        double totalBalance = walletDao.getBalance(bidderId);

        User bidder = userDao.findById(bidderId);
        saveTransaction(bidderId, TransactionType.BID_HOLD, amount, totalBalance,
                String.format("Tạm giữ %s để đặt giá phiên %s", formatVnd(amount), auctionId),
                auctionId);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Hủy tạm giữ (Unhold) khi Bidder bị vượt giá
    // ──────────────────────────────────────────────────────────────────────
    public void releaseHeldBalance(String previousLeaderId, double previousAmount, String auctionId) {
        if (previousLeaderId == null || previousAmount <= 0) return;

        // Cập nhật Database: Giảm giá trị cột 'held_amount'
        walletDao.release(previousLeaderId, previousAmount);

        double totalBalance = walletDao.getBalance(previousLeaderId);

        saveTransaction(previousLeaderId, TransactionType.BID_RELEASE, previousAmount, totalBalance,
                String.format("Hủy tạm giữ do bị vượt giá trong phiên %s", auctionId),
                auctionId);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Xử lý thanh toán khi đấu giá kết thúc
    // ──────────────────────────────────────────────────────────────────────
    public void settleAuction(String winnerId, double winnerAmount,
                              String sellerId, String auctionId, String adminId) {
        if (winnerId == null) {
            return; // Không có người đặt giá
        }

        // 1. CHÍNH THỨC TRỪ TIỀN WINNER
        // Lệnh này phải giảm cả cột 'balance' (tổng tiền) VÀ cột 'held_amount' (tiền đang khóa)
        walletDao.debitHeld(winnerId, winnerAmount);

        double winnerBalance = walletDao.getBalance(winnerId);
        saveTransaction(winnerId, TransactionType.AUCTION_WIN, winnerAmount, winnerBalance,
                String.format("Thanh toán thắng đấu giá %s", auctionId),
                auctionId);

        // 2. Tính toán hoa hồng
        double commission    = winnerAmount * COMMISSION_RATE; // 5%
        double sellerReceive = winnerAmount - commission;      // 95%

        // 3. Seller nhận 95%
        double sellerBalanceAfter = walletDao.credit(sellerId, sellerReceive);
        saveTransaction(sellerId, TransactionType.SELLER_RECEIVE, sellerReceive, sellerBalanceAfter,
                String.format("Nhận doanh thu đấu giá %s (95%%)", auctionId),
                auctionId);

        // 4. Admin nhận 5% hoa hồng
        if (adminId != null) {
            double adminBalanceAfter = walletDao.credit(adminId, commission);
            saveTransaction(adminId, TransactionType.COMMISSION, commission, adminBalanceAfter,
                    String.format("Hoa hồng 5%% từ phiên %s", auctionId),
                    auctionId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Helper: tìm admin đầu tiên trong hệ thống để nhận hoa hồng
    // ──────────────────────────────────────────────────────────────────────

    public String findAdminId() {
        List<User> all = userDao.findAll();
        return all.stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .map(User::getId)
                .findFirst()
                .orElse(null);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Hàm tiện ích nội bộ
    // ──────────────────────────────────────────────────────────────────────

    private void saveTransaction(String userId, TransactionType type,
                                 double amount, double balanceAfter,
                                 String description, String auctionId) {
        WalletTransaction tx = new WalletTransaction(
                UUID.randomUUID().toString(),
                userId, type, amount, balanceAfter, description, auctionId
        );
        walletDao.saveTransaction(tx);
    }

    private String validateToken(String token) {
        String userId = TokenUtil.getUserId(token);
        if (userId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }
        return userId;
    }

    private User getUser(String userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw new AuctionException("USER_NOT_FOUND", "Không tìm thấy tài khoản người dùng.");
        }
        return user;
    }

    private String formatVnd(double amount) {
        return String.format("%,.0f VNĐ", amount);
    }
}