package com.auction.server.service;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.CreateAuctionRequest;
import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.enums.UserRole;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UnauthorizedException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.User;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * AuctionService xử lý logic nghiệp vụ vòng đời của phiên đấu giá.
 *
 * [MERGE] Những điểm đã tích hợp:
 *  - createAuction: GIỮ hỗ trợ lên lịch bắt đầu (startTime từ Seller) của bạn
 *  - closeAuction:  DÙNG phiên bản mới — chỉ đánh dấu FINISHED, KHÔNG settle ví ngay
 *  - confirmPayment: THÊM MỚI — Winner xác nhận → settle ví (hold → thật)
 *  - markAsPaid:    GIỮ LẠI cho Admin dùng thủ công (không qua settle ví)
 *  - cancelAuction: MERGE — Admin + Seller đều được hủy (của bạn),
 *                   dùng releaseHeldBalance thay vì refundPreviousLeader (Hold Balance mới)
 */
public class AuctionService {

    private final AuctionDAO auctionDao;
    private final ItemDAO itemDao;
    private final BidTransactionDAO bidDao;
    private final UserDAO userDao;
    private final WalletService walletService;

    private final AuctionEventBus eventBus = AuctionEventBus.getInstance();
    private final AuctionManager manager   = AuctionManager.getInstance();

    public AuctionService(AuctionDAO auctionDao, ItemDAO itemDao, BidTransactionDAO bidDao,
                          UserDAO userDao, WalletService walletService) {
        this.auctionDao    = auctionDao;
        this.itemDao       = itemDao;
        this.bidDao        = bidDao;
        this.userDao       = userDao;
        this.walletService = walletService;
    }

    // =========================================================================
    // CREATE — [GIỮ LẠI] hỗ trợ lên lịch bắt đầu của bạn
    // =========================================================================
    public AuctionResponse createAuction(CreateAuctionRequest req, String token) {
        String sellerId = TokenUtil.getUserId(token);
        if (sellerId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }

        User seller = userDao.findById(sellerId);
        if (seller == null || seller.getRole() != UserRole.SELLER) {
            throw new AuctionException("PERMISSION_DENIED", "Chỉ Seller mới được quyền tạo phiên đấu giá.");
        }

        // Factory Pattern: tạo Item từ request
        java.util.Map<String, Object> itemData = new java.util.HashMap<>();
        itemData.put("title",       req.getTitle());
        itemData.put("description", req.getDescription());
        itemData.put("category",    req.getCategory());
        itemData.put("sellerId",    sellerId);

        Item newItem = com.auction.server.pattern.factory.ItemFactory.createItem(itemData);
        String newItemId = newItem.getId();

        boolean isItemSaved = itemDao.save(newItem);
        if (!isItemSaved) {
            throw new RuntimeException("Lỗi: Không thể lưu thông tin Sản phẩm vào Database!");
        }

        Auction auction = new Auction();
        auction.setId(UUID.randomUUID().toString());
        auction.setItemId(newItemId);
        auction.setSellerId(sellerId);

        // [GIỮ LẠI] Hỗ trợ lên lịch: nếu Seller chọn startTime tương lai thì dùng,
        // ngược lại bắt đầu ngay
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = (req.getStartTime() != null && req.getStartTime().isAfter(now))
            ? req.getStartTime()
            : now;
        auction.setStartTime(startTime);

        auction.setStartPrice(req.getStartingPrice());
        auction.setCurrentPrice(req.getStartingPrice());

        double increment = req.getMinBidIncrement() > 0
            ? req.getMinBidIncrement()
            : Math.max(1, req.getStartingPrice() * 0.05);
        auction.setMinBidIncrement(increment);

        if (req.getEndTime() != null) {
            auction.setEndTime(req.getEndTime());
        } else {
            auction.setEndTime(startTime.plusMinutes(req.getDurationMinutes()));
        }

        auction.setStatus(AuctionStatus.OPEN);

        boolean isSaved = auctionDao.save(auction);
        if (!isSaved) {
            throw new RuntimeException("Đã xảy ra lỗi hệ thống khi lưu phiên đấu giá.");
        }

        // Thêm vào AuctionManager để AuctionTimerService tự động OPEN → RUNNING
        manager.addAuction(auction);

        AuctionResponse response = new AuctionResponse();
        response.setAuctionId(auction.getId());
        response.setTitle(req.getTitle());
        response.setDescription(req.getDescription());
        response.setCategory(newItem.getCategory() != null ? newItem.getCategory().name() : "OTHER");
        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setHighestBidderName(null);
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        response.setTimeRemaining(calcTimeRemaining(auction));
        response.setSellerId(sellerId);

        return response;
    }

    // =========================================================================
    // START
    // =========================================================================
    public AuctionResponse startAuction(String auctionId, String token) {
        if (token != null && !TokenUtil.isValid(token)) {
            throw new AuctionException("UNAUTHORIZED", "Bạn cần đăng nhập để thực hiện thao tác này.");
        }

        Auction auction = getOrThrow(auctionId);
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionException("INVALID_STATUS", "Phiên đấu giá không ở trạng thái OPEN.");
        }

        auction.setStatus(AuctionStatus.RUNNING);
        auction.setStartTime(LocalDateTime.now());
        auctionDao.update(auction);
        manager.addAuction(auction);
        eventBus.publishAuctionStarted(auction);

        Item item = itemDao.findById(auction.getItemId());
        AuctionResponse response = new AuctionResponse();
        response.setAuctionId(auction.getId());
        response.setTitle(item != null ? item.getTitle() : "Không xác định");
        response.setDescription(item != null ? item.getDescription() : "Không xác định");
        response.setCategory((item != null && item.getCategory() != null) ? item.getCategory().name() : "N/A");
        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setHighestBidderName(null);
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        return response;
    }

    // =========================================================================
    // CLOSE — [MỚI] Chỉ đánh dấu FINISHED + lưu winner, KHÔNG settle ví ngay.
    // Tiền thật được xử lý khi Winner gọi confirmPayment().
    // =========================================================================
    public synchronized void closeAuction(String auctionId) {
        Auction auction = getOrThrow(auctionId);
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            return;
        }

        java.util.Optional<BidTransaction> topBidOpt = bidDao.findHighestBid(auctionId);
        if (topBidOpt.isPresent()) {
            BidTransaction topBid = topBidOpt.get();
            auction.setWinnerId(topBid.getBidderId());
            auction.setCurrentLeader(topBid.getBidderName());
            auction.setCurrentPrice(topBid.getAmount());
            // [MỚI] KHÔNG gọi settleAuction ở đây — chờ Winner xác nhận qua confirmPayment()
        }

        auction.setStatus(AuctionStatus.FINISHED);
        auctionDao.update(auction);
        manager.removeAuction(auctionId);
        eventBus.publishAuctionClosed(auction);
    }

    // =========================================================================
    // CONFIRM PAYMENT — [THÊM MỚI] Winner xác nhận → chính thức settle ví
    // RUNNING(hold) → FINISHED → PAID
    // =========================================================================
    public void confirmPayment(String auctionId, String token) {
        String requesterId = TokenUtil.getUserId(token);
        if (requesterId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        Auction auction = auctionDao.findById(auctionId);
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        if (auction.getStatus() != AuctionStatus.FINISHED) {
            throw new AuctionException("INVALID_STATE",
                "Chỉ có thể thanh toán khi phiên ở trạng thái FINISHED. "
                    + "Trạng thái hiện tại: " + auction.getStatus());
        }

        if (!requesterId.equals(auction.getWinnerId())) {
            throw new UnauthorizedException("Chỉ người chiến thắng mới có quyền xác nhận thanh toán.");
        }

        // Chính thức trừ tiền Hold → cộng cho Seller (sau khi trừ hoa hồng Admin)
        try {
            String adminId = walletService.findAdminId();
            walletService.settleAuction(
                requesterId,               // winnerId (đang bị hold)
                auction.getCurrentPrice(), // winnerAmount
                auction.getSellerId(),     // sellerId
                auctionId,                 // auctionId
                adminId                    // adminId nhận hoa hồng
            );
        } catch (Exception e) {
            System.err.println("[AuctionService.confirmPayment] Lỗi hệ thống Ví: " + e.getMessage());
            throw new AuctionException("PAYMENT_FAILED", "Không thể thanh toán: " + e.getMessage());
        }

        auction.setStatus(AuctionStatus.PAID);
        auctionDao.update(auction);
        eventBus.publishAuctionStatusChanged(auction, "PAID");
    }

    // =========================================================================
    // MARK AS PAID — [GIỮ LẠI] Admin xác nhận thủ công (không qua settle ví)
    // Dùng khi cần can thiệp trực tiếp mà không cần Winner tự xác nhận.
    // =========================================================================
    public void markAsPaid(String auctionId, String token) {
        String requesterId = TokenUtil.getUserId(token);
        if (requesterId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        Auction auction = auctionDao.findById(auctionId);
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        if (auction.getStatus() != AuctionStatus.FINISHED) {
            throw new AuctionException("INVALID_STATE",
                "Chỉ có thể xác nhận thanh toán khi phiên ở trạng thái FINISHED. "
                    + "Trạng thái hiện tại: " + auction.getStatus());
        }

        String role    = TokenUtil.getRole(token);
        boolean isAdmin  = "ADMIN".equals(role);
        boolean isWinner = requesterId.equals(auction.getWinnerId());
        if (!isAdmin && !isWinner) {
            throw new UnauthorizedException("Chỉ người thắng cuộc hoặc Admin mới được xác nhận thanh toán.");
        }

        auction.setStatus(AuctionStatus.PAID);
        auctionDao.update(auction);
        eventBus.publishAuctionStatusChanged(auction, "PAID");
    }

    // =========================================================================
    // CANCEL — [MERGE] Admin + Seller đều được hủy (của bạn),
    // dùng releaseHeldBalance (Hold Balance mới) thay vì refundPreviousLeader
    // =========================================================================
    public void cancelAuction(String auctionId, String token) {
        String requesterId = TokenUtil.getUserId(token);
        if (requesterId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        String role = TokenUtil.getRole(token);

        Auction auction = auctionDao.findById(auctionId);
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        // [GIỮ LẠI] Admin hủy bất kỳ, Seller chỉ hủy phiên của chính mình
        boolean isAdmin  = "ADMIN".equals(role);
        boolean isSeller = "SELLER".equals(role) && auction.getSellerId().equals(requesterId);
        if (!isAdmin && !isSeller) {
            throw new UnauthorizedException("Bạn không có quyền hủy phiên đấu giá này.");
        }

        if (auction.getStatus() == AuctionStatus.PAID) {
            throw new AuctionException("INVALID_STATE", "Không thể hủy phiên đấu giá đã được thanh toán.");
        }

        // [MERGE] Nếu phiên đang RUNNING → nhả tiền tạm giữ (Hold) của người dẫn đầu
        // Dùng releaseHeldBalance thay vì refundPreviousLeader vì giờ là Hold Balance
        if (auction.getStatus() == AuctionStatus.RUNNING
            && auction.getCurrentLeaderId() != null
            && auction.getCurrentLeaderAmount() > 0) {
            try {
                walletService.releaseHeldBalance(
                    auction.getCurrentLeaderId(),
                    auction.getCurrentLeaderAmount(),
                    auctionId
                );
            } catch (Exception e) {
                System.err.println("[AuctionService.cancelAuction] Lỗi nhả tiền giữ khi hủy phiên: "
                    + e.getMessage());
            }
        }

        auction.setStatus(AuctionStatus.CANCELED);
        auctionDao.update(auction);
        manager.removeAuction(auctionId);
        eventBus.publishAuctionStatusChanged(auction, "CANCELED");
    }

    // =========================================================================
    // GET DETAIL
    // =========================================================================
    public AuctionResponse getDetail(String auctionId) {
        Auction auction = getOrThrow(auctionId);
        Item item = itemDao.findById(auction.getItemId());

        AuctionResponse response = new AuctionResponse();
        response.setAuctionId(auction.getId());
        response.setTitle(item != null ? item.getTitle() : "Không xác định");
        response.setDescription(item != null ? item.getDescription() : "Không xác định");
        response.setCategory((item != null && item.getCategory() != null) ? item.getCategory().name() : "N/A");
        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setHighestBidderName(auction.getCurrentLeader());
        response.setSellerId(auction.getSellerId());
        response.setWinnerId(auction.getWinnerId());
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        response.setTimeRemaining(calcTimeRemaining(auction));
        response.setMinBidIncrement(auction.getMinBidIncrement());
        response.setBidCount(auction.getBidCount());

        try {
            List<BidTransaction> bids = bidDao.findByAuctionId(auctionId);
            if (bids != null) {
                response.setRecentBids(new ArrayList<>(bids));
            }
        } catch (Exception e) {
            System.err.println("[AuctionService.getDetail] Không load được bid history: " + e.getMessage());
        }

        return response;
    }

    // =========================================================================
    // GET LIST
    // =========================================================================
    public List<AuctionResponse> getList(String statusStr, int page, int size) {
        AuctionStatus statusEnum = null;
        if (statusStr != null && !statusStr.trim().isEmpty() && !statusStr.equalsIgnoreCase("ALL")) {
            try {
                statusEnum = AuctionStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AuctionException("INVALID_STATUS",
                    "Trạng thái phiên đấu giá không hợp lệ: " + statusStr);
            }
        }

        int offset = page * size;
        List<Auction> auctions = auctionDao.findAuctions(statusEnum, offset, size);

        List<AuctionResponse> responseList = new ArrayList<>();
        if (auctions != null) {
            for (Auction auction : auctions) {
                responseList.add(mapToResponse(auction));
            }
        }
        return responseList;
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private AuctionResponse mapToResponse(Auction auction) {
        Item item = itemDao.findById(auction.getItemId());
        AuctionResponse response = new AuctionResponse();
        response.setAuctionId(auction.getId());
        response.setTitle(item != null ? item.getTitle() : "Không xác định");
        response.setDescription(item != null ? item.getDescription() : "Không xác định");
        response.setCategory((item != null && item.getCategory() != null) ? item.getCategory().name() : "N/A");
        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setHighestBidderName(auction.getCurrentLeader());
        response.setSellerId(auction.getSellerId());
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        response.setTimeRemaining(calcTimeRemaining(auction));
        response.setBidCount(auction.getBidCount());
        return response;
    }

    private long calcTimeRemaining(Auction auction) {
        if (auction.getEndTime() == null) return 0;
        long seconds = java.time.Duration.between(LocalDateTime.now(), auction.getEndTime()).toSeconds();
        return Math.max(0, seconds);
    }

    private Auction getOrThrow(String id) {
        Auction a = manager.getAuction(id);
        if (a != null) return a;
        a = auctionDao.findById(id);
        if (a == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + id);
        }
        return a;
    }
}