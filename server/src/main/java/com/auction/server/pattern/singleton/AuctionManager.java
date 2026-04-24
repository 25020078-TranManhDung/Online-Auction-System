package com.auction.server.pattern.singleton;

import com.auction.shared.model.Auction; // Import class Auction từ thư mục shared

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {

    private static volatile AuctionManager instance;

    // ConcurrentHashMap — thread-safe, không cần synchronized khi read
    private final Map<String, Auction> activeAuctions = new ConcurrentHashMap<>();

    private AuctionManager() {}

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) instance = new AuctionManager();
            }
        }
        return instance;
    }

    public void addAuction(Auction a) {
        activeAuctions.put(a.getId(), a);
    }

    public void removeAuction(String id) {
        activeAuctions.remove(id);
    }

    public Auction getAuction(String id) {
        return activeAuctions.get(id);
    }

    public Collection<Auction> getAll() {
        return activeAuctions.values();
    }

    public boolean isActive(String id) {
        return activeAuctions.containsKey(id);
    }
}