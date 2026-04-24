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
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

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

    // Tích hợp Design Pattern (Singleton & Observer)
    private final AuctionEventBus eventBus = AuctionEventBus.getInstance();
    private final AuctionManager manager = AuctionManager.getInstance();

    public AuctionService(AuctionDAO auctionDao, ItemDAO itemDao, BidTransactionDAO bidDao, UserDAO userDao) {
        this.auctionDao = auctionDao;
        this.itemDao = itemDao;
        this.bidDao = bidDao;
        this.userDao = userDao;
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
        // (Nếu nhóm chưa code ItemFactory, bạn có thể tạo tạm Item thủ công ở đây)
        String newItemId = UUID.randomUUID().toString();
        /* GỢI Ý DÙNG FACTORY:
        Item newItem = ItemFactory.createItem(req.getCategory(), req.getItemAttributes());
        newItem.setId(newItemId);
        newItem.setTitle(req.getTitle());
        newItem.setDescription(req.getDescription());
        itemDao.save(newItem);
        */

        // 3. Khởi tạo phiên đấu giá
        Auction auction = new Auction();
        auction.setId(UUID.randomUUID().toString()); // Tạo ID chuỗi
        auction.setItemId(newItemId); // Sử dụng ID của Item vừa tạo ở trên

        // Đã sửa lại thành getStartingPrice() cho khớp với DTO
        auction.setStartPrice(req.getStartingPrice());
        auction.setCurrentPrice(req.getStartingPrice());

        // Xử lý MinBidIncrement bị thiếu trong DTO (Ví dụ: Set mặc định bước giá là 5% giá khởi điểm)
        double defaultIncrement = req.getStartingPrice() * 0.05;
        auction.setMinBidIncrement(defaultIncrement);

        auction.setStartTime(req.getStartTime());
        auction.setEndTime(req.getEndTime());
        auction.setStatus(AuctionStatus.OPEN);

        // 4. Lưu vào Database
        boolean isSaved = auctionDao.save(auction);
        if (!isSaved) {
            throw new RuntimeException("Đã xảy ra lỗi hệ thống khi lưu phiên đấu giá.");
        }

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

        // Thêm sellerId (lấy từ biến sellerId đã xác thực ở đầu hàm createAuction)
        response.setSellerId(sellerId);

        return response;
    }

    public AuctionResponse startAuction(String auctionId, String token) {
        // Kiểm tra token cơ bản
        if (!TokenUtil.isValid(token)) {
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
     */
    public synchronized void closeAuction(String auctionId) {
        Auction auction = getOrThrow(auctionId);

        if (auction.getStatus() != AuctionStatus.RUNNING) {
            return; // Nếu đã đóng hoặc chưa chạy thì bỏ qua
        }

        // Xác định người thắng cuộc (Giả định DAO trả về Object thay vì Optional để tránh lỗi như UserService)
        // Khai báo đúng kiểu Optional mà DAO trả về
        java.util.Optional<BidTransaction> topBidOpt = bidDao.findHighestBid(auctionId);

        // Kiểm tra xem có người đặt giá không (thay cho việc check != null)
        if (topBidOpt.isPresent()) {
            BidTransaction topBid = topBidOpt.get(); // Lấy đối tượng thật ra khỏi hộp Optional
            auction.setWinnerId(topBid.getBidderId()); // ID lưu vào database để tham chiếu
            auction.setCurrentLeader(topBid.getBidderName()); // Tên hiển thị ra giao diện cho đẹp
            auction.setCurrentPrice(topBid.getAmount());
        }

        auction.setStatus(AuctionStatus.FINISHED);
        auctionDao.update(auction);

        // Dọn dẹp RAM và thông báo kết thúc
        manager.removeAuction(auctionId);
        eventBus.publishAuctionClosed(auction);
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
        response.setHighestBidderName(auction.getCurrentLeader()); // Lấy tên người đang dẫn đầu
        response.setSellerId(auction.getSellerId());

        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        response.setStatus(auction.getStatus());

        return response;
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
}
