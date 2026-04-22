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
import java.util.concurrent.Executors;

public class SocketServer {
    private final int port;
    private final MessageRouter router;
    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();

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
    public void registerUser(String userId, ClientHandler handler) {
        userHandlers.put(userId, handler);
    }

    // Hủy định danh khi user ĐĂNG XUẤT (LOGOUT) chủ động, nhưng vẫn giữ kết nối Socket.
    public void unregisterUser(String userId) {
        userHandlers.remove(userId);
        System.out.println("[SERVER] User " + userId + " đã đăng xuất.");
    }

    /**
     * QUAN TRỌNG: Dọn dẹp tài nguyên khi Client ngắt kết nối.
     * Ngăn chặn rò rỉ bộ nhớ (Memory Leak) và lỗi gửi tin nhắn tới socket đã đóng.
     */
    public void removeClient(ClientHandler handler) {
        // 1. Gỡ khỏi danh sách định danh người dùng
        userHandlers.values().remove(handler);

        // 2. Gỡ khỏi tất cả các phiên đấu giá đang theo dõi
        auctionSubscribers.values().forEach(subs -> subs.remove(handler));

        System.out.println("[SERVER] Đã giải phóng tài nguyên của một Client ngắt kết nối.");
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