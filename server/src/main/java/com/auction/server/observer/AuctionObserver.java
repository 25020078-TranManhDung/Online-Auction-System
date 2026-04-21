package com.auction.server.observer;

import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;

/**
 * Interface định nghĩa các sự kiện mà hệ thống có thể quan sát.
 */
public interface AuctionObserver {
    // Khi có một bid hợp lệ được chấp nhận
    void onBidPlaced(Auction auction, BidTransaction bid);

    // Khi phiên đấu giá chính thức bắt đầu (OPEN -> RUNNING)
    void onAuctionStarted(Auction auction);

    // Khi phiên đấu giá kết thúc (RUNNING -> FINISHED)
    void onAuctionClosed(Auction auction);

    // Khi logic Anti-sniping kích hoạt (Gia hạn thời gian)
    void onAuctionExtended(Auction auction, long extraSeconds);

    // Khi một lỗi xảy ra (Ví dụ: Auto-bid của người dùng bị vượt qua)
    void onError(Auction auction, String errorCode, String message);
}
