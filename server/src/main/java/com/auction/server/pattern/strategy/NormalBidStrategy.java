package com.auction.server.pattern.strategy;

import com.auction.shared.model.Auction;
import com.auction.shared.model.user.User;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.enums.AuctionStatus;

public class NormalBidStrategy implements BidStrategy {

    @Override
    public void validateBid(Auction auction, User bidder, double amount) throws InvalidBidException {
        // 1. Kiểm tra trạng thái phiên đấu giá
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new InvalidBidException("Lỗi: Phiên đấu giá hiện không trong trạng thái mở bán.");
        }

        // 2. Ngăn chặn gian lận (Seller tự bơm giá)
        if (auction.getSellerId() != null && auction.getSellerId().equals(bidder.getId())) {
            throw new InvalidBidException("Lỗi: Bạn không thể tự đặt giá cho sản phẩm của mình.");
        }

        // 3. Tích hợp logic tính "Bước giá tối thiểu" từ code của bạn
        double currentPrice = auction.getCurrentPrice(); // Hoặc getCurrentHighestBid() tùy bạn đặt tên
        double minRequiredBid;

        if (currentPrice == 0) {
            // Nếu chưa có ai đặt giá, giá hợp lệ tối thiểu là giá khởi điểm
            minRequiredBid = auction.getStartPrice();
        } else {
            // Chắt lọc ưu điểm từ code của bạn: Giá mới phải = giá hiện tại + bước giá
            minRequiredBid = currentPrice + auction.getMinBidIncrement();
        }

        // Kiểm tra tính hợp lệ
        if (amount < minRequiredBid) {
            throw new InvalidBidException(
                String.format("Giá đặt không hợp lệ. Bạn phải đặt ít nhất %.2f (Bao gồm bước giá tối thiểu).", minRequiredBid)
            );
        }
    }

    @Override
    public double calculateNewPrice(Auction auction, double amount) {
        // Chắt lọc từ code của bạn: Đối với Normal Bid, giá hệ thống ghi nhận chính là amount
        return amount;
    }
}