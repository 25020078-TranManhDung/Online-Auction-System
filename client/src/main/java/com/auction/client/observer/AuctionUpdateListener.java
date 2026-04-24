package com.auction.client.observer;

import com.google.gson.JsonObject;

public interface AuctionUpdateListener {
    /**
     * Hàm này được gọi khi Server báo cáo có sự thay đổi về phiên đấu giá (Bắt đầu/Kết thúc).
     * Dựa theo PROTOCOL.md, chuỗi JSON (eventData) truyền vào sẽ có dạng:
     * { "type": "PUSH", "event": "...", "data": {...} }
     * * Các 'event' mà Listener này sẽ nhận được bao gồm:
     * * 1. "AUCTION_CLOSED": Phiên đấu giá đã kết thúc.
     * - data bao gồm: auctionId, winnerId, winnerName, finalPrice, closedAt.
     * * @param eventData Toàn bộ gói tin JSON PUSH từ Server
     */
    void onAuctionStatusChanged(JsonObject eventData);
}