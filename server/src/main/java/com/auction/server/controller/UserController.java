package com.auction.server.controller;

import com.auction.server.network.ClientHandler;
import com.auction.server.network.SocketServer;
import com.auction.server.service.UserService;
import com.auction.server.util.TokenUtil;
import com.auction.shared.dto.request.LoginRequest;
import com.auction.shared.dto.request.RegisterRequest;
import com.auction.shared.dto.response.AuthResponse;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;

import java.util.Map;

public class UserController {
    private final UserService userService;
    private final SocketServer socketServer;

    public UserController(UserService userService, SocketServer socketServer) {
        this.userService = userService;
        this.socketServer = socketServer;
    }

    public ServerResponse login(Message msg, ClientHandler sender) {
        // Tận dụng hàm getData() tích hợp sẵn JsonUtil
        LoginRequest req = msg.getData(LoginRequest.class);
        AuthResponse auth = userService.login(req);

        // Lưu userId vào ClientHandler để dùng khi cleanup
        sender.setUserId(auth.getUserId());
        socketServer.registerUser(auth.getUserId(), sender);

        return ServerResponse.ok(msg.getRequestId(), auth);
    }

    public ServerResponse register(Message msg) {
        RegisterRequest req = msg.getData(RegisterRequest.class);
        return ServerResponse.ok(msg.getRequestId(), userService.register(req));
    }

    public ServerResponse logout(Message msg, ClientHandler sender) {
        // BƯỚC 1: Xóa Token khỏi bộ nhớ (In-memory tokenStore), khóa vĩnh viễn token này
        if (msg.getToken() != null) {
            TokenUtil.invalidate(msg.getToken());
        }

        // BƯỚC 2: Gỡ kết nối Socket hiện tại ra khỏi Server
        socketServer.unregisterUser(sender.getUserId());
        sender.setUserId(null);

        return ServerResponse.ok(msg.getRequestId(), Map.of("message", "Đã đăng xuất và hủy token"));
    }

    public ServerResponse getAllUsers(Message msg) {
        try {
            // Gọi tầng Service lấy danh sách User
            Object userList = userService.getAllUsers();
            return ServerResponse.ok(msg.getRequestId(), userList);
        } catch (Exception e) {
            e.printStackTrace();
            return ServerResponse.fail(msg.getRequestId(), "SERVER_ERROR", "Lỗi khi lấy danh sách User");
        }
    }

    public ServerResponse toggleStatus(Message msg) {
        try {
            // Bắt data Client gửi lên (ví dụ: { "userId": "U001" })
            Map data = msg.getData(Map.class);
            String targetUserId = (String) data.get("userId");

            if (targetUserId == null) {
                return ServerResponse.fail(msg.getRequestId(), "BAD_REQUEST", "Thiếu userId cần khóa/mở");
            }

            // Gọi tầng Service để xử lý cập nhật trạng thái trong Database
            userService.toggleUserStatus(targetUserId);

            return ServerResponse.ok(msg.getRequestId(), Map.of("message", "Đã cập nhật trạng thái tài khoản: " + targetUserId));
        } catch (Exception e) {
            e.printStackTrace();
            return ServerResponse.fail(msg.getRequestId(), "SERVER_ERROR", "Lỗi khi cập nhật trạng thái User");
        }
    }
}