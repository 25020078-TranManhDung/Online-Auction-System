package com.auction.server.service;

import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.model.Auction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service chạy ngầm (Background Job) để kiểm tra và đóng các phiên đấu giá hết hạn.
 * Tối ưu hóa: Quét trực tiếp trên Cache bộ nhớ (AuctionManager) mỗi 1 giây
 * để đạt độ trễ thời gian thực (Real-time) mà không làm nghẽn Database.
 */
public class AuctionTimerService {

    private final AuctionService auctionService;
    private final AuctionManager manager = AuctionManager.getInstance();
    private ScheduledExecutorService scheduler;

    // Inject AuctionService thay vì DAO, vì hàm closeAuction của Service đã xử lý mọi logic
    public AuctionTimerService(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Auction-Timer-Thread");
            t.setDaemon(true);
            return t;
        });

        // Quét mỗi 1 giây
        scheduler.scheduleAtFixedRate(
                this::checkExpiredAuctions,
                0, 1, TimeUnit.SECONDS
        );

        System.out.println("✅ AuctionTimerService đã khởi động (Chế độ Real-time: 1s/lần).");
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("🛑 AuctionTimerService đã dừng.");
        }
    }

    private void checkExpiredAuctions() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // === 1. Tự động chuyển OPEN -> RUNNING khi startTime đã đến ===
            List<Auction> pendingAuctions = manager.getAll().stream()
                    .filter(a -> a.getStatus() == AuctionStatus.OPEN)
                    .filter(a -> a.getStartTime() != null &&
                            (a.getStartTime().isBefore(now) || a.getStartTime().isEqual(now)))
                    .collect(Collectors.toList());

            for (Auction auction : pendingAuctions) {
                try {
                    auctionService.startAuction(auction.getId(), null);
                    System.out.println("▶ Tự động bắt đầu phiên: " + auction.getId());
                } catch (Exception e) {
                    System.err.println("❌ Lỗi khi start phiên [" + auction.getId() + "]: " + e.getMessage());
                }
            }

            // === 2. Lọc các phiên đang chạy và đã qua thời gian kết thúc ===
            List<Auction> expiredAuctions = manager.getAll().stream()
                    .filter(a -> a.getStatus() == AuctionStatus.RUNNING)
                    .filter(a -> a.getEndTime().isBefore(now) || a.getEndTime().isEqual(now))
                    .collect(Collectors.toList());

            if (!expiredAuctions.isEmpty()) {
                System.out.println("⏳ Phát hiện " + expiredAuctions.size() + " phiên đấu giá hết hạn lúc " + now);
            }

            // === 3. Tiến hành đóng từng phiên ===
            for (Auction auction : expiredAuctions) {
                try {
                    // Gọi sang AuctionService để kết thúc phiên.
                    // LƯU Ý CHO LUỒNG MỚI: Hàm closeAuction này sẽ chỉ đổi trạng thái thành FINISHED,
                    // lưu người thắng cuộc, và KHÔNG trừ tiền ngay.
                    auctionService.closeAuction(auction.getId());
                    System.out.println("🔒 Đã kết thúc phiên đấu giá: " + auction.getId() + " (Đang chờ người thắng thanh toán)");
                } catch (Exception e) {
                    System.err.println("❌ Lỗi khi đóng phiên đấu giá [" + auction.getId() + "]: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("🔥 Lỗi nghiêm trọng trong AuctionTimerService: " + e.getMessage());
            e.printStackTrace();
        }
    }
}