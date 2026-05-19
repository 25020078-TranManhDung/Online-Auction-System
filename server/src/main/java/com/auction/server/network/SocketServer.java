package com.auction.server.network;

import com.auction.server.network.ClientHandler;
import com.auction.server.network.MessageRouter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SocketServer {
    private final int port;
    private final MessageRouter router;
    private ServerSocket serverSocket;
    // Giới hạn tối đa 100 client đồng thời để tránh OOM (OutOfMemoryError).
    // - corePoolSize=10: Luôn giữ sẵn 10 thread chờ việc
    // - maximumPoolSize=100: Tối đa 100 thread khi tải cao
    // - Queue=200: Cho phép 200 client xếp hàng chờ khi pool đầy
    // - CallerRunsPolicy: Khi queue đầy, từ chối kết nối mới thay vì crash server
    private final ExecutorService pool = new ThreadPoolExecutor(
        10, 100,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(200),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // Map auctionId -> Tập hợp các ClientHandler đang xem auction đó
    // Dùng để broadcast thông báo BID_PLACED, AUCTION_CLOSED
    private final Map<String, Set<ClientHandler>> auctionSubscribers = new ConcurrentHashMap<>();

    // Map userId -> ClientHandler để push riêng cho 1 user (ví dụ: AUTO_BID_FAILED)
    private final Map<String, ClientHandler> userHandlers = new ConcurrentHashMap<>();

    public SocketServer(int port, MessageRouter router) {
        this.port = port;
        this.router = router;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[SERVER] Đang lắng nghe trên cổng: " + port);

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] Kết nối mới từ: " + clientSocket.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(clientSocket, router, this);
                    pool.submit(handler);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("[SERVER] Lỗi accept: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("[SERVER] Không thể khởi động server trên cổng " + port, e);
        }
    }

    // Đăng ký một client vào danh sách theo dõi phiên đấu giá cụ thể.
    public void subscribeAuction(String auctionId, ClientHandler handler) {
        auctionSubscribers
            .computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
            .add(handler);
        System.out.println("[SERVER] Client đăng ký xem Auction: " + auctionId);
    }

    // Hủy đăng ký theo dõi khi client rời khỏi màn hình chi tiết hoặc ngắt kết nối.
    public void unsubscribeAuction(String auctionId, ClientHandler handler) {
        Set<ClientHandler> subs = auctionSubscribers.get(auctionId);
        if (subs != null) {
            subs.remove(handler);
        }
    }

    // Gửi thông báo tới toàn bộ các client đang xem một phiên đấu giá (Real-time Update). [cite: 94, 95]
    public void broadcastToAuction(String auctionId, String jsonMessage) {
        Set<ClientHandler> subs = auctionSubscribers.get(auctionId);
        if (subs != null) {
            subs.forEach(h -> h.sendMessage(jsonMessage));
        }
    }

    // Gửi tin nhắn riêng cho một user cụ thể dựa trên userId.
    public void sendToUser(String userId, String jsonMessage) {
        ClientHandler h = userHandlers.get(userId);
        if (h != null) {
            h.sendMessage(jsonMessage);
        }
    }

    // Lưu trữ handler khi user đăng nhập thành công để hỗ trợ push riêng.
    // FIX: Nếu userId đã có session cũ trên máy khác → kick phiên cũ ra trước.
    // FIX2: Nhận thêm newToken để CHỈ xóa token cũ, giữ lại token mới cho phiên hiện tại.
    public void registerUser(String userId, ClientHandler handler, String newToken) {
        ClientHandler oldHandler = userHandlers.get(userId);
        // QUAN TRỌNG: put handler MỚI vào map TRƯỚC khi kick handler cũ.
        userHandlers.put(userId, handler);

        if (oldHandler != null && oldHandler != handler) {
            System.out.println("[SERVER] Phát hiện đăng nhập trùng: user " + userId + " — kick phiên cũ.");

            // 1. Thu hồi token cũ NGAY — giữ newToken để Laptop2 dùng được
            com.auction.server.util.TokenUtil.invalidateAllForUserExcept(userId, newToken);

            // 2. FIX RACE CONDITION: chạy kick trong daemon thread riêng.
            //    Trước đây Thread.sleep(600) nằm trên ClientHandler thread của Laptop2,
            //    làm trễ response LOGIN 600ms. Trong khoảng trễ đó, Laptop1 kịp đăng
            //    nhập lại → sinh tokenC → invalidateAllForUserExcept(tokenC) xóa mất
            //    newToken của Laptop2 → Laptop2 nhận token đã bị xóa → TOKEN_EXPIRED.
            //    Bằng cách chạy kick async, response về Laptop2 NGAY, tránh race.
            final ClientHandler toKick = oldHandler;
            final String kickPush = "{\"type\":\"PUSH\",\"event\":\"SESSION_EXPIRED\"," +
                "\"data\":{\"message\":\"Tài khoản của bạn đã được đăng nhập ở thiết bị khác. Bạn đã bị đăng xuất.\"}}";

            Thread kickThread = new Thread(() -> {
                toKick.sendMessage(kickPush);
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                toKick.forceClose();
                System.out.println("[SERVER] Đã kick phiên cũ của user " + userId + " thành công.");
            }, "kick-old-session-" + userId);
            kickThread.setDaemon(true);
            kickThread.start();
        }
    }

    // Hủy định danh khi user ĐĂNG XUẤT (LOGOUT) chủ động, nhưng vẫn giữ kết nối Socket.
    public void unregisterUser(String userId) {
        userHandlers.remove(userId);
        System.out.println("[SERVER] User " + userId + " đã đăng xuất.");
    }

    /**
     * FIX: Ngắt kết nối cưỡng bức user đang online — gọi ngay sau khi admin khoá tài khoản.
     * Quy trình:
     *   1. Gửi PUSH event ACCOUNT_LOCKED → client hiển thị thông báo và về màn hình login
     *   2. Đóng socket → cleanup() trong ClientHandler tự dọn tài nguyên
     *
     * @param userId  ID user bị khoá
     * @param reason  Lý do hiển thị cho client
     */
    public void kickUser(String userId, String reason) {
        ClientHandler handler = userHandlers.get(userId);
        if (handler == null) {
            System.out.println("[SERVER] kickUser: user " + userId + " không online, bỏ qua.");
            return;
        }
        // Gửi PUSH trước để client có thể hiển thị thông báo và điều hướng về login
        String kickPush = String.format(
            "{\"type\":\"PUSH\",\"event\":\"ACCOUNT_LOCKED\",\"data\":{\"message\":\"%s\"}}",
            reason.replace("\"", "\\\"")
        );
        handler.sendMessage(kickPush);

        // Delay nhỏ để client nhận và xử lý message trước khi socket bị đóng
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}

        handler.forceClose();   // Đóng socket → vòng lặp readLine() ném IOException → cleanup()
        unregisterUser(userId);
        System.out.println("[SERVER] Đã kick user bị khoá: " + userId);
    }

    /**
     * QUAN TRỌNG: Dọn dẹp tài nguyên khi Client ngắt kết nối.
     * Ngăn chặn rò rỉ bộ nhớ (Memory Leak) và lỗi gửi tin nhắn tới socket đã đóng.
     */
    public void removeClient(ClientHandler handler) {
        // FIX BUG: Dùng remove(key, value) thay vì values().remove(handler)
        // Tránh xóa nhầm handler MỚI khi handler CŨ bị cleanup() sau forceClose().
        // Trường hợp: máy 2 login → registerUser() put handler mới vào map
        //             → forceClose() đóng socket cũ → cleanup() của máy cũ chạy
        //             → values().remove() sẽ tìm và xóa handler cũ, nhưng nếu userId
        //               đã được ghi đè bởi handler mới thì xóa nhầm!
        String uid = handler.getUserId();
        if (uid != null) {
            // Chỉ xóa nếu value trong map VẪN là handler này (không phải handler mới)
            userHandlers.remove(uid, handler);
        }

        // 2. Gỡ khỏi tất cả các phiên đấu giá đang theo dõi
        auctionSubscribers.values().forEach(subs -> subs.remove(handler));

        System.out.println("[SERVER] Đã giải phóng tài nguyên của một Client ngắt kết nối (userId=" + uid + ").");
    }

    public void shutdown() {
        System.out.println("[SERVER] Đang dừng hệ thống...");
        pool.shutdown();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khi đóng ServerSocket: " + e.getMessage());
        }
    }
}