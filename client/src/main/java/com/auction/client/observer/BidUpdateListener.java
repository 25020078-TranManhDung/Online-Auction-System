package com.auction.client.observer;

import com.google.gson.JsonObject;

public interface BidUpdateListener {
    /**
     * Hàm này được gọi khi có biến động về giá trong phiên đấu giá.
     * Dựa theo PROTOCOL.md, chuỗi JSON (eventData) truyền vào sẽ có dạng:
     * { "type": "PUSH", "event": "...", "data": {...} }
     * * Các 'event' mà Listener này sẽ nhận được bao gồm:
     * * 1. "BID_PLACED": Có người vừa đặt giá.
     * - data bao gồm: auctionId, bidderId, bidderName, amount, newCurrentPrice, isAutoBid, timestamp.
     * * 2. "AUTO_BID_PLACED": Auto-bid của chính user vừa tự động chạy.
     * - data bao gồm: auctionId, amount, newCurrentPrice, remainingMax.
     * * 3. "AUTO_BID_FAILED": Auto-bid bị vượt hạn mức (không đủ maxBid).
     * - data bao gồm: auctionId, message, currentPrice.
     * * @param eventData Toàn bộ gói tin JSON PUSH từ Server
     */
    void onBidUpdated(JsonObject eventData);
}