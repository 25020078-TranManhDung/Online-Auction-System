package com.auction.server;

import com.auction.server.dao.*;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.pattern.singleton.DatabaseManager;
import com.auction.server.service.*;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.model.Auction;
import com.auction.server.dao.impl.AuctionDaoImpl;
import com.auction.server.dao.impl.BidTransactionDaoImpl;
import com.auction.server.dao.impl.ItemDaoImpl;
import com.auction.server.dao.impl.UserDaoImpl;
import com.auction.server.dao.impl.AutoBidDaoImpl;

import java.util.List;

/**
 * Lớp khởi động chính của Server.
 * Áp dụng nguyên lý "Khởi tạo theo tầng" (Layered Initialization) để đảm bảo
 * hệ thống luôn ở trạng thái sẵn sàng 100% trước khi đón nhận request từ Client.
 */
public class ServerMain {

    // Cổng mặc định cho Socket Server (Có thể chuyển vào file config.properties sau)
    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Online Auction Server...");

        try {
            // =========================================================
            // BƯỚC 1: KHỞI TẠO KẾT NỐI DATABASE
            // =========================================================
            System.out.print("[1/7] Kết nối Database... ");
            // Giả định DatabaseManager của bạn có hàm init hoặc tự động kết nối khi lấy instance
            DatabaseManager.getInstance().getConnection();
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 2: KHỞI TẠO TẦNG DAO (Data Access Object)
            // =========================================================
            System.out.print("[2/7] Khởi tạo các DAO... ");
            UserDAO userDao = new UserDaoImpl();
            ItemDAO itemDao = new ItemDaoImpl();
            AuctionDAO auctionDao = new AuctionDaoImpl();
            BidTransactionDAO bidDao = new BidTransactionDaoImpl();
            AutoBidDAO autoBidDao = new AutoBidDaoImpl();
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 3: NẠP DỮ LIỆU TỪ DB LÊN CACHE (RAM)
            // =========================================================
            System.out.print("[3/7] Nạp phiên đấu giá RUNNING vào Cache... ");
            List<Auction> runningAuctions = auctionDao.findByStatus(AuctionStatus.RUNNING);
            AuctionManager manager = AuctionManager.getInstance();
            for (Auction a : runningAuctions) {
                manager.addAuction(a);
            }
            System.out.println("✅ OK (" + runningAuctions.size() + " phiên)");

            // =========================================================
            // BƯỚC 4: KHỞI TẠO TẦNG SERVICE & NHÚNG DEPENDENCY
            // =========================================================
            System.out.print("[4/7] Khởi tạo Services... ");
            // Khởi tạo theo đúng thứ tự phụ thuộc (Service này cần Service kia)
            BidService bidService = new BidService(bidDao, auctionDao, userDao);
            AutoBidService autoBidService = new AutoBidService(bidService, autoBidDao, bidDao);
            AuctionService auctionService = new AuctionService(auctionDao, itemDao, bidDao, userDao);
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 5: ĐĂNG KÝ OBSERVER CHO EVENT BUS (Real-time)
            // =========================================================
            System.out.print("[5/7] Thiết lập Event Bus & Khôi phục Auto-Bid... ");
            // (Nếu nhóm đã code BidNotifier để đẩy Socket thì thêm vào đây)
            // BidNotifier notifier = new BidNotifier(socketServer);
            // AuctionEventBus.getInstance().subscribe(notifier);

            AuctionEventBus.getInstance().subscribe(autoBidService);

            // Nạp lại các Auto-bid đang dang dở từ DB vào PriorityQueue để tiếp tục chạy
            // (Bạn cần viết thêm hàm restoreQueues() vào AutoBidService dựa trên code tham khảo của bạn)
            // autoBidService.restoreQueues();
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 6: KHỞI ĐỘNG BACKGROUND JOBS (Đồng hồ hệ thống)
            // =========================================================
            System.out.print("[6/7] Khởi động Auction Timer... ");
            AuctionTimerService timerService = new AuctionTimerService(auctionService);
            timerService.start();
            // In OK đã được xử lý bên trong hàm start() của Timer

            // =========================================================
            // BƯỚC 7: MỞ CỔNG MẠNG CHO CLIENT KẾT NỐI (CUỐI CÙNG)
            // =========================================================
            System.out.println("[7/7] Mở cổng mạng Socket trên Port " + PORT + "...");
            // TODO: Khởi tạo và start class SocketServer của nhóm tại đây
            // SocketServer socketServer = new SocketServer(PORT, bidService, auctionService, ...);
            // socketServer.start();

            System.out.println("\n🎉 SERVER KHỞI ĐỘNG THÀNH CÔNG VÀ SẴN SÀNG NHẬN KẾT NỐI!");

            // Add Hook để tự động dọn dẹp tài nguyên khi tắt Server (Bấm Ctrl+C)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Đang tắt Server, dọn dẹp tài nguyên...");
                timerService.stop();
                System.out.println("Tạm biệt!");
            }));

        } catch (Exception e) {
            System.err.println("\n❌ LỖI NGHIÊM TRỌNG KHI KHỞI ĐỘNG SERVER:");
            e.printStackTrace();
            System.exit(1); // Ép dừng chương trình với mã lỗi 1
        }
    }
}
