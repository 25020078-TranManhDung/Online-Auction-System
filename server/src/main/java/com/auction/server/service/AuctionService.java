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
 * Tích hợp Singleton (Manager, EventBus) và TokenUtil để kiểm duyệt phân quyền.
 */
public class AuctionService {

    // Dependency Injection cho tầng DAO
    private final AuctionDAO auctionDao;
    private final ItemDAO itemDao;
    private final BidTransactionDAO bidDao;
    private final UserDAO userDao;

    private final WalletService walletService;

    // Tích hợp Design Pattern (Singleton & Observer)
    private final AuctionEventBus eventBus = AuctionEventBus.getInstance();
    private final AuctionManager manager = AuctionManager.getInstance();

    public AuctionService(AuctionDAO auctionDao, ItemDAO itemDao, BidTransactionDAO bidDao, UserDAO userDao, WalletService walletService) {
        this.auctionDao = auctionDao;
        this.itemDao = itemDao;
        this.bidDao = bidDao;
        this.userDao = userDao;
        this.walletService = walletService;
    }

    public AuctionResponse createAuction(CreateAuctionRequest req, String token) {
        // 1. Xác thực Seller
        String sellerId = TokenUtil.getUserId(token);
        if (sellerId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }

        User seller = userDao.findById(sellerId);
        if (seller == null || seller.getRole() != UserRole.SELLER) {
            throw new AuctionException("PERMISSION_DENIED", "Chỉ Seller mới được quyền tạo phiên đấu giá.");
        }

        // 2. ÁP DỤNG FACTORY PATTERN: Tạo Item mới từ Request
        // Tạo Map chứa dữ liệu để ném vào Factory
        java.util.Map<String, Object> itemData = new java.util.HashMap<>();
        itemData.put("title", req.getTitle());
        itemData.put("description", req.getDescription());
        itemData.put("category", req.getCategory());
        itemData.put("sellerId", sellerId); // Set luôn người bán để Factory nhét vào Item

        // Gọi Factory đẻ ra đối tượng Item (Đã tự động được cấp UUID bên trong Factory)
        Item newItem = com.auction.server.pattern.factory.ItemFactory.createItem(req.getCategory(), itemData);

        // Lấy ID vừa được Factory sinh ra
        String newItemId = newItem.getId();

        // BẮT BUỘC: LƯU SẢN PHẨM VÀO DATABASE TRƯỚC ĐỂ LÀM KHÓA CHÍNH!
        boolean isItemSaved = itemDao.save(newItem);
        if (!isItemSaved) {
            throw new RuntimeException("Lỗi: Không thể lưu thông tin Sản phẩm vào Database!");
        }

        // 3. Khởi tạo phiên đấu giá
        Auction auction = new Auction();
        auction.setId(UUID.randomUUID().toString()); // Tạo ID chuỗi
        auction.setItemId(newItemId); // Sử dụng ID của Item vừa tạo ở trên

        auction.setSellerId(sellerId);

        // 👉 CHỐT THỜI GIAN HIỆN TẠI VÀO BIẾN 'now'
        LocalDateTime now = java.time.LocalDateTime.now();
        auction.setStartTime(now);

        // Đã sửa lại thành getStartingPrice() cho khớp với DTO
        auction.setStartPrice(req.getStartingPrice());
        auction.setCurrentPrice(req.getStartingPrice());

        // BUG FIX: Dùng minBidIncrement từ Client thay vì tính mặc định 5%
        // Nếu Client không gửi (= 0), fallback về 5% giá khởi điểm
        double increment = req.getMinBidIncrement() > 0
            ? req.getMinBidIncrement()
            : Math.max(1, req.getStartingPrice() * 0.05);
        auction.setMinBidIncrement(increment);

        // BUG FIX: Dùng durationMinutes từ Client để tính endTime chính xác
        if (req.getEndTime() != null) {
            auction.setEndTime(req.getEndTime());
        } else {
            auction.setEndTime(now.plusMinutes(req.getDurationMinutes()));
        }

        auction.setStatus(AuctionStatus.OPEN);
        // 4. Lưu vào Database
        boolean isSaved = auctionDao.save(auction);
        if (!isSaved) {
            throw new RuntimeException("Đã xảy ra lỗi hệ thống khi lưu phiên đấu giá.");
        }

        // FIX: Thêm vào AuctionManager để AuctionTimerService scan được và tự động OPEN→RUNNING
        manager.addAuction(auction);

        // Khởi tạo DTO phản hồi
        AuctionResponse response = new AuctionResponse();

        // Map dữ liệu từ Auction và Request sang DTO
        response.setAuctionId(auction.getId());
        response.setTitle(req.getTitle());
        response.setDescription(req.getDescription());
        response.setCategory(req.getCategory() != null ? req.getCategory().name() : "N/A");
        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setHighestBidderName(null); // Phiên mới tạo chưa có ai đặt giá
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        response.setTimeRemaining(calcTimeRemaining(auction)); // BUG FIX: client cần để đếm ngược

        // Thêm sellerId (lấy từ biến sellerId đã xác thực ở đầu hàm createAuction)
        response.setSellerId(sellerId);

        return response;
    }

    public AuctionResponse startAuction(String auctionId, String token) {
        // Cho phép gọi nội bộ (từ AuctionTimerService) với token = null
        if (token != null && !TokenUtil.isValid(token)) {
            throw new AuctionException("UNAUTHORIZED", "Bạn cần đăng nhập để thực hiện thao tác này.");
        }

        Auction auction = getOrThrow(auctionId);

        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionException("INVALID_STATUS", "Phiên đấu giá không ở trạng thái OPEN.");
        }

        // Chuyển trạng thái
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setStartTime(LocalDateTime.now());
        auctionDao.update(auction);

        // TỐI ƯU 1: Đưa vào in-memory cache (Manager) để xử lý Bidding tốc độ cao
        manager.addAuction(auction);

        // TỐI ƯU 2: Broadcast sự kiện realtime cho các Client (Observer Pattern)
        eventBus.publishAuctionStarted(auction);

        // Khởi tạo DTO phản hồi
        AuctionResponse response = new AuctionResponse();

        // Lấy thông tin sản phẩm (Item) để lấy Tên, Mô tả, Thể loại...
        Item item = itemDao.findById(auction.getItemId());

        // Map dữ liệu vào Response
        response.setAuctionId(auction.getId());
        response.setTitle(item != null ? item.getTitle() : "Không xác định");
        response.setDescription(item != null ? item.getDescription() : "Không xác định");

        // Kiểm tra enum category để tránh lỗi NullPointerException
        if (item != null && item.getCategory() != null) {
            response.setCategory(item.getCategory().name());
        } else {
            response.setCategory("N/A");
        }

        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        // Phiên mới bắt đầu nên chưa có người thắng / người ra giá cao nhất
        response.setHighestBidderName(null);

        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());

        return response;
    }

    /**
     * TỐI ƯU 3: Dùng 'synchronized' để chặn Race Condition (2 luồng cùng đóng 1 phiên)
     * THAY ĐỔI: Hàm này giờ chỉ đổi trạng thái sang FINISHED và lưu winner,
     * KHÔNG trừ tiền hay chia tiền ngay lập tức (Chờ Confirm Payment).
     */
    public synchronized void closeAuction(String auctionId) {
        Auction auction = getOrThrow(auctionId);

        if (auction.getStatus() != AuctionStatus.RUNNING) {
            return; // Nếu đã đóng hoặc chưa chạy thì bỏ qua
        }

        // Xác định người thắng cuộc (Lấy từ Bid mới nhất)
        java.util.Optional<BidTransaction> topBidOpt = bidDao.findHighestBid(auctionId);

        if (topBidOpt.isPresent()) {
            BidTransaction topBid = topBidOpt.get();
            auction.setWinnerId(topBid.getBidderId()); // ID lưu vào database để chờ thanh toán
            auction.setCurrentLeader(topBid.getBidderName());
            auction.setCurrentPrice(topBid.getAmount());

            // XÓA BỎ đoạn gọi walletService.settleAuction ở đây!
        }

        // Chuyển sang trạng thái chờ thanh toán
        auction.setStatus(AuctionStatus.FINISHED);
        auctionDao.update(auction);

        // Dọn dẹp RAM và thông báo kết thúc
        manager.removeAuction(auctionId);
        eventBus.publishAuctionClosed(auction);
    }

    /**
     * CẬP NHẬT: Xác nhận thanh toán (Thay thế cho markAsPaid cũ).
     * Chỉ người thắng cuộc mới được gọi.
     * @param auctionId  ID phiên đấu giá
     * @param token      Token của người gọi (phải là Winner)
     */
    public void confirmPayment(String auctionId, String token) {
        String requesterId = TokenUtil.getUserId(token);
        if (requesterId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        Auction auction = auctionDao.findById(auctionId);
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        // 1. Kiểm tra trạng thái và quyền hạn
        if (auction.getStatus() != AuctionStatus.FINISHED) {
            throw new AuctionException("INVALID_STATE", "Chỉ có thể thanh toán khi phiên ở trạng thái FINISHED.");
        }
        if (!requesterId.equals(auction.getWinnerId())) {
            throw new UnauthorizedException("Chỉ người chiến thắng mới có quyền xác nhận thanh toán.");
        }

        // 2. Tiến hành giao dịch tài chính (Chính thức trừ tiền Hold, cộng cho Seller)
        try {
            String adminId = walletService.findAdminId();
            walletService.settleAuction(
                    requesterId,              // ID người thắng (đang bị hold tiền)
                    auction.getCurrentPrice(), // Số tiền thắng
                    auction.getSellerId(),     // ID người bán
                    auctionId,                 // ID phiên đấu giá
                    adminId                    // ID admin nhận hoa hồng
            );
        } catch (Exception e) {
            System.err.println("[AuctionService.confirmPayment] Lỗi hệ thống Ví: " + e.getMessage());
            throw new AuctionException("PAYMENT_FAILED", "Không thể thanh toán: " + e.getMessage());
        }

        // 3. Cập nhật trạng thái phiên đấu giá
        auction.setStatus(AuctionStatus.PAID);
        auctionDao.update(auction);
        eventBus.publishAuctionStatusChanged(auction, "PAID");
    }

    /**
     * Hủy phiên đấu giá: bất kỳ trạng thái nào (trừ PAID) → CANCELED.
     * Chỉ Admin mới được gọi.
     * @param auctionId  ID phiên đấu giá
     * @param token      Token của Admin
     */
    public void cancelAuction(String auctionId, String token) {
        String requesterId = TokenUtil.getUserId(token);
        if (requesterId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        // Kiểm tra quyền Admin
        String role = TokenUtil.getRole(token);
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Chỉ Admin mới có quyền hủy phiên đấu giá.");
        }

        Auction auction = auctionDao.findById(auctionId);
        if (auction == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND",
                "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        // Phiên đã PAID thì không được hủy
        if (auction.getStatus() == AuctionStatus.PAID) {
            throw new AuctionException("INVALID_STATE",
                "Không thể hủy phiên đấu giá đã được thanh toán.");
        }

        auction.setStatus(AuctionStatus.CANCELED);
        auctionDao.update(auction);

        // Nếu phiên đang chạy trên RAM thì dọn dẹp luôn
        manager.removeAuction(auctionId);
        eventBus.publishAuctionStatusChanged(auction, "CANCELED");
    }

    public AuctionResponse getDetail(String auctionId) {
        Auction auction = getOrThrow(auctionId);
        Item item = itemDao.findById(auction.getItemId());

        AuctionResponse response = new AuctionResponse();

        // Map dữ liệu an toàn
        response.setAuctionId(auction.getId());
        response.setTitle(item != null ? item.getTitle() : "Không xác định");
        response.setDescription(item != null ? item.getDescription() : "Không xác định");
        response.setCategory((item != null && item.getCategory() != null) ? item.getCategory().name() : "N/A");

        response.setStartingPrice(auction.getStartPrice());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setHighestBidderName(auction.getCurrentLeader());
        response.setSellerId(auction.getSellerId());
        response.setWinnerId(auction.getWinnerId());   // ← FIX: trả winnerId về client

        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());
        response.setTimeRemaining(calcTimeRemaining(auction));

        // FIX: Thêm các trường bị thiếu khiến client không hiển thị được
        response.setMinBidIncrement(auction.getMinBidIncrement()); // bước giá tối thiểu
        response.setBidCount(auction.getBidCount());               // số lượt đặt giá

        // Load lịch sử đặt giá từ DB để hiển thị trên client
        try {
            List<com.auction.shared.model.BidTransaction> bids = bidDao.findByAuctionId(auctionId);
            if (bids != null) {
                response.setRecentBids(new java.util.ArrayList<>(bids));
            }
        } catch (Exception e) {
            System.err.println("[AuctionService.getDetail] Không load được bid history: " + e.getMessage());
        }

        return response;
    }

    // Hàm tiện ích: Tính số giây còn lại của phiên đấu giá
    private long calcTimeRemaining(Auction auction) {
        if (auction.getEndTime() == null) return 0;
        long seconds = java.time.Duration.between(LocalDateTime.now(), auction.getEndTime()).toSeconds();
        return Math.max(0, seconds);
    }

    // Hàm tiện ích: Tối ưu truy xuất dữ liệu
    private Auction getOrThrow(String id) {
        // Ưu tiên 1: Lấy từ RAM (Cache) trước cho tốc độ chớp nhoáng
        Auction a = manager.getAuction(id);
        if (a != null) return a;

        // Ưu tiên 2: Nếu RAM không có (Server restart), truy xuất Database
        a = auctionDao.findById(id);
        if (a == null) {
            throw new ResourceNotFoundException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá có ID: " + id);
        }
        return a;
    }

    public List<AuctionResponse> getList(String statusStr, int page, int size) {
        // 1. Xử lý trạng thái (Parse Enum an toàn)
        AuctionStatus statusEnum = null;

        // Tránh lỗi NullPointerException và cho phép lấy TẤT CẢ (ALL) nếu không truyền status
        if (statusStr != null && !statusStr.trim().isEmpty() && !statusStr.equalsIgnoreCase("ALL")) {
            try {
                statusEnum = AuctionStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Bắt lỗi nếu Client truyền lên một status rác không tồn tại trong hệ thống
                throw new AuctionException("INVALID_STATUS", "Trạng thái phiên đấu giá không hợp lệ: " + statusStr);
            }
        }

        // 2. Tính toán thông số phân trang (Offset)
        int offset = page * size;

        // 3. Gọi DAO để lấy dữ liệu
        // LƯU Ý: Giả định AuctionDAO của bạn có hàm findAuctions nhận vào (status, offset, limit).
        // Nếu DAO của bạn đặt tên khác (ví dụ: findAll, getList), hãy sửa lại tên hàm cho khớp nhé!
        List<Auction> auctions = auctionDao.findAuctions(statusEnum, offset, size);

        // 4. Map dữ liệu từ Entity (Auction) sang DTO (AuctionResponse)
        List<AuctionResponse> responseList = new ArrayList<>();
        if (auctions != null) {
            for (Auction auction : auctions) {
                responseList.add(mapToResponse(auction));
            }
        }

        return responseList;
    }

    private AuctionResponse mapToResponse(Auction auction) {
        AuctionResponse response = new AuctionResponse();

        // Truy vấn ItemDAO để lấy thêm thông tin chi tiết của sản phẩm
        Item item = itemDao.findById(auction.getItemId());

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
        response.setTimeRemaining(calcTimeRemaining(auction)); // BUG FIX
        response.setBidCount(auction.getBidCount());           // BUG FIX: thiếu dòng này khiến bidCount luôn = 0 trong danh sách

        return response;
    }
}