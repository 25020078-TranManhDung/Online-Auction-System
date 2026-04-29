package com.auction.client.network;

import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;
import com.auction.shared.util.JsonUtil;
import com.auction.client.model.UserSession; // Import đúng package theo cấu trúc thư mục

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Lớp quản lý kết nối Socket phía Client (Singleton).
 * Chịu trách nhiệm gửi yêu cầu đồng bộ (Blocking) và quản lý luồng đọc dữ liệu.
 */
public class SocketClient {
    private static volatile SocketClient instance;

    private Socket socket;
    private PrintWriter out;
    private Thread readerThread;

    // requestId → Future đang chờ — hỗ trợ nhiều request bay cùng lúc an toàn
    private final Map<String, CompletableFuture<ServerResponse>> pending = new ConcurrentHashMap<>();

    private MessageHandler messageHandler;
    private String host;
    private int port;

    // Chặn khởi tạo trực tiếp từ bên ngoài
    private SocketClient() {}

    public static SocketClient getInstance() {
        if (instance == null) {
            synchronized (SocketClient.class) {
                if (instance == null) {
                    instance = new SocketClient();
                }
            }
        }
        return instance;
    }

    public void connect(String host, int port) {
        this.host = host;
        this.port = port;
        doConnect();
    }

    private void doConnect() {
        try {
            socket = new Socket(host, port);
            // Sử dụng PrintWriter với autoFlush = true và mã hóa UTF-8
            out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Khởi chạy luồng đọc tin nhắn ngầm
            messageHandler = new MessageHandler(socket.getInputStream(), this);
            readerThread = new Thread(messageHandler, "socket-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            System.out.println("[Client] Đã kết nối thành công tới Server " + host + ":" + port);
        } catch (IOException e) {
            throw new RuntimeException("Không kết nối được server. Vui lòng kiểm tra lại địa chỉ hoặc trạng thái Server.", e);
        }
    }

    /**
     * Gửi request lên Server và đợi kết quả.
     * @param action Tên hành động (lấy từ Actions.java)
     * @param data Dữ liệu đi kèm (DTO)
     * @param responseType Class của DTO kết quả mong muốn nhận về
     * @return Đối tượng DTO kết quả sau khi giải mã JSON
     */
    public <T> T send(String action, Object data, Class<T> responseType) {
        // 1. TẤM KHIÊN PHÒNG THỦ (Defensive Check)
        if (this.out == null || this.socket == null || this.socket.isClosed()) {
            throw new IllegalStateException("Không thể gửi yêu cầu: Chưa có kết nối đến Server. Hãy chắc chắn rằng Server đã khởi động.");
        }

        String reqId = UUID.randomUUID().toString();
        String token = UserSession.getInstance().getToken();
        Message msg = new Message(action, token, reqId, data);

        CompletableFuture<ServerResponse> future = new CompletableFuture<>();
        pending.put(reqId, future);

        try {
            // 2. An toàn tuyệt đối để lock
            synchronized (this.out) {
                this.out.println(JsonUtil.toJson(msg));
            }

            ServerResponse resp = future.get(10, TimeUnit.SECONDS);

            if (!resp.isSuccess()) {
                String errMsg = resp.getError() != null
                    ? resp.getError().getMessage()
                    : "Lỗi không xác định từ Server";
                throw new RuntimeException(errMsg);
            }

            if (responseType == Void.class) return null;
            return resp.getData(responseType);

        } catch (TimeoutException e) {
            throw new RuntimeException("Server không phản hồi sau 10 giây (Timeout).");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Lỗi giao tiếp mạng: " + e.getMessage());
        } finally {
            pending.remove(reqId);
        }
    }

    /**
     * Được gọi bởi MessageHandler khi nhận được Response có requestId hợp lệ.
     */
    public void completeRequest(String requestId, ServerResponse resp) {
        CompletableFuture<ServerResponse> f = pending.get(requestId);
        if (f != null) {
            f.complete(resp);
        }
    }

    /**
     * Cơ chế thử kết nối lại khi rớt mạng.
     */
    public void reconnect() {
        System.out.println("[Client] Đang thử kết nối lại sau 3 giây...");
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        doConnect();
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
}