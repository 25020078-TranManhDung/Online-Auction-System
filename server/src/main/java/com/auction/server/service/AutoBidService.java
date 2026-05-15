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

        AutoBidSetting setting = new AutoBidSetting(
            UUID.randomUUID().toString(),
            bidderId,
            req.getAuctionId(),
            req.getMaxBidAmount(),
            req.getIncrementAmount(),
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

    // === 2. LOGIC LÕI XỬ LÝ ĐUA GIÁ ===
    private void triggerAutoBid(String auctionId, double currentPrice) {
        // KHÓA MỀM (Soft Lock): Đảm bảo tại 1 thời điểm chỉ có 1 luồng xử lý auto-bid cho phiên này
        if (!processing.add(auctionId)) return;

        try {
            runAutoBidCycle(auctionId);
        } finally {
            // LUÔN LUÔN NHẢ KHÓA dù có lỗi xảy ra
            processing.remove(auctionId);
        }
    }

    /**
     * Chạy vòng lặp cascade auto-bid:
     * Mỗi vòng: tìm auto-bidder chưa dẫn đầu + còn trong ngân sách → đặt giá → lặp lại.
     * Dừng khi: không có ai cần phản ứng, hoặc tất cả đã cạn ngân sách.
     *
     * Đây là Proxy Bidding đúng chuẩn:
     * - bidder1 maxBid=7M, bidder2 maxBid=6.5M, increment=100k
     * - bidder2 đặt 6.5M → bidder1 auto đặt 6.6M → bidder2 hết ngân sách → dừng
     * - bidder1 thắng với 6.6M (không phải 7M — chỉ đủ để vượt qua đối thủ)
     */
    private void runAutoBidCycle(String auctionId) {
        // Giới hạn số vòng để chống vòng lặp vô hạn trong trường hợp dữ liệu bất thường
        int maxRounds = 200;

        for (int round = 0; round < maxRounds; round++) {
            PriorityQueue<AutoBidSetting> queue = queues.get(auctionId);
            if (queue == null || queue.isEmpty()) break;

            Auction auction = manager.getAuction(auctionId);
            if (auction == null) break;

            double price = auction.getCurrentPrice();

            // Xác định ai đang dẫn đầu thực sự (theo DB)
            Optional<BidTransaction> topBid = bidDao.findHighestBid(auctionId);
            String currentLeaderId = topBid.map(BidTransaction::getBidderId).orElse(null);

            // Tìm auto-bidder có thể phản ứng (chưa dẫn đầu, còn ngân sách, đăng ký sớm nhất)
            AutoBidSetting candidate = null;
            for (AutoBidSetting s : queue) {  // PriorityQueue duyệt theo thứ tự đăng ký sớm nhất
                if (!s.isActive()) continue;
                if (s.getBidderId().equals(currentLeaderId)) continue; // Đã dẫn đầu rồi

                double nextPrice = price + s.getIncrement();
                if (nextPrice > s.getMaxBid()) {
                    // Hết ngân sách → vô hiệu hóa
                    s.setActive(false);
                    autoBidDao.update(s);
                    continue;
                }

                candidate = s;
                break; // Chọn người đăng ký sớm nhất có thể đặt giá
            }

            if (candidate == null) break; // Không còn ai cần phản ứng → kết thúc cascade

            // Đặt giá cho candidate
            double nextPrice = price + candidate.getIncrement();
            BidRequest systemBid = new BidRequest(auctionId, candidate.getBidderId(), nextPrice, true);

            try {
                bidService.placeSystemBid(systemBid);
                // Tiếp tục vòng lặp: kiểm tra xem có ai phản ứng lại không
            } catch (Exception e) {
                // Giá không hợp lệ hoặc lỗi khác → vô hiệu hóa setting này
                candidate.setActive(false);
                autoBidDao.update(candidate);
            }
        }
    }

    // === 3. KHÔI PHỤC QUEUE TỪ DATABASE SAU KHI SERVER RESTART ===
    /**
     * Được gọi một lần duy nhất từ ServerMain khi khởi động.
     * Nạp lại toàn bộ auto-bid đang active từ DB vào in-memory queues
     * để các phiên RUNNING không bị mất trạng thái auto-bid sau restart.
     */
    public void restoreQueuesFromDatabase(List<Auction> runningAuctions) {
        for (Auction auction : runningAuctions) {
            List<AutoBidSetting> activeSettings = autoBidDao.findActiveByAuction(auction.getId());
            if (!activeSettings.isEmpty()) {
                PriorityQueue<AutoBidSetting> queue =
                    queues.computeIfAbsent(auction.getId(), k -> new PriorityQueue<>(BY_TIME));
                queue.addAll(activeSettings);
                System.out.println("  → Khôi phục " + activeSettings.size()
                    + " auto-bid setting cho phiên: " + auction.getId());
            }
        }
    }

    // === 4. NGƯỜI DÙNG HỦY AUTO-BID ===
    public AutoBidResponse cancel(String auctionId, String token) {
        String bidderId = TokenUtil.getUserId(token);
        if (bidderId == null) {
            throw new AuctionException("UNAUTHORIZED", "Token không hợp lệ.");
        }

        PriorityQueue<AutoBidSetting> queue = queues.get(auctionId);

        if (queue != null) {
            // Khóa queue lại khi xóa để tránh ConcurrentModificationException
            synchronized (queue) {
                queue.removeIf(setting -> {
                    if (setting.getBidderId().equals(bidderId)) {
                        setting.setActive(false);
                        autoBidDao.update(setting);
                        return true;
                    }
                    return false;
                });
            }
        }

        return new AutoBidResponse(true, "Đã hủy đăng ký Auto-bid thành công!");
    }

    // =================================================================
    // EVENT LISTENER (Tuân thủ hợp đồng AuctionObserver)
    // =================================================================

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        // FIX: Không chặn auto-bid cascade ở đây nữa.
        // Trước đây: `if (bid.isAutoBid()) return` → Chặn hoàn toàn cascade giữa 2 auto-bidder.
        // Kết quả sai: bidder1 và bidder2 đứng bằng giá nhau thay vì đẩy lên đến khi 1 người hết ngân sách.
        //
        // Soft-lock `processing.add(auctionId)` trong triggerAutoBid đã đủ để:
        // - Chống vòng lặp đệ quy vô hạn (chỉ 1 luồng xử lý tại 1 thời điểm)
        // - runAutoBidCycle() giới hạn maxRounds=200 để an toàn tuyệt đối
        //
        // Nếu bid vừa rồi đang trong quá trình xử lý (processing set còn giữ lock),
        // triggerAutoBid() sẽ tự return ngay → không có double-processing.
        triggerAutoBid(auction.getId(), auction.getCurrentPrice());
    }

    @Override
    public void onAuctionStarted(Auction auction) {        // AutoBidService không cần quan tâm lúc phiên mới mở, để trống.
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        // Dọn dẹp RAM khi phiên kết thúc
        queues.remove(auction.getId());
        processing.remove(auction.getId());
    }

    @Override
    public void onAuctionInfoUpdated(Auction auction) {
        // AutoBidService không cần xử lý khi seller cập nhật thông tin phiên
    }

    @Override
    public void onAuctionStatusChanged(Auction auction, String newStatus) {
        // Khi phiên chuyển PAID hoặc CANCELED: dọn dẹp queue nếu còn sót
        queues.remove(auction.getId());
        processing.remove(auction.getId());
    }

    @Override
    public void onAuctionExtended(Auction auction, long extraSeconds) {
        // AutoBidService không bị ảnh hưởng bởi việc gia hạn thời gian, để trống.
    }

    @Override
    public void onError(Auction auction, String errorCode, String message) {
        System.err.println("AutoBidService nhận được lỗi từ phiên " + auction.getId() + ": " + message);
    }
}