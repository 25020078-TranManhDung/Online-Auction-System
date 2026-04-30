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
 */
public class BidService {

    private final BidTransactionDAO bidDao;
    private final AuctionDAO auctionDao;
    private final UserDAO userDao;

    private final AuctionEventBus eventBus = AuctionEventBus.getInstance();
    private final AuctionManager manager = AuctionManager.getInstance();

    // TỐI ƯU 1: Lock riêng biệt theo từng AuctionId.
    // Giúp 100 người đặt giá ở Phiên A không làm chặn 100 người đang đặt giá ở Phiên B.
    private final Map<String, Object> auctionLocks = new ConcurrentHashMap<>();

    public BidService(BidTransactionDAO bidDao, AuctionDAO auctionDao, UserDAO userDao) {
        this.bidDao = bidDao;
        this.auctionDao = auctionDao;
        this.userDao = userDao;
    }

    public BidResponse placeBid(BidRequest req, String token) {
        // 1. Xác thực Token
        String bidderId = TokenUtil.getUserId(token);
        if (bidderId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }

        String auctionId = req.getAuctionId();
        double amount = req.getAmount();

        // 2. Lấy đối tượng Lock riêng của phiên đấu giá này (Nếu chưa có thì tạo mới an toàn)
        Object lock = auctionLocks.computeIfAbsent(auctionId, k -> new Object());

        // CHỐNG RACE CONDITION (Concurrency)
        synchronized (lock) {

            // 3. Lấy Auction từ RAM (Manager) để truy xuất siêu tốc độ
            Auction auction = manager.getAuction(auctionId);
            if (auction == null) {
                // Đề phòng trường hợp Cache miss, gọi fallback xuống DB
                auction = auctionDao.findById(auctionId);
                if (auction == null) {
                    throw new AuctionException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.");
                }
            }

            // 4. Kiểm tra trạng thái phiên
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new AuctionException("AUCTION_CLOSED", "Phiên đấu giá đã đóng hoặc chưa bắt đầu.");
            }

            // 5. Ngăn người bán tự đẩy giá (Shill Bidding)
            if (auction.getSellerId().equals(bidderId)) {
                throw new AuctionException("INVALID_BID", "Người bán không thể tự đặt giá cho sản phẩm của mình.");
            }

            // Lấy thông tin người đấu giá (Đã fix lỗi NullPointerException tiềm ẩn)
            User bidder = userDao.findById(bidderId);
            if (bidder == null) {
                throw new AuctionException("USER_NOT_FOUND", "Tài khoản người dùng không tồn tại.");
            }

            // 6. Xây dựng đối tượng Transaction
            BidTransaction newBid = new BidTransaction(
                    UUID.randomUUID().toString(),
                    auctionId,
                    bidderId,
                    bidder.getUsername(),
                    amount,
                    LocalDateTime.now(),
                    req.isAutoBid() // Dựa vào yêu cầu nếu nhóm có tích hợp auto-bid
            );

            // 7. Gọi hàm xử lý logic kinh tế đã định nghĩa sẵn trong class Auction
            boolean isValidBid = auction.addBidTransaction(newBid);

            if (!isValidBid) {
                throw new AuctionException("INSUFFICIENT_BID",
                        "Mức giá phải lớn hơn hoặc bằng Giá hiện tại (" + auction.getCurrentPrice() + ") + Bước giá tối thiểu (" + auction.getMinBidIncrement() + ")");
            }

            // 8. Cập nhật vào Cơ sở dữ liệu
            bidDao.save(newBid);
            auctionDao.update(auction); // Cập nhật lại giá hiện tại và currentLeader xuống DB

            // 9. Tính năng Anti-Sniping (Chống bắn tỉa giây cuối)
            checkAndExtend(auction);

            // 10. Broadcast Realtime: Thông báo cho toàn bộ Socket Client đang theo dõi phiên
            eventBus.publishBidPlaced(auction, newBid);

            // 11. Trả về Response
            BidResponse response = new BidResponse();
            response.setAuctionId(auctionId);
            response.setBidderId(bidderId);
            response.setBidderName(bidder.getUsername());
            response.setAmount(amount); // Mức giá user vừa đặt

            // Sửa thành setNewCurrentPrice cho đúng với thiết kế của DTO
            response.setNewCurrentPrice(auction.getCurrentPrice());

            return response;

        } // Kết thúc đồng bộ hóa (Unlock)
    }

    // Dành riêng cho hệ thống Auto-Bid (Bỏ qua khâu check token)
    public void placeSystemBid(BidRequest req) {
        // Tái sử dụng logic kiểm tra bằng cách truyền giả một chuỗi token (nếu bạn tự thiết kế Token đặc biệt cho Admin)
        // HOẶC tách toàn bộ logic từ dòng số 2 của placeBid cũ ra một hàm private placeBidInternal(req, bidderId)
        // rồi cả 2 hàm đều gọi vào đó.

        // (Đây là bài tập nhỏ về Refactoring cho nhóm, bạn hãy tách code để tránh lặp code nhé!)
    }

    /**
     * TỐI ƯU 2: Anti-sniping mechanism (Gia hạn thời gian)
     * Nếu có người đặt giá trong vòng 30 giây cuối cùng, thời gian sẽ tự động cộng thêm 60 giây.
     */
    private void checkAndExtend(Auction auction) {
        long remainingSeconds = java.time.temporal.ChronoUnit.SECONDS.between(
                LocalDateTime.now(), auction.getEndTime()
        );

        if (remainingSeconds > 0 && remainingSeconds <= 30) {
            auction.setEndTime(auction.getEndTime().plusSeconds(60));
            auctionDao.update(auction); // Cập nhật DB

            // Bắn sự kiện cập nhật thời gian
            // (Nếu nhóm chưa khai báo publishAuctionExtended thì cứ để tạm comment)
            // eventBus.publishAuctionExtended(auction);
        }
    }

    public List<BidTransaction> getHistory(String auctionId) {
        // 1. Kiểm tra phiên đấu giá có tồn tại không thông qua Manager (Cache)
        Auction auction = manager.getAuction(auctionId);
        if (auction == null) {
            // Fallback xuống DB nếu không thấy trong Cache
            auction = auctionDao.findById(auctionId);
            if (auction == null) {
                throw new AuctionException("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.");
            }
        }

        // 2. Lấy danh sách giao dịch từ DAO
        // Thông thường danh sách này nên được sắp xếp theo thời gian (ASC) để vẽ biểu đồ
        List<BidTransaction> history = bidDao.findByAuctionId(auctionId);

        return history != null ? history : new ArrayList<>();
    }
}
