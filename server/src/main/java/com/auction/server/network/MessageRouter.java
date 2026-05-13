package com.auction.server.network;

import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.server.controller.WalletController;
import com.auction.server.util.TokenUtil;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.network.protocol.Actions;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;
import com.auction.shared.util.JsonUtil;
import com.google.gson.JsonSyntaxException;

import java.util.Set;

public class MessageRouter {

    private static final Set<String> PUBLIC_ACTIONS = Set.of(
        Actions.LOGIN,
        Actions.REGISTER
    );

    private UserController    userCtrl;
    private AuctionController auctionCtrl;
    private BidController     bidCtrl;
    private ItemController    itemCtrl;
    private WalletController  walletCtrl;

    public MessageRouter() {}

    /** Constructor đầy đủ */
    public MessageRouter(UserController userCtrl, AuctionController auctionCtrl,
                         BidController bidCtrl, ItemController itemCtrl,
                         WalletController walletCtrl) {
        this.userCtrl    = userCtrl;
        this.auctionCtrl = auctionCtrl;
        this.bidCtrl     = bidCtrl;
        this.itemCtrl    = itemCtrl;
        this.walletCtrl  = walletCtrl;
    }

    /** Constructor tương thích ngược (không có ví) */
    public MessageRouter(UserController userCtrl, AuctionController auctionCtrl,
                         BidController bidCtrl, ItemController itemCtrl) {
        this(userCtrl, auctionCtrl, bidCtrl, itemCtrl, null);
    }

    public void setControllers(UserController userCtrl, AuctionController auctionCtrl,
                               BidController bidCtrl, ItemController itemCtrl,
                               WalletController walletCtrl) {
        this.userCtrl    = userCtrl;
        this.auctionCtrl = auctionCtrl;
        this.bidCtrl     = bidCtrl;
        this.itemCtrl    = itemCtrl;
        this.walletCtrl  = walletCtrl;
    }

    public String route(String rawJson, ClientHandler sender) {
        Message msg = null;
        try {
            msg = JsonUtil.fromJson(rawJson, Message.class);

            if (msg == null || msg.getAction() == null || msg.getAction().trim().isEmpty()) {
                return err(null, "UNKNOWN_ACTION", "Thiếu trường action hoặc payload JSON rỗng.");
            }

            if (!PUBLIC_ACTIONS.contains(msg.getAction())) {
                String token = msg.getToken();
                if (token == null || token.trim().isEmpty()) {
                    return err(msg.getRequestId(), "UNAUTHORIZED", "Yêu cầu cung cấp Token hợp lệ.");
                }
                if (!TokenUtil.isValid(token)) {
                    return err(msg.getRequestId(), "TOKEN_EXPIRED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
                }
            }

            ServerResponse response = switch (msg.getAction()) {

                // Auth
                case Actions.LOGIN    -> userCtrl.login(msg, sender);
                case Actions.REGISTER -> userCtrl.register(msg);
                case Actions.LOGOUT   -> userCtrl.logout(msg, sender);

                // User Management
                case Actions.GET_ALL_USERS      -> userCtrl.getAllUsers(msg);
                case Actions.TOGGLE_USER_STATUS -> userCtrl.toggleStatus(msg);

                // Auction Operations
                case Actions.GET_AUCTIONS        -> auctionCtrl.getList(msg);
                case Actions.GET_AUCTION_DETAIL  -> auctionCtrl.getDetail(msg, sender);
                case Actions.CREATE_AUCTION      -> auctionCtrl.create(msg);
                case Actions.START_AUCTION       -> auctionCtrl.start(msg);
                case Actions.CLOSE_AUCTION       -> auctionCtrl.close(msg);
                case Actions.ADMIN_CLOSE_AUCTION -> auctionCtrl.close(msg);

                // [MERGE] Giữ MARK_AS_PAID (Admin thủ công) + thêm CONFIRM_PAYMENT (Winner tự xác nhận)
                case Actions.MARK_AS_PAID    -> auctionCtrl.markAsPaid(msg);
                case Actions.CONFIRM_PAYMENT -> auctionCtrl.confirmPayment(msg);

                case Actions.CANCEL_AUCTION  -> auctionCtrl.cancelAuction(msg);

                // Bid
                case Actions.PLACE_BID       -> bidCtrl.placeBid(msg);
                case Actions.SET_AUTO_BID    -> bidCtrl.setAutoBid(msg);
                case Actions.CANCEL_AUTO_BID -> bidCtrl.cancelAutoBid(msg);
                case Actions.GET_BID_HISTORY -> bidCtrl.getHistory(msg);
                case Actions.GET_ALL_BIDS    -> bidCtrl.getAllBids(msg); // [GIỮ LẠI] Admin

                // Item
                case Actions.CREATE_ITEM -> itemCtrl.create(msg);
                case Actions.GET_ITEM    -> itemCtrl.get(msg);
                case Actions.UPDATE_ITEM -> itemCtrl.update(msg);
                case Actions.DELETE_ITEM -> itemCtrl.delete(msg);

                // Wallet
                case Actions.GET_WALLET -> walletCtrl.getWallet(msg);
                case Actions.TOP_UP     -> walletCtrl.topUp(msg);
                case Actions.WITHDRAW   -> walletCtrl.withdraw(msg);

                default -> ServerResponse.fail(msg.getRequestId(), "NOT_FOUND",
                    String.format("Server không hỗ trợ action: [%s]", msg.getAction()));
            };

            if (response.getRequestId() == null) {
                response.setRequestId(msg.getRequestId());
            }
            return JsonUtil.toJson(response);

        } catch (JsonSyntaxException e) {
            return err(reqId(msg), "BAD_REQUEST", "Định dạng JSON không hợp lệ.");
        } catch (AuctionException e) {
            return err(reqId(msg), e.getCode(), e.getMessage());
        } catch (Exception e) {
            System.err.printf("[Router Exception] Action=%s | Error=%s%n",
                (msg != null ? msg.getAction() : "N/A"), e.getMessage());
            e.printStackTrace();
            return err(reqId(msg), "INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi nghiêm trọng tại phía máy chủ.");
        }
    }

    private String err(String reqId, String code, String message) {
        return JsonUtil.toJson(ServerResponse.fail(reqId, code, message));
    }

    private String reqId(Message m) {
        return m != null ? m.getRequestId() : null;
    }
}