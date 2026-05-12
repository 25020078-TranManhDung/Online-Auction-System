package com.auction.server.observer;

import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lớp trung tâm quản lý các sự kiện đấu giá (Subject).
 * Áp dụng Singleton Pattern và Thread-safe Collection.
 */
public class AuctionEventBus {

    // volatile đảm bảo tính đồng bộ bộ nhớ giữa các luồng
    private static volatile AuctionEventBus instance;

    // Sử dụng CopyOnWriteArrayList: Rất an toàn khi duyệt (publish) đồng thời với lúc có client mới subscribe
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    // Private constructor ngăn khởi tạo từ bên ngoài
    private AuctionEventBus() {}

    // Singleton với Double-Checked Locking giúp tối ưu hiệu năng
    public static AuctionEventBus getInstance() {
        if (instance == null) {
            synchronized (AuctionEventBus.class) {
                if (instance == null) {
                    instance = new AuctionEventBus();
                }
            }
        }
        return instance;
    }

    public void subscribe(AuctionObserver o) {
        // Tránh trường hợp 1 client vô tình đăng ký 2 lần
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    public void unsubscribe(AuctionObserver o) {
        observers.remove(o);
    }

    public void publishBidPlaced(Auction a, BidTransaction b) {
        observers.forEach(o -> o.onBidPlaced(a, b));
    }

    public void publishAuctionStarted(Auction a) {
        observers.forEach(o -> o.onAuctionStarted(a));
    }

    public void publishAuctionClosed(Auction a) {
        observers.forEach(o -> o.onAuctionClosed(a));
    }

    /**
     * Phát sự kiện khi phiên đổi sang PAID hoặc CANCELED.
     * @param newStatus "PAID" hoặc "CANCELED"
     */
    public void publishAuctionStatusChanged(Auction a, String newStatus) {
        observers.forEach(o -> o.onAuctionStatusChanged(a, newStatus));
    }

    // Tích hợp tham số extraSeconds từ giao thức của chúng ta
    public void publishAuctionExtended(Auction a, long extraSeconds) {
        observers.forEach(o -> o.onAuctionExtended(a, extraSeconds));
    }

    // Bổ sung publishError để xử lý Push các gói tin lỗi (VD: AUTO_BID_FAILED) theo PROTOCOL.md
    public void publishError(Auction a, String errorCode, String message) {
        observers.forEach(o -> o.onError(a, errorCode, message));
    }
}