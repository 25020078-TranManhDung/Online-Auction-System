package com.auction.server.service;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.CreateAuctionRequest;
import com.auction.shared.dto.request.UpdateAuctionRequest;
import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.enums.ItemCategory;
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
 *  - updateAuction: [MỚI] Seller sửa thông tin phiên khi còn OPEN
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
    // UPDATE — [MỚI] Seller sửa thông tin phiên khi còn OPEN
    // =========================================================================
    public AuctionResponse updateAuction(UpdateAuctionRequest req, String token) {
        // 1. Xác thực token
        String sellerId = TokenUtil.getUserId(token);
        if (sellerId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }

        // 2. Chỉ SELLER được sửa
        String role = TokenUtil.getRole(token);
        if (!"SELLER".equals(role)) {
            throw new UnauthorizedException("Chỉ Seller mới được quyền sửa phiên đấu giá.");
        }

        // 3. Tìm phiên (ưu tiên DB để tránh cache stale)
        Auction auction = auctionDao.findById(req.getAuctionId());
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + req.getAuctionId());
        }

        // 4. Kiểm tra chủ sở hữu
        if (!sellerId.equals(auction.getSellerId())) {
            throw new UnauthorizedException("Bạn không có quyền sửa phiên đấu giá này.");
        }

        // 5. Chặn nếu không còn OPEN
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionException("INVALID_STATUS",
                "Chỉ có thể sửa phiên đấu giá khi còn ở trạng thái OPEN. "
                    + "Trạng thái hiện tại: " + auction.getStatus());
        }

        // 6. Validate dữ liệu
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new AuctionException("INVALID_REQUEST", "Tên sản phẩm không được để trống.");
        }
        if (req.getStartingPrice() <= 0) {
            throw new AuctionException("INVALID_REQUEST", "Giá khởi điểm phải lớn hơn 0.");
        }
        if (req.getMinBidIncrement() <= 0) {
            throw new AuctionException("INVALID_REQUEST", "Bước giá phải lớn hơn 0.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getEndTime() == null || req.getEndTime().isBefore(now.plusMinutes(5))) {
            throw new AuctionException("INVALID_REQUEST",
                "Thời gian kết thúc phải ít nhất 5 phút từ bây giờ.");
        }
        if (req.getStartTime() != null && req.getEndTime().isBefore(req.getStartTime())) {
            throw new AuctionException("INVALID_REQUEST",
                "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }

        // 7. Cập nhật Item (title, description, category)
        Item item = itemDao.findById(auction.getItemId());
        if (item != null) {
            item.setTitle(req.getTitle());
            item.setDescription(req.getDescription() != null ? req.getDescription() : "");
            if (req.getCategory() != null) {
                try {
                    item.setCategory(ItemCategory.valueOf(req.getCategory()));
                } catch (IllegalArgumentException ignored) {}
            }
            itemDao.update(item);
        }

        // 8. Cập nhật Auction (giá, thời gian)
        auction.setStartPrice(req.getStartingPrice());
        auction.setCurrentPrice(req.getStartingPrice());    // chưa có bid nên current = start
        auction.setMinBidIncrement(req.getMinBidIncrement());
        if (req.getStartTime() != null) {
            auction.setStartTime(req.getStartTime());
        }
        auction.setEndTime(req.getEndTime());

        // 9. Ghi xuống DB — updateBasicInfo tự fail nếu status đã khác OPEN (race condition safe)
        boolean updated = auctionDao.updateBasicInfo(auction);
        if (!updated) {
            throw new AuctionException("INVALID_STATUS",
                "Phiên đấu giá đã chuyển sang trạng thái khác, không thể sửa.");
        }

        // 10. [QUAN TRỌNG] Đồng bộ lại cache để AuctionTimerService dùng đúng startTime mới
        manager.addAuction(auction);

        // 10b. Broadcast cho tất cả bidder đang xem phiên biết có thay đổi
        eventBus.publishAuctionInfoUpdated(auction);

        // 11. Build response
        AuctionResponse response = new AuctionResponse();
        response.setAuctionId(auction.getId());
        response.setTitle(item != null ? item.getTitle() : req.getTitle());
        response.setDescription(item != null ? item.getDescription() : req.getDescription());
        response.setCategory(item != null && item.getCategory() != null ? item.getCategory().name() : "OTHER");
        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setMinBidIncrement(auction.getMinBidIncrement());
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        response.setSellerId(sellerId);
        response.setTimeRemaining(calcTimeRemaining(auction));

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
        }

        auction.setStatus(AuctionStatus.FINISHED);
        auctionDao.update(auction);
        manager.removeAuction(auctionId);
        eventBus.publishAuctionClosed(auction);
    }

    // =========================================================================
    // CONFIRM PAYMENT — [THÊM MỚI] Winner xác nhận → chính thức settle ví
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

        try {
            String adminId = walletService.findAdminId();
            walletService.settleAuction(
                requesterId,
                auction.getCurrentPrice(),
                auction.getSellerId(),
                auctionId,
                adminId
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
    // MARK AS PAID — [GIỮ LẠI] Admin xác nhận thủ công
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
    // CANCEL — [MERGE]
    // =========================================================================
    public void cancelAuction(String auctionId, String token) {
        // token = null khi được gọi từ AuctionTimerService (system auto-cancel)
        boolean isSystemCall = (token == null);

        if (!isSystemCall) {
            String requesterId = TokenUtil.getUserId(token);
            if (requesterId == null) {
                throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
            }
            String role = TokenUtil.getRole(token);
            Auction auctionCheck = auctionDao.findById(auctionId);
            if (auctionCheck != null) {
                boolean isAdmin  = "ADMIN".equals(role);
                boolean isSeller = "SELLER".equals(role) && auctionCheck.getSellerId().equals(requesterId);
                if (!isAdmin && !isSeller) {
                    throw new UnauthorizedException("Bạn không có quyền hủy phiên đấu giá này.");
                }
            }
        }

        Auction auction = auctionDao.findById(auctionId);
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        if (auction.getStatus() == AuctionStatus.PAID) {
            throw new AuctionException("INVALID_STATE", "Không thể hủy phiên đấu giá đã được thanh toán.");
        }

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
    // GET DETAIL — Phiên bản TỐI ƯU SẠCH SẼ (0 extra queries cho Avatar)
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
            // Lấy danh sách lịch sử đặt giá (DAO đã JOIN lấy sẵn kèm Avatar của từng người)
            List<BidTransaction> bids = bidDao.findByAuctionId(auctionId);
            if (bids != null) {
                response.setRecentBids(new ArrayList<>(bids));

                // 🌟 MẸO TỐI ƯU ĐẲNG CẤP: Vì danh sách 'bids' đã được câu lệnh SQL sắp xếp theo
                // ORDER BY amount DESC, nên phần tử đầu tiên (index 0) CHÍNH LÀ lượt đặt giá cao nhất!
                // Ta chỉ việc bốc luôn Avatar của phần tử này gán cho người dẫn đầu (Leader)
                // mà KHÔNG CẦN gọi Database thêm bất kỳ một lần nào nữa.
                if (!bids.isEmpty() && bids.get(0).getBidderAvatar() != null) {
                    response.setHighestBidderAvatar(bids.get(0).getBidderAvatar());
                }
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
        response.setMinBidIncrement(auction.getMinBidIncrement()); // ← FIX: thiếu dòng này khiến bước giá = 0 trong edit dialog
        response.setHighestBidderName(auction.getCurrentLeader());
        response.setSellerId(auction.getSellerId());
        response.setWinnerId(auction.getWinnerId());
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