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
}