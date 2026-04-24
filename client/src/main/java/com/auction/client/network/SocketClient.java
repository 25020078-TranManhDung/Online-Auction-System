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
        // Tạo requestId duy nhất để khớp nối với Response sau này
        String reqId = UUID.randomUUID().toString();

        // Lấy token trực tiếp từ UserSession (Singleton luôn tồn tại)
        // Nếu chưa đăng nhập, token sẽ là null (hợp lệ cho LOGIN/REGISTER)
        String token = UserSession.getInstance().getToken();

        // Đóng gói vào "phong bì" Message chuẩn Protocol
        Message msg = new Message(action, token, reqId, data);

        // Tạo một "phễu" để hứng kết quả trả về từ MessageHandler
        CompletableFuture<ServerResponse> future = new CompletableFuture<>();
        pending.put(reqId, future);

        try {
            // Gửi chuỗi JSON qua mạng (Thread-safe nhờ synchronized)
            synchronized (out) {
                // out.println() tự động thêm ký tự xuống dòng \n để Server readLine() được
                out.println(JsonUtil.toJson(msg));
            }

            // Đứng chờ Server phản hồi
            ServerResponse resp = future.get(10, TimeUnit.SECONDS);

            // Kiểm tra trạng thái thành công của nghiệp vụ
            if (!resp.isSuccess()) {
                String errMsg = resp.getError() != null
                        ? resp.getError().getMessage()
                        : "Lỗi không xác định từ Server";
                throw new RuntimeException(errMsg);
            }

            // Nếu thành công và không cần trả về data (VD: LOGOUT)
            if (responseType == Void.class) return null;

            // Sử dụng hàm ép kiểu dùng Gson có sẵn trong ServerResponse
            return resp.getData(responseType);

        } catch (TimeoutException e) {
            throw new RuntimeException("Server không phản hồi sau 10 giây (Timeout).");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Lỗi hệ thống trong quá trình giao tiếp: " + e.getMessage());
        } finally {
            // Luôn dọn dẹp requestId để tránh tràn bộ nhớ (Memory Leak)
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