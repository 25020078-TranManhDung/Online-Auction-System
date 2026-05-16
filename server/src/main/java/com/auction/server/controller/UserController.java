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
import com.auction.shared.dto.request.ChangePasswordRequest;

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
            Map data = msg.getData(Map.class);
            String targetUserId = (String) data.get("userId");
            if (targetUserId == null) {
                return ServerResponse.fail(msg.getRequestId(), "BAD_REQUEST", "Thiếu userId");
            }
            userService.toggleUserStatus(targetUserId);
            com.auction.shared.model.user.User updatedUser = userService.getById(targetUserId);
            boolean isNowLocked = !"ACTIVE".equalsIgnoreCase(updatedUser.getStatus());
            if (isNowLocked) {
                TokenUtil.invalidateAllForUser(targetUserId);
                socketServer.kickUser(targetUserId, "Tài khoản của bạn đã bị khoá bởi quản trị viên.");
            }
            return ServerResponse.ok(msg.getRequestId(),
                Map.of("message", "Đã cập nhật trạng thái", "newStatus", updatedUser.getStatus()));
        } catch (Exception e) {
            e.printStackTrace();
            return ServerResponse.fail(msg.getRequestId(), "SERVER_ERROR", "Lỗi khi cập nhật trạng thái User");
        }
    }

    /**
     * Admin khoá tài khoản theo mức độ vi phạm.
     * Request body: { userId, action: "WARN"|"TEMP_1D"|"TEMP_7D"|"TEMP_30D"|"PERM"|"UNLOCK" }
     */
    public ServerResponse banUser(Message msg) {
        try {
            Map data = msg.getData(Map.class);
            String targetUserId = (String) data.get("userId");
            String action       = (String) data.get("action");

            if (targetUserId == null || action == null) {
                return ServerResponse.fail(msg.getRequestId(), "BAD_REQUEST", "Thiếu userId hoặc action");
            }

            String resultMessage = userService.banUser(targetUserId, action);

            // Kick nếu là khoá trực tiếp HOẶC WARN đã leo thang thành khoá
            com.auction.shared.model.user.User updatedUser = userService.getById(targetUserId);
            boolean isNowLocked = "TEMP_LOCKED".equalsIgnoreCase(updatedUser.getStatus())
                || "PERM_LOCKED".equalsIgnoreCase(updatedUser.getStatus());
            boolean isLocking = !action.equalsIgnoreCase("UNLOCK");

            if (isLocking && isNowLocked) {
                TokenUtil.invalidateAllForUser(targetUserId);
                socketServer.kickUser(targetUserId,
                    "Tài khoản của bạn đã bị khoá bởi quản trị viên. Lý do: " + action);
            } else if ("WARN".equalsIgnoreCase(action) && !isNowLocked) {
                // WARN nhẹ (chưa bị khoá): push thẳng WARNING_RECEIVED tới user đang online
                // Thông báo hiện ở cửa sổ của USER, không phải cửa sổ Admin
                String warningPush = String.format(
                    "{\"type\":\"PUSH\",\"event\":\"WARNING_RECEIVED\","
                        + "\"data\":{\"message\":\"%s\",\"violationCount\":%d}}",
                    resultMessage.replace("\"", "\\\"").replace("\n", " "),
                    updatedUser.getViolationCount()
                );
                socketServer.sendToUser(targetUserId, warningPush);
                System.out.println("[UserController] Đã push WARNING_RECEIVED → user " + targetUserId);
            }
            System.out.println("[UserController.banUser] " + resultMessage);
            return ServerResponse.ok(msg.getRequestId(),
                Map.of("message", resultMessage,
                    "newStatus", updatedUser.getStatus(),
                    "violationCount", updatedUser.getViolationCount()));
        } catch (Exception e) {
            e.printStackTrace();
            return ServerResponse.fail(msg.getRequestId(), "SERVER_ERROR", e.getMessage());
        }
    }

    /**
     * Xử lý yêu cầu Đổi mật khẩu từ Client
     */
    public ServerResponse changePassword(Message msg) {
        try {
            // 1. Tận dụng JsonUtil/Gson (thông qua getData) để ép kiểu payload thành ChangePasswordRequest
            ChangePasswordRequest req = msg.getData(ChangePasswordRequest.class);

            if (req == null || req.getUserId() == null || req.getOldPassword() == null || req.getNewPassword() == null) {
                return ServerResponse.fail(msg.getRequestId(), "BAD_REQUEST", "Dữ liệu đổi mật khẩu không hợp lệ hoặc bị thiếu.");
            }

            // 2. Gọi tầng Service để xử lý logic (so sánh hash, lưu db...)
            userService.changePassword(req);

            // 3. Trả về thông báo thành công cho Client
            return ServerResponse.ok(msg.getRequestId(), Map.of("message", "Đổi mật khẩu thành công!"));

        } catch (Exception e) {
            e.printStackTrace();
            // Nếu sai mật khẩu cũ hoặc lỗi DB, Service sẽ ném Exception, ta bắt ở đây và gửi lỗi về Client
            return ServerResponse.fail(msg.getRequestId(), "CHANGE_PASSWORD_FAILED", e.getMessage());
        }
    }

    /**
     * Xử lý yêu cầu Cập nhật hoặc Xóa ảnh đại diện (Avatar)
     */
    public ServerResponse updateAvatar(Message msg) {
        try {
            // Lấy dữ liệu từ gói tin gửi lên
            com.auction.shared.dto.request.UpdateAvatarRequest req =
                    msg.getData(com.auction.shared.dto.request.UpdateAvatarRequest.class);

            if (req == null || req.getUserId() == null) {
                return ServerResponse.fail(msg.getRequestId(), "BAD_REQUEST", "Thiếu thông tin User ID.");
            }

            // Gọi tầng Service để cập nhật DB
            userService.updateAvatar(req.getUserId(), req.getAvatarBase64());

            // Phản hồi thành công
            String responseMsg = req.isRemoveRequest() ? "Đã xóa ảnh đại diện!" : "Cập nhật ảnh đại diện thành công!";

            // ✅ ĐÃ SỬA: Trả về thẳng chuỗi String thay vì bọc trong Map.of("message", responseMsg)
            return ServerResponse.ok(msg.getRequestId(), responseMsg);

        } catch (Exception e) {
            e.printStackTrace();
            return ServerResponse.fail(msg.getRequestId(), "UPDATE_AVATAR_FAILED", "Lỗi khi cập nhật ảnh: " + e.getMessage());
        }
    }
}