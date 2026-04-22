package com.auction.server.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final MessageRouter router;
    private final SocketServer server;
    private PrintWriter out;

    private String userId;             // Bằng null cho đến khi LOGIN thành công
    private String watchingAuctionId;  // Phiên đấu giá Client đang mở xem chi tiết

    // CONSTRUCTOR ĐỂ NHẬN KẾT NỐI TỪ SOCKET_SERVER
    public ClientHandler(Socket socket, MessageRouter router, SocketServer server) {
        this.socket = socket;
        this.router = router;
        this.server = server;
    }

    @Override
    public void run() {
        try (
                // Sử dụng chuẩn UTF-8 để không bị lỗi font tiếng Việt
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            this.out = writer;
            String line;

            // Đọc dữ liệu theo từng dòng
            // CHÚ Ý: Client phải gửi JSON trên 1 dòng duy nhất (không có \n bên trong JSON)
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                try {
                    String response = router.route(line, this);
                    if (response != null) {
                        sendMessage(response);
                    }
                } catch (Exception e) {
                    // Bắt mọi lỗi xảy ra trong quá trình xử lý logic (Controller, Service, Database)
                    System.err.println("[ClientHandler] Lỗi nghiệp vụ từ Client " + getUserId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client ngắt kết nối: " + socket.getRemoteSocketAddress());
        } finally {
            cleanup();
        }
    }

    // Đảm bảo Thread-safe khi nhiều luồng cùng gọi
    public synchronized void sendMessage(String json) {
        if (out != null && !socket.isClosed()) {
            out.println(json); // println tự thêm '\n' làm cờ báo hiệu kết thúc gói tin
        }
    }

    private void cleanup() {
        server.removeClient(this);

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }



    public void setUserId(String id) {
        this.userId = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setWatchingAuction(String auctionId) {
        // Rời auction cũ trước khi vào auction mới
        if (watchingAuctionId != null) {
            server.unsubscribeAuction(watchingAuctionId, this);
        }

        watchingAuctionId = auctionId;

        if (auctionId != null) {
            server.subscribeAuction(auctionId, this);
        }
    }

    public String getWatchingAuctionId() {
        return watchingAuctionId;
    }
}