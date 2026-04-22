package com.auction.server.observer;

import com.auction.server.network.SocketServer;
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.util.JsonUtil;
import com.auction.shared.network.protocol.PushMessage;

import java.util.Map;

/**
 * Lớp đóng vai trò Observer: Lắng nghe sự kiện từ EventBus và phát (broadcast)
 * gói tin PUSH JSON qua SocketServer tới các Client.
 */
public class BidNotifier implements AuctionObserver {
    private final SocketServer socketServer;

    public BidNotifier(SocketServer socketServer) {
        this.socketServer = socketServer;
    }

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        // Cập nhật bám sát PROTOCOL.md
        PushMessage push = new PushMessage("BID_PLACED", Map.of(
                "auctionId",       auction.getId(),
                "bidderId",        bid.getBidderId(), // PROTOCOL yêu cầu có bidderId
                "bidderName",      bid.getBidderName(),
                "amount",          bid.getAmount(),
                "newCurrentPrice", auction.getCurrentPrice(),
                "isAutoBid",       bid.isAutoBid(),
                "timestamp",    bid.getTimestamp().toString()
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        // Dùng ternary operator (toán tử 3 ngôi) để tránh NullPointerException trong Map.of
        PushMessage push = new PushMessage("AUCTION_CLOSED", Map.of(
                "auctionId",  auction.getId(),
                "winnerId",   auction.getCurrentLeader() != null ? auction.getCurrentLeader() : "",
                "winnerName", auction.getCurrentLeader() != null ? auction.getCurrentLeader() : "Không có",
                "finalPrice", auction.getCurrentPrice(),
                "closedAt",   auction.getEndTime().toString() // Bổ sung theo PROTOCOL.md
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onAuctionExtended(Auction auction, long extraSeconds) {
        // Dùng extraSeconds động thay vì hardcode 60
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
        // Hiện tại không cần push, nhưng có thể mở rộng sau này để báo cho Client reload lại UI
    }

    @Override
    public void onError(Auction auction, String errorCode, String message) {
        // Push các sự kiện lỗi/hệ thống (VD: AUTO_BID_FAILED theo PROTOCOL.md)
        PushMessage push = new PushMessage(errorCode, Map.of(
                "auctionId",    auction.getId(),
                "message",      message,
                "currentPrice", auction.getCurrentPrice()
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    // Lưu ý: Lớp PushMessage (hoặc DTO tương đương) cần tự động thiết lập thuộc tính type="PUSH" khi chuyển JSON.
}

