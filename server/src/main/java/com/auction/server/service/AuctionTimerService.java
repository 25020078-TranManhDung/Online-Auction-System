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

    // Inject AuctionService thay vì DAO, vì hàm closeAuction của Service đã xử lý mọi logic (DB, Socket)
    public AuctionTimerService(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public void start() {
        // Tạo Thread Pool dành riêng cho Background Job
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Auction-Timer-Thread");
            // TỐI ƯU 1: Daemon = true giúp luồng này tự chết khi Server tắt, không treo JVM
            t.setDaemon(true);
            return t;
        });

        // TỐI ƯU 2: Quét mỗi 1 giây vì chúng ta thao tác trên RAM, cực kỳ nhẹ và chính xác tuyệt đối
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

            // 1. Lọc các phiên đang chạy và đã qua thời gian kết thúc (từ RAM)
            List<Auction> expiredAuctions = manager.getAll().stream()
                    .filter(a -> a.getStatus() == AuctionStatus.RUNNING)
                    .filter(a -> a.getEndTime().isBefore(now) || a.getEndTime().isEqual(now))
                    .collect(Collectors.toList());

            if (!expiredAuctions.isEmpty()) {
                System.out.println("⏳ Phát hiện " + expiredAuctions.size() + " phiên đấu giá hết hạn lúc " + now);
            }

            // 2. Tiến hành đóng từng phiên
            for (Auction auction : expiredAuctions) {
                try {
                    // Gọi sang AuctionService để đóng.
                    // Ở đó đã có sẵn logic cập nhật DB, thông báo Socket, tính người thắng cuộc.
                    auctionService.closeAuction(auction.getId());
                    System.out.println("🔒 Đã đóng thành công phiên đấu giá: " + auction.getId());
                } catch (Exception e) {
                    // TỐI ƯU 3: Try-catch riêng lẻ cho từng phiên
                    // Nếu phiên A bị lỗi đóng, Timer vẫn tiếp tục chạy để đóng phiên B, không bị sập toàn hệ thống
                    System.err.println("❌ Lỗi khi đóng phiên đấu giá [" + auction.getId() + "]: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // TỐI ƯU 4: Bọc khối Try-catch tổng, đảm bảo Background Job không bao giờ chết vì lỗi Runtime
            System.err.println("🔥 Lỗi nghiêm trọng trong AuctionTimerService: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
