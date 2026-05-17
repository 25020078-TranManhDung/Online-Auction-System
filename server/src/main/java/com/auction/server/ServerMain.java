package com.auction.server;

import com.auction.server.controller.*;
import com.auction.server.dao.*;
import com.auction.server.dao.impl.*;
import com.auction.server.network.MessageRouter;
import com.auction.server.network.SocketServer;
import com.auction.server.observer.AuctionEventBus;
import com.auction.server.observer.BidNotifier;
import com.auction.server.pattern.singleton.AuctionManager;
import com.auction.server.pattern.singleton.DatabaseManager;
import com.auction.server.service.*;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.model.Auction;
import com.auction.server.controller.WalletController;
import com.auction.server.dao.WalletDAO;
import com.auction.server.dao.impl.WalletDaoImpl;
import com.auction.server.service.WalletService;
import java.util.List;

/**
 * Lớp khởi động chính của Server (Composition Root).
 * Áp dụng kiến trúc Phân tầng (Layered Architecture).
 * Chịu trách nhiệm lắp ráp (wiring) toàn bộ các Dependency của hệ thống một cách an toàn
 * trước khi mở cổng mạng để đón kết nối từ Client.
 */
public class ServerMain {

    // Cổng mạng TCP mặc định cho SocketServer
    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Online Auction Server...");

        // Khai báo ở phạm vi ngoài try-catch để có thể truy cập được từ khối finally (dùng cho Shutdown Hook)
        SocketServer socketServer = null;
        AuctionTimerService timerService = null;

        try {
            // =========================================================
            // BƯỚC 1: KHỞI TẠO KẾT NỐI DATABASE (Singleton Pattern)
            // =========================================================
            System.out.print("[1/8] Kết nối Cơ sở dữ liệu... ");
            // Sử dụng try-with-resources để mượn 1 Connection kiểm tra kết nối (Warm-up HikariCP).
            // Sau khi hết khối try, conn.close() tự động được gọi để trả Connection về Pool,
            // khắc phục triệt để lỗi Connection Leak (Rò rỉ kết nối) gây treo hệ thống.
            try (java.sql.Connection conn = DatabaseManager.getInstance().getConnection()) {
            } catch (Exception e) {
                System.err.println(" Lỗi kết nối DB: " + e.getMessage());
                System.exit(1); // Dừng server ngay lập tức nếu không có Database
            }
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 2: KHỞI TẠO TẦNG DATA ACCESS OBJECT (DAO)
            // Khởi tạo các class Impl (thực thi thao tác SQL) và gán vào Interface.
            // Điều này giúp hệ thống lỏng lẻo (Loosely Coupled), dễ dàng thay DB sau này.
            // =========================================================
            System.out.print("[2/8] Khởi tạo các DAO Layer... ");
            UserDAO userDao = new UserDaoImpl();
            ItemDAO itemDao = new ItemDaoImpl();
            AuctionDAO auctionDao = new AuctionDaoImpl();
            BidTransactionDAO bidDao = new BidTransactionDaoImpl();
            AutoBidDAO autoBidDao = new AutoBidDaoImpl();
            WalletDAO     walletDao     = new WalletDaoImpl();
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 3: CACHE WARM-UP (Tải dữ liệu lên RAM)
            // Để xử lý đấu giá tốc độ cao, ta nạp các phiên đang RUNNING từ DB lên bộ nhớ đệm (RAM)
            // thông qua Singleton AuctionManager.
            // =========================================================
            System.out.print("[3/8] Khôi phục phiên đấu giá RUNNING vào Cache... ");
            List<Auction> runningAuctions = auctionDao.findByStatus(AuctionStatus.RUNNING);
            AuctionManager manager = AuctionManager.getInstance();
            for (Auction a : runningAuctions) {
                manager.addAuction(a);
            }
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 4: KHỞI TẠO TẦNG BUSINESS LOGIC (SERVICES)
            // Áp dụng Dependency Injection (Tiêm phụ thuộc): Truyền các DAO vào Service.
            // Tầng Service chỉ chứa logic nghiệp vụ, không chứa câu lệnh SQL.
            // =========================================================
            System.out.print("[4/8] Khởi tạo tầng Services... ");
            UserService userService = new UserService(userDao);

            // ItemService cần giao tiếp với cả bảng items và users (để check quyền)
            ItemService itemService = new ItemService(itemDao, userDao);
            WalletService walletService = new WalletService(walletDao, userDao);
            // BidService là trung tâm xử lý concurrency, cần nhiều DAO để check logic chéo
            BidService bidService = new BidService(bidDao, auctionDao, userDao, walletService);
            AutoBidService autoBidService = new AutoBidService(bidService, autoBidDao, bidDao);
            AuctionService auctionService = new AuctionService(auctionDao, itemDao, bidDao, userDao, walletService);

            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 5: THIẾT LẬP OBSERVER PATTERN (Event Bus)
            // Đăng ký AutoBidService như một "Người quan sát" (Observer).
            // Mỗi khi có người đặt giá mới, EventBus sẽ tự động thông báo cho AutoBidService hoạt động.
            // =========================================================
            System.out.print("[5/8] Thiết lập Event Bus & Khôi phục Auto-Bid... ");
            AuctionEventBus.getInstance().subscribe(autoBidService);
            // FIX: Nạp lại các auto-bid settings đang active từ DB vào in-memory queues.
            // Nếu bỏ dòng này, sau mỗi lần server restart queues sẽ rỗng
            // dù DB vẫn còn bản ghi is_active=true → auto-bid không bao giờ kích hoạt.
            autoBidService.restoreQueuesFromDatabase(runningAuctions);
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 6: GIẢI QUYẾT CIRCULAR DEPENDENCY & KHỞI TẠO CONTROLLERS
            // Vòng lặp phụ thuộc: Router cần Controller -> Controller cần Socket -> Socket cần Router.
            // Giải pháp: Tạo Router rỗng trước, tạo Socket, tạo Controller, rồi dùng Setter tiêm ngược lại.
            // =========================================================
            System.out.print("[6/8] Khởi tạo Router & SocketServer... ");
            // 6.1 Khởi tạo Front Controller (Định tuyến) với trạng thái rỗng
            MessageRouter messageRouter = new MessageRouter();

            // 6.2 Khởi tạo mạng lưới Socket lắng nghe Client
            socketServer = new SocketServer(PORT, messageRouter);

            // 6.3 Tạo BidNotifier — Observer gửi push notification qua Socket
            // (Phải tạo SAU khi socketServer đã được khởi tạo)
            BidNotifier bidNotifier = new BidNotifier(socketServer, userDao);
            AuctionEventBus.getInstance().subscribe(bidNotifier);

            // 6.4 Tiêm các Service nghiệp vụ và SocketServer vào các Controller giao tiếp
            UserController userCtrl = new UserController(userService, socketServer);
            AuctionController auctionCtrl = new AuctionController(auctionService);
            BidController bidCtrl = new BidController(bidService, autoBidService);
            ItemController itemCtrl = new ItemController(itemService);
            WalletController walletCtrl = new WalletController(walletService);
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 7: TIÊM CONTROLLERS VÀO ROUTER (SETTER INJECTION)
            // Hoàn tất việc nối vòng tròn phụ thuộc một cách an toàn luồng.
            // =========================================================
            System.out.print("[7/8] Liên kết Controllers vào MessageRouter... ");
            messageRouter.setControllers(userCtrl, auctionCtrl, bidCtrl, itemCtrl, walletCtrl);
            System.out.println("✅ OK");

            // =========================================================
            // BƯỚC 8: KHỞI ĐỘNG CÁC LUỒNG THỰC THI NỀN (Daemon Threads)
            // Bật bộ đếm thời gian để tự động đóng các phiên đấu giá khi hết giờ.
            // =========================================================
            System.out.print("[8/8] Khởi động hệ thống nền... ");
            timerService = new AuctionTimerService(auctionService);
            timerService.start();
            System.out.println("✅ OK");

            // Mọi mảnh ghép đã được khởi tạo xong và nằm sẵn trên RAM.
            // Khởi động block main thread để chấp nhận (accept) socket kết nối.
            System.out.println("\n🎉 HỆ THỐNG ĐÃ SẴN SÀNG NHẬN KẾT NỐI TẠI TCP PORT: " + PORT);
            socketServer.start();

        } catch (Exception e) {
            System.err.println("\n❌ FATAL ERROR: QUÁ TRÌNH KHỞI ĐỘNG SERVER THẤT BẠI:");
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Hook dọn dẹp tài nguyên khi Server bị crash hoặc bị người dùng tắt (Ctrl+C)
            shutdownHook(socketServer, timerService);
        }
    }

    /**
     * Phương thức Graceful Shutdown.
     * Đảm bảo mọi kết nối mạng, các luồng ngầm và kết nối DB được giải phóng an toàn
     * tránh hiện tượng rò rỉ bộ nhớ (Memory Leak) hoặc file bị khóa.
     */
    private static void shutdownHook(SocketServer socketServer, AuctionTimerService timerService) {
        System.out.println("\n🛑 Đang tiến hành dọn dẹp tài nguyên hệ thống...");
        if (timerService != null) {
            timerService.stop(); // Dừng luồng quét thời gian
        }
        if (socketServer != null) {
            socketServer.shutdown(); // Ngắt kết nối các client và đóng cổng TCP
        }

        // Mở rộng thêm: Nên gọi DatabaseManager.getInstance().shutdown() nếu class đó có hàm đóng Pool.
        System.out.println("Hệ thống đã tắt an toàn!");
    }
}