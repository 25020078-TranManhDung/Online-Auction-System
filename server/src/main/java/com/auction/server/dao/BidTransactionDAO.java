package com.auction.server.dao;

import com.auction.shared.model.BidTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BidTransactionDAO {
    boolean save(BidTransaction bid);

    List<BidTransaction> findByAuctionId(String auctionId);
    List<BidTransaction> findByBidderId(String bidderId);

    Optional<BidTransaction> findHighestBid(String auctionId);

    double getCurrentPrice(String auctionId);
}
