package com.auction.server.observer;

import com.auction.server.network.SocketServer;
import com.auction.server.dao.UserDAO; // Thêm thư viện DAO
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.model.user.User; // Thêm thư viện User
import com.auction.shared.util.JsonUtil;
import com.auction.shared.network.protocol.PushMessage;

import java.util.Map;

/**
 * Lớp đóng vai trò Observer: Lắng nghe sự kiện từ EventBus và phát (broadcast)
 * gói tin PUSH JSON qua SocketServer tới các Client.
 */
public class BidNotifier implements AuctionObserver {
    private final SocketServer socketServer;
    private final UserDAO userDao; // 🌟 [MỚI] Thêm UserDAO để truy vấn Database

    // 🌟 [MỚI] Sửa lại Constructor để nhận thêm UserDAO
    public BidNotifier(SocketServer socketServer, UserDAO userDao) {
        this.socketServer = socketServer;
        this.userDao = userDao;
    }

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        // 🌟 [MỚI] Lấy thông tin Avatar của người dùng từ Database
        String avatarBase64 = ""; // Mặc định là chuỗi rỗng để tránh lỗi NullPointerException của Map.of
        try {
            if (userDao != null) {
                User user = userDao.findById(bid.getBidderId());
                if (user != null && user.getAvatar() != null) {
                    avatarBase64 = user.getAvatar();
                }
            }
        } catch (Exception e) {
            System.err.println("[BidNotifier] Không thể lấy avatar cho user " + bid.getBidderId());
        }

        // FIX RACE CONDITION: Dùng bid.getAmount() thay vì auction.getCurrentPrice().
        PushMessage push = new PushMessage("BID_PLACED", Map.of(
                "auctionId",       auction.getId(),
                "bidderId",        bid.getBidderId(),
                "bidderName",      bid.getBidderName(),
                "bidderAvatar",    avatarBase64,     // 🌟 [MỚI BỔ SUNG] Đính kèm chuỗi ảnh Base64
                "amount",          bid.getAmount(),
                "newCurrentPrice", bid.getAmount(),  // FIX: dùng giá của bid này, không phải auction hiện tại
                "isAutoBid",       bid.isAutoBid(),
                "timestamp",       bid.getTimestamp().toString()
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onAuctionInfoUpdated(Auction auction) {
        PushMessage push = new PushMessage("AUCTION_INFO_UPDATED", Map.of(
                "auctionId", auction.getId(),
                "message",   "Thông tin phiên đấu giá vừa được cập nhật bởi người bán."
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
        System.out.println("[BidNotifier] AUCTION_INFO_UPDATED broadcast: " + auction.getId());
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        PushMessage push = new PushMessage("AUCTION_CLOSED", Map.of(
                "auctionId",  auction.getId(),
                "winnerId",   auction.getCurrentLeader() != null ? auction.getCurrentLeader() : "",
                "winnerName", auction.getCurrentLeader() != null ? auction.getCurrentLeader() : "Không có",
                "finalPrice", auction.getCurrentPrice(),
                "closedAt",   auction.getEndTime().toString()
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onAuctionExtended(Auction auction, long extraSeconds) {
        PushMessage push = new PushMessage("AUCTION_EXTENDED", Map.of(
                "auctionId",  auction.getId(),
                "newEndTime", auction.getEndTime().toString(),
                "extendedBy", extraSeconds,
                "message",    "Phiên đấu giá được gia hạn do có biến động giá phút cuối!"
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onAuctionStarted(Auction auction) {
    }

    @Override
    public void onAuctionStatusChanged(Auction auction, String newStatus) {
        PushMessage push = new PushMessage("AUCTION_STATUS_CHANGED", Map.of(
                "auctionId",  auction.getId(),
                "newStatus",  newStatus,
                "winnerId",   auction.getWinnerId()   != null ? auction.getWinnerId()   : "",
                "winnerName", auction.getCurrentLeader() != null ? auction.getCurrentLeader() : "Không có",
                "finalPrice", auction.getCurrentPrice(),
                "message",    "PAID".equals(newStatus)
                        ? "Phiên đấu giá đã được xác nhận thanh toán."
                        : "Phiên đấu giá đã bị hủy."
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onError(Auction auction, String errorCode, String message) {
        PushMessage push = new PushMessage(errorCode, Map.of(
                "auctionId",    auction.getId(),
                "message",      message,
                "currentPrice", auction.getCurrentPrice()
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }
}