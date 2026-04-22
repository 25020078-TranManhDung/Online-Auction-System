package com.auction.server.network;

// Tạm thời comment các Controller vì chưa có
// import com.auction.server.controller.*;
import com.auction.shared.exception.*;
import com.auction.shared.network.protocol.Actions;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Set;

public class MessageRouter {

    private static final Gson GSON = new Gson();

    private static final Set<String> PUBLIC = Set.of(Actions.LOGIN, Actions.REGISTER);

    // BƯỚC 1: TẠM THỜI ĐÓNG BĂNG CONTROLLER
    /*
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
    */

    // Constructor tạm thời không tham số để SocketServer có thể khởi tạo
    public MessageRouter() {
        System.out.println("[MessageRouter] Đã khởi tạo Skeleton Router");
    }

    public String route(String rawJson, ClientHandler sender) {
        Message msg = null;
        try {
            // Sử dụng Gson chuẩn để parse JSON
            msg = GSON.fromJson(rawJson, Message.class);

            if (msg == null || msg.getAction() == null) {
                return err(null, "UNKNOWN_ACTION", "Thiếu trường action hoặc JSON rỗng");
            }

            // Validate token với action cần auth
            if (!PUBLIC.contains(msg.getAction())) {
                // Tạm thời bỏ qua check Token để Client dễ test, sau này mở ra sau
                if (msg.getToken() == null) {
                    return err(msg.getRequestId(), "TOKEN_INVALID", "Token không hợp lệ hoặc đã bị thiếu");
                }
            }

            // SKELETON - TRẢ VỀ DỮ LIỆU GIẢ THAY VÌ GỌI CONTROLLER
            Object result = switch (msg.getAction()) {
                case Actions.LOGIN -> "Đã nhận lệnh LOGIN.";
                case Actions.PLACE_BID -> "Đã nhận lệnh PLACE_BID.";
                case Actions.GET_AUCTIONS -> "Đã nhận lệnh GET_AUCTIONS.";
                // Bắt mọi action khác
                default -> "Server đã nhận lệnh: [" + msg.getAction() + "].";
            };

            // Wrap result thành ServerResponse
            if (result instanceof ServerResponse r) {
                if (r.getRequestId() == null) {
                    r.setRequestId(msg.getRequestId());
                }
                return GSON.toJson(r);
            }
            return GSON.toJson(ServerResponse.ok(msg.getRequestId(), result));

        }
        catch (JsonSyntaxException e) {
            // Bắt lỗi rách JSON (VD: Client gửi thiếu dấu ngoặc nhọn)
            return err(reqId(msg), "BAD_REQUEST", "Định dạng JSON không hợp lệ");
        }

        catch (AuctionException e) {
            // Bắt 1 class cha, xử lý được hàng chục class con!
            // Dù B ném ra InvalidBidException hay TokenExpiredException, hàm này đều tự lấy ra đúng mã lỗi.
            return err(reqId(msg), e.getCode(), e.getMessage());
        }
        catch (Exception e) {
            // Lỗi hệ thống nghiêm trọng (NullPointer, Mất kết nối DB...)
            System.err.println("[MessageRouter] Lỗi không xác định: " + e.getMessage());
            e.printStackTrace();
            return err(reqId(msg), "INTERNAL_ERROR", "Lỗi server nội bộ");
        }
    }

    // Hàm tiện ích nội bộ
    private String err(String reqId, String code, String msg) {
        return GSON.toJson(ServerResponse.fail(reqId, code, msg));
    }

    private String reqId(Message m) {
        return m != null ? m.getRequestId() : null;
    }
}