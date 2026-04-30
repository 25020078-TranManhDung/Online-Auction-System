package com.auction.server.network;

import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.server.util.TokenUtil;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.network.protocol.Actions;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;
import com.auction.shared.util.JsonUtil;
import com.google.gson.JsonSyntaxException;

import java.util.Set;

/**
 * Front Controller (Bộ điều khiển trung tâm) cho hệ thống Socket.
 * Đóng vai trò như một DispatcherServlet trong Spring Boot:
 * Tiếp nhận JSON thô -> Deserialize -> Xác thực Token -> Phân phối (Dispatch) -> Serialize kết quả.
 */
public class MessageRouter {

    // Cache danh sách các Endpoint công khai (Whitelist) không cần kiểm tra quyền
    private static final Set<String> PUBLIC_ACTIONS = Set.of(
        Actions.LOGIN,
        Actions.REGISTER
    );

    // Dependency Injection (DI) các Controller nghiệp vụ
    private final UserController userCtrl;
    private final AuctionController auctionCtrl;
    private final BidController bidCtrl;
    private final ItemController itemCtrl;

    public MessageRouter(UserController userCtrl, AuctionController auctionCtrl,
                         BidController bidCtrl, ItemController itemCtrl) {
        this.userCtrl = userCtrl;
        this.auctionCtrl = auctionCtrl;
        this.bidCtrl = bidCtrl;
        this.itemCtrl = itemCtrl;
    }

    /**
     * Hàm định tuyến trung tâm.
     * @param rawJson Chuỗi JSON thô từ SocketClient gửi lên.
     * @param sender  Context của client hiện tại (dùng để push data hoặc quản lý session).
     * @return Chuỗi JSON chuẩn hóa của ServerResponse để đẩy ngược về Client.
     */
    public String route(String rawJson, ClientHandler sender) {
        Message msg = null;
        try {
            // 1. Tận dụng JsonUtil để Parse dữ liệu một cách an toàn
            msg = JsonUtil.fromJson(rawJson, Message.class);

            if (msg == null || msg.getAction() == null || msg.getAction().trim().isEmpty()) {
                return err(null, "UNKNOWN_ACTION", "Thiếu trường action hoặc payload JSON rỗng.");
            }

            // 2. Security Interceptor: Xác thực Token (Authentication)
            if (!PUBLIC_ACTIONS.contains(msg.getAction())) {
                String token = msg.getToken();

                // Kiểm tra định dạng đầu vào
                if (token == null || token.trim().isEmpty()) {
                    return err(msg.getRequestId(), "UNAUTHORIZED", "Yêu cầu cung cấp Token hợp lệ.");
                }

                // Tận dụng TokenUtil để kiểm tra Token có tồn tại trong bộ nhớ và còn hạn hay không
                if (!TokenUtil.isValid(token)) { //
                    // Sử dụng đúng mã lỗi TOKEN_EXPIRED hoặc TOKEN_INVALID theo PROTOCOL.md
                    return err(msg.getRequestId(), "TOKEN_EXPIRED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
                }
            }

            // 3. Dispatcher: Sử dụng hằng số tĩnh từ class Actions để điều hướng
            ServerResponse response = switch (msg.getAction()) {

                // --- Auth Operations ---
                case Actions.LOGIN -> userCtrl.login(msg, sender);
                case Actions.REGISTER -> userCtrl.register(msg);
                case Actions.LOGOUT -> userCtrl.logout(msg, sender);

                // --- Auction Operations ---
                case Actions.GET_AUCTIONS -> auctionCtrl.getList(msg);
                case Actions.GET_AUCTION_DETAIL -> auctionCtrl.getDetail(msg, sender);
                case Actions.CREATE_AUCTION -> auctionCtrl.create(msg);
                case Actions.START_AUCTION -> auctionCtrl.start(msg);
                case Actions.CLOSE_AUCTION -> auctionCtrl.close(msg);

                // --- Bid Operations ---
                case Actions.PLACE_BID -> bidCtrl.placeBid(msg);
                case Actions.SET_AUTO_BID -> bidCtrl.setAutoBid(msg);
                case Actions.CANCEL_AUTO_BID -> bidCtrl.cancelAutoBid(msg);
                case Actions.GET_BID_HISTORY -> bidCtrl.getHistory(msg);

                // --- Item Operations ---
                case Actions.CREATE_ITEM -> itemCtrl.create(msg);
                case Actions.GET_ITEM -> itemCtrl.get(msg);
                case Actions.UPDATE_ITEM -> itemCtrl.update(msg);
                case Actions.DELETE_ITEM -> itemCtrl.delete(msg);

                // Default Fallback
                default -> ServerResponse.fail(msg.getRequestId(), "NOT_FOUND",
                    String.format("Server không hỗ trợ API action: [%s]", msg.getAction()));
            };

            // 4. Đồng bộ Request ID và Serialize response
            if (response.getRequestId() == null) {
                response.setRequestId(msg.getRequestId());
            }
            return JsonUtil.toJson(response);

        } catch (JsonSyntaxException e) {
            // Lỗi từ phía Client gửi sai định dạng JSON
            return err(reqId(msg), "BAD_REQUEST", "Định dạng JSON không hợp lệ. Vui lòng kiểm tra lại cấu trúc.");

        } catch (AuctionException e) {
            // Đa hình xử lý Ngoại lệ: Mọi nghiệp vụ lỗi (VD: Đặt giá thấp, Phiên đóng)
            // đều sục bọt về đây và được chuyển hóa thành HTTP-like Error Response.
            return err(reqId(msg), e.getCode(), e.getMessage());

        } catch (Exception e) {
            // Xử lý lỗi hệ thống tột cùng (NullPointer, Mất kết nối DB...)
            System.err.printf("[Router Exception] Hành động %s gây ra lỗi: %s%n",
                (msg != null ? msg.getAction() : "N/A"), e.getMessage());
            e.printStackTrace();
            return err(reqId(msg), "INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi nghiêm trọng tại phía máy chủ.");
        }
    }

    /**
     * Utility method: Đóng gói chuẩn Error Payload
     */
    private String err(String reqId, String code, String message) {
        return JsonUtil.toJson(ServerResponse.fail(reqId, code, message)); //[cite: 6, 16]
    }

    private String reqId(Message m) {
        return m != null ? m.getRequestId() : null;
    }
}