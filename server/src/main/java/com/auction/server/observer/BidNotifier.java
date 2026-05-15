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
        // FIX RACE CONDITION: Dùng bid.getAmount() thay vì auction.getCurrentPrice().
        //
        // Vấn đề: publishBidPlaced(auction, bid_bidder2) được gọi bên trong synchronized block.
        // onBidPlaced() kích hoạt AutoBidService → cascade placeSystemBid(bid_bidder1) →
        // auction.currentPrice bị update lên 6.9M TRƯỚC KHI Map.of() ở đây được thực thi.
        // → Client nhận push "bidder2: newCurrentPrice=6,900,000" (SAI — đúng phải là 6,800,000).
        // → Màn hình hiển thị "[MỚI] bidder2: 6,900,000" và "[MỚI] bidder1: 6,900,000" — trùng giá.
        //
        // Fix: newCurrentPrice = bid.getAmount() = giá CHÍNH XÁC của bid vừa được xác nhận.
        // BidTransaction.amount không bao giờ thay đổi sau khi được tạo → thread-safe.
        PushMessage push = new PushMessage("BID_PLACED", Map.of(
            "auctionId",       auction.getId(),
            "bidderId",        bid.getBidderId(),
            "bidderName",      bid.getBidderName(),
            "amount",          bid.getAmount(),
            "newCurrentPrice", bid.getAmount(),  // FIX: dùng giá của bid này, không phải auction hiện tại
            "isAutoBid",       bid.isAutoBid(),
            "timestamp",       bid.getTimestamp().toString()
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
    }

    @Override
    public void onAuctionInfoUpdated(Auction auction) {
        // Push cho tất cả client đang xem phiên này — yêu cầu reload thông tin
        // Auction model không có title (title nằm trong Item) → dùng auctionId
        PushMessage push = new PushMessage("AUCTION_INFO_UPDATED", Map.of(
            "auctionId", auction.getId(),
            "message",   "Thông tin phiên đấu giá vừa được cập nhật bởi người bán."
        ));
        socketServer.broadcastToAuction(auction.getId(), JsonUtil.toJson(push));
        System.out.println("[BidNotifier] AUCTION_INFO_UPDATED broadcast: " + auction.getId());
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
    public void onAuctionStatusChanged(Auction auction, String newStatus) {
        // Push PAID hoặc CANCELED tới tất cả client đang theo dõi phiên
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