package com.auction.server.service;

import com.auction.server.dao.AutoBidDAO;
import com.auction.server.dao.BidTransactionDAO;
import com.auction.server.observer.AuctionObserver;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.AutoBidRequest;
import com.auction.shared.dto.request.BidRequest;
import com.auction.shared.dto.response.AutoBidResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AutoBidSetting;
import com.auction.shared.model.BidTransaction;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoBidService xử lý logic Đặt giá tự động (Proxy Bidding).
 * Đảm bảo nguyên tắc: Người đăng ký trước được ưu tiên nếu trùng giá.
 * Chống vòng lặp vô hạn (Infinite Loop) khi 2 Auto-bidder đấu nhau.
 */
public class AutoBidService implements AuctionObserver {

    // PriorityQueue: Ưu tiên người đăng ký sớm (So sánh theo thời gian)
    private static final Comparator<AutoBidSetting> BY_TIME = Comparator.comparing(AutoBidSetting::getRegisteredAt);
    private final Map<String, PriorityQueue<AutoBidSetting>> queues = new ConcurrentHashMap<>();

    // Concurrent Set: Khóa mềm chống đệ quy vòng lặp khi auto-bid trigger liên tục
    private final Set<String> processing = ConcurrentHashMap.newKeySet();

    private final BidService bidService;
    private final AutoBidDAO autoBidDao;
    private final BidTransactionDAO bidDao;
    private final AuctionManager manager = AuctionManager.getInstance();

    public AutoBidService(BidService bidService, AutoBidDAO autoBidDao, BidTransactionDAO bidDao) {
        this.bidService = bidService;
        this.autoBidDao = autoBidDao;
        this.bidDao = bidDao;
    }

    // === 1. NGƯỜI DÙNG ĐĂNG KÝ AUTO-BID ===
    public AutoBidResponse register(AutoBidRequest req, String token) {
        String bidderId = TokenUtil.getUserId(token);
        if (bidderId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        Auction auction = manager.getAuction(req.getAuctionId());
        if (auction == null || auction.getStatus() != AuctionStatus.RUNNING) {
            throw new AuctionException("INVALID_AUCTION", "Phiên không tồn tại hoặc chưa mở.");
        }

        if (req.getMaxBidAmount() <= auction.getCurrentPrice()) {
            throw new AuctionException("INVALID_BID", "Giá tối đa phải cao hơn giá hiện tại của sản phẩm.");
        }

        // Tạo cấu hình Auto-Bid
        // Tạo cấu hình Auto-Bid
        AutoBidSetting setting = new AutoBidSetting(
                UUID.randomUUID().toString(),
                bidderId,
                req.getAuctionId(),
                req.getMaxBidAmount(),     // Sửa dòng này
                req.getIncrementAmount(),  // Sửa dòng này
                true,
                LocalDateTime.now()
        );

        autoBidDao.save(setting);

        // Đưa vào hàng đợi ưu tiên của phiên đấu giá đó
        queues.computeIfAbsent(req.getAuctionId(), k -> new PriorityQueue<>(BY_TIME)).add(setting);

        // Kiểm tra xem user này có đang dẫn đầu không
        Optional<BidTransaction> topBid = bidDao.findHighestBid(req.getAuctionId());
        boolean alreadyWinning = topBid.isPresent() && topBid.get().getBidderId().equals(bidderId);

        // Nếu chưa dẫn đầu và giá vẫn nằm trong ngân sách -> Kích hoạt Auto-bid ngay lập tức
        double nextPrice = auction.getCurrentPrice() + setting.getIncrement();
        if (!alreadyWinning && nextPrice <= setting.getMaxBid()) {
            triggerAutoBid(req.getAuctionId(), auction.getCurrentPrice());
        }

        return new AutoBidResponse(true, "Đăng ký Auto-Bid thành công. Hệ thống sẽ tự động đặt giá giúp bạn!");
    }

    // === 2. EVENT LISTENER: ĐƯỢC GỌI KHI CÓ NGƯỜI ĐẶT GIÁ MỚI ===
    public void onBidPlaced(String auctionId, double currentPrice, boolean isAutoBid) {
        // TỐI ƯU CỰC MẠNH: Nếu lượt bid vừa rồi CŨNG LÀ auto-bid -> BỎ QUA để chống vòng lặp vô hạn
        if (isAutoBid) return;

        // Nếu là bid thủ công, đánh thức các Auto-bidder khác
        triggerAutoBid(auctionId, currentPrice);
    }

    // === 3. LOGIC LÕI XỬ LÝ ĐUA GIÁ ===
    private void triggerAutoBid(String auctionId, double currentPrice) {
        // KHÓA MỀM (Soft Lock): Đảm bảo tại 1 thời điểm chỉ có 1 luồng xử lý auto-bid cho phiên này
        if (!processing.add(auctionId)) return;

        try {
            PriorityQueue<AutoBidSetting> queue = queues.get(auctionId);
            if (queue == null || queue.isEmpty()) return;

            Auction auction = manager.getAuction(auctionId);
            if (auction == null) return;

            // Xác định ai đang dẫn đầu thực sự trong DB
            Optional<BidTransaction> topBid = bidDao.findHighestBid(auctionId);
            String currentLeaderId = topBid.map(BidTransaction::getBidderId).orElse(null);

            for (AutoBidSetting s : queue) {
                if (!s.isActive()) continue;

                // Nếu auto-bidder này ĐÃ dẫn đầu rồi -> Không tự đẩy giá lên nữa (Tránh tự chém vào chân)
                if (s.getBidderId().equals(currentLeaderId)) continue;

                double nextPrice = currentPrice + s.getIncrement();

                // Nếu giá tiếp theo vượt quá ngân sách -> Tắt auto-bid của user này
                if (nextPrice > s.getMaxBid()) {
                    s.setActive(false);
                    autoBidDao.update(s);
                    // (Tùy chọn) Gọi Socket gửi Push Notification thông báo cho user "Bạn đã hết tiền"
                    continue;
                }

                // CHUẨN BỊ ĐẶT GIÁ
                BidRequest systemBid = new BidRequest(auctionId, s.getBidderId(), nextPrice, true);

                try {
                    // Gọi BidService để đặt giá tự động (System Bypass Token)
                    bidService.placeSystemBid(systemBid);
                    break; // CHÚ Ý: Chỉ xử lý 1 lượt bid cho 1 người, sau đó nhường CPU/vòng lặp cho Request khác
                } catch (Exception e) {
                    s.setActive(false);
                    autoBidDao.update(s);
                }
            }
        } finally {
            // LUÔN LUÔN NHẢ KHÓA dù có lỗi xảy ra
            processing.remove(auctionId);
        }
    }

    // Cleanup khi đóng phiên
    public void onAuctionClosed(String auctionId) {
        queues.remove(auctionId);
        processing.remove(auctionId);
    }

    // =================================================================
    // 2. EVENT LISTENER (Tuân thủ hợp đồng AuctionObserver)
    // =================================================================

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        // TỐI ƯU CỰC MẠNH: Nếu lượt bid vừa rồi CŨNG LÀ auto-bid -> BỎ QUA để chống vòng lặp vô hạn
        if (bid.isAutoBid()) return;

        // Nếu là bid thủ công, đánh thức các Auto-bidder khác
        triggerAutoBid(auction.getId(), auction.getCurrentPrice());
    }

    @Override
    public void onAuctionStarted(Auction auction) {
        // AutoBidService không cần quan tâm lúc phiên mới mở, để trống.
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        // Dọn dẹp RAM khi phiên kết thúc
        queues.remove(auction.getId());
        processing.remove(auction.getId());
    }

    @Override
    public void onAuctionExtended(Auction auction, long extraSeconds) {
        // AutoBidService không bị ảnh hưởng bởi việc gia hạn thời gian, để trống.
    }

    @Override
    public void onError(Auction auction, String errorCode, String message) {
        // Có thể in ra log để debug nếu cần
        System.err.println("AutoBidService nhận được lỗi từ phiên " + auction.getId() + ": " + message);
    }

    // =================================================================
    // 3. LOGIC LÕI XỬ LÝ ĐUA GIÁ (Giữ nguyên phần code triggerAutoBid của bạn ở dưới đây)
    // =================================================================

    // === 4. NGƯỜI DÙNG HỦY AUTO-BID ===
    public AutoBidResponse cancel(String auctionId, String token) {
        // 1. Xác thực người dùng thông qua Token
        String bidderId = TokenUtil.getUserId(token);
        if (bidderId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        // 2. Lấy hàng đợi ưu tiên của phiên đấu giá hiện tại
        PriorityQueue<AutoBidSetting> queue = queues.get(auctionId);

        if (queue != null) {
            // TỐI ƯU CONCURRENCY: Khóa queue lại khi xóa để tránh lỗi ConcurrentModificationException
            // trong trường hợp luồng khác (triggerAutoBid) đang duyệt qua danh sách này.
            synchronized (queue) {
                queue.removeIf(setting -> {
                    // Nếu tìm thấy cấu hình của user này
                    if (setting.getBidderId().equals(bidderId)) {
                        // 3. Tắt trạng thái kích hoạt và cập nhật xuống DB
                        setting.setActive(false);
                        autoBidDao.update(setting);
                        return true; // Xóa khỏi bộ nhớ RAM
                    }
                    return false;
                });
            }
        }

        // Trả về thông báo thành công
        return new AutoBidResponse(true, "Đã hủy đăng ký Auto-bid thành công!");
    }
}
