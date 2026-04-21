package com.auction.server.pattern.strategy;

import com.auction.shared.model.Auction;
import com.auction.shared.model.user.User;
import com.auction.shared.exception.InvalidBidException;

/**
 * Interface cốt lõi áp dụng Strategy Pattern cho việc xử lý các loại hình đặt giá khác nhau.
 */
public interface BidStrategy {

    /**
     * Kiểm tra tính hợp lệ của lượt đặt giá.
     * Tối ưu từ ảnh gốc: Gộp hàm isValid và getInvalidReason bằng cách ném Exception.
     * * @param auction Phiên đấu giá hiện tại
     * @param bidder  Người dùng thực hiện bid
     * @param amount  Số tiền bid hoặc cấu hình bid (maxBid)
     * @throws InvalidBidException nếu bid vi phạm bất kỳ rule nào
     */
    void validateBid(Auction auction, User bidder, double amount) throws InvalidBidException;

    /**
     * Tính toán mức giá mới sẽ được cập nhật làm giá cao nhất (Current Highest Bid).
     * * @param auction Phiên đấu giá hiện tại
     * @param amount  Số tiền người dùng nhập vào
     * @return Mức giá mới được ghi nhận vào hệ thống
     */
    double calculateNewPrice(Auction auction, double amount);
}