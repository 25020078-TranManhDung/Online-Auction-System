package com.auction.server.service;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.BidRequest;
import com.auction.shared.dto.response.BidResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.model.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BidService xử lý logic cốt lõi của tính năng đặt giá (Bidding).
 * Áp dụng "Fine-grained locking" (Khóa chi tiết) theo từng phiên đấu giá
 * để tối đa hóa hiệu suất Concurrent Bidding.
 *
 * [MERGE] Cơ chế Hold Balance:
 *   - Thay deductForBid()        → holdBalanceForBid()       (tạm giữ tiền, chưa trừ thật)
 *   - Thay refundPreviousLeader() → releaseHeldBalance()      (nhả tạm giữ, không hoàn tiền)
 *   Tiền thật chỉ được chuyển khi Winner gọi CONFIRM_PAYMENT.
 */
public class BidService {

    private final BidTransactionDAO bidDao;
    private final AuctionDAO auctionDao;
    private final UserDAO userDao;

    private final WalletService walletService;

    private final AuctionEventBus eventBus = AuctionEventBus.getInstance();
    private final AuctionManager manager = AuctionManager.getInstance();

    // TỐI ƯU: Lock riêng biệt theo từng AuctionId.
    // Giúp 100 người đặt giá ở Phiên A không làm chặn 100 người đang đặt giá ở Phiên B.
    private final Map<String, Object> auctionLocks = new ConcurrentHashMap<>();

    public BidService(BidTransactionDAO bidDao, AuctionDAO auctionDao, UserDAO userDao, WalletService walletService) {
        this.bidDao = bidDao;
        this.auctionDao = auctionDao;
        this.userDao = userDao;
        this.walletService = walletService;
    }

    public BidResponse placeBid(BidRequest req, String token) {
        String bidderId = TokenUtil.getUserId(token);
        if (bidderId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }
        return placeBidInternal(req, bidderId);
    }

    // Dành riêng cho hệ thống Auto-Bid (bỏ qua token, lấy bidderId từ request)
    public void placeSystemBid(BidRequest req) {
        placeBidInternal(req, req.getBidderId());
    }

    // Hàm lõi dùng chung — chứa toàn bộ logic đặt giá
    private BidResponse placeBidInternal(BidRequest req, String bidderId) {
        String auctionId = req.getAuctionId();
        double amount = req.getAmount();

        Object lock = auctionLocks.computeIfAbsent(auctionId, k -> new Object());

        // CHỐNG RACE CONDITION: synchronized theo từng phiên, không lock toàn bộ service
        synchronized (lock) {

            // Lấy Auction từ RAM (Manager) — fallback xuống DB nếu cache miss
            Auction auction = manager.getAuction(auctionId);
            if (auction == null) {
                auction = auctionDao.findById(auctionId);
                if (auction == null) {
                    throw new AuctionException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.");
                }
            }

            // Kiểm tra trạng thái phiên
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new AuctionException("AUCTION_CLOSED", "Phiên đấu giá đã đóng hoặc chưa bắt đầu.");
            }

            // Ngăn người bán tự đẩy giá (Shill Bidding)
            if (auction.getSellerId().equals(bidderId)) {
                throw new AuctionException("INVALID_BID", "Người bán không thể tự đặt giá cho sản phẩm của mình.");
            }

            User bidder = userDao.findById(bidderId);
            if (bidder == null) {
                throw new AuctionException("USER_NOT_FOUND", "Tài khoản người dùng không tồn tại.");
            }

            // --- [HOLD BALANCE] Kiểm tra giá hợp lệ trước khi thao tác ví ---
            if (amount < auction.getCurrentPrice() + auction.getMinBidIncrement()) {
                throw new AuctionException("INSUFFICIENT_BID",
                    "Mức giá phải lớn hơn hoặc bằng Giá hiện tại (" + auction.getCurrentPrice()
                        + ") + Bước giá tối thiểu (" + auction.getMinBidIncrement() + ")");
            }

            // Ngăn người dẫn đầu tự đẩy giá thêm
            if (bidderId.equals(auction.getCurrentLeaderId())) {
                throw new AuctionException("INVALID_BID", "Bạn đang là người dẫn đầu, không thể tự đặt giá thêm.");
            }

            // Lưu leader cũ để nhả hold sau
            String previousLeaderId     = auction.getCurrentLeaderId();
            double previousLeaderAmount = auction.getCurrentLeaderAmount();

            // [HOLD BALANCE] Tạm giữ tiền bidder mới (kiểm tra available balance)
            walletService.holdBalanceForBid(bidderId, amount, auctionId);

            // Xây dựng BidTransaction
            BidTransaction newBid = new BidTransaction(
                UUID.randomUUID().toString(),
                auctionId,
                bidderId,
                bidder.getUsername(),
                amount,
                LocalDateTime.now(),
                req.isAutoBid()
            );

            // Cập nhật model phiên đấu giá
            auction.addBidTransaction(newBid);
            auction.setCurrentLeaderId(bidderId);
            auction.setCurrentLeaderAmount(amount);

            // [HOLD BALANCE] Nhả tạm giữ cho người dẫn đầu cũ (không hoàn tiền thật)
            if (previousLeaderId != null) {
                walletService.releaseHeldBalance(previousLeaderId, previousLeaderAmount, auctionId);
            }

            // Lưu DB
            bidDao.save(newBid);
            auctionDao.update(auction);

            // Anti-Sniping: gia hạn nếu bid trong 30s cuối
            checkAndExtend(auction);

            // Broadcast realtime cho toàn bộ client
            eventBus.publishBidPlaced(auction, newBid);

            BidResponse response = new BidResponse();
            response.setAuctionId(auctionId);
            response.setBidderId(bidderId);
            response.setBidderName(bidder.getUsername());
            response.setAmount(amount);
            response.setNewCurrentPrice(auction.getCurrentPrice());

            return response;
        }
    }

    /**
     * Anti-sniping: nếu bid trong 30 giây cuối → gia hạn thêm 60 giây.
     */
    private void checkAndExtend(Auction auction) {
        long remainingSeconds = java.time.temporal.ChronoUnit.SECONDS.between(
            LocalDateTime.now(), auction.getEndTime()
        );
        if (remainingSeconds > 0 && remainingSeconds <= 30) {
            auction.setEndTime(auction.getEndTime().plusSeconds(60));
            auctionDao.update(auction);
            eventBus.publishAuctionExtended(auction, 60L);
        }
    }

    public List<BidTransaction> getHistory(String auctionId) {
        Auction auction = manager.getAuction(auctionId);
        if (auction == null) {
            auction = auctionDao.findById(auctionId);
            if (auction == null) {
                throw new AuctionException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.");
            }
        }
        List<BidTransaction> history = bidDao.findByAuctionId(auctionId);
        return history != null ? history : new ArrayList<>();
    }

    /**
     * [GIỮ LẠI] Admin: lấy toàn bộ lịch sử đặt giá của hệ thống.
     * JOIN với items để trả về tên sản phẩm cho cột "Sản phẩm" trên Admin dashboard.
     */
    public List<BidTransaction> getAllBids() {
        List<BidTransaction> all = bidDao.findAll();
        return all != null ? all : new ArrayList<>();
    }
}