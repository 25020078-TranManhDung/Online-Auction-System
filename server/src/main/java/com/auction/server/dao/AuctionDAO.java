package com.auction.server.dao;

import com.auction.shared.model.Auction;
import com.auction.shared.enums.AuctionStatus;
import java.time.LocalDateTime;
import java.util.List;

public interface AuctionDAO {
    Auction findById(String id);
    List<Auction> findByStatus(AuctionStatus status);

    List<Auction> findBySellerId(String sellerId);

    List<Auction> findExpiringBefore(LocalDateTime deadline);

    List<Auction> findAuctions(AuctionStatus status, int offset, int limit);

    boolean save(Auction auction);
    boolean update(Auction auction);
}