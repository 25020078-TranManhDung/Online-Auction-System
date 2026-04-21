package com.auction.server.pattern.strategy;

import com.auction.shared.model.Auction;
import com.auction.shared.model.user.User;
import com.auction.shared.exception.InvalidBidException;

public class AutoBidStrategy implements BidStrategy {
    private final double maxBid;
    private final double increment;

    // Constructor nhận cấu hình từ người dùng khi họ thiết lập Auto-bid
    public AutoBidStrategy(double maxBid, double increment) {
        this.maxBid = maxBid;
        this.increment = increment;
    }

    @Override
    public void validateBid(Auction auction, User bidder, double amount) throws InvalidBidException {
        // Lưu ý: Tham số 'amount' từ Interface có thể bỏ qua ở đây,
        // vì Auto-bid hoạt động dựa trên maxBid và increment đã cấu hình.

        // 1. Kiểm tra bảo mật cơ bản (Tương tự Normal Bid)
        if (!auction.getStatus().toString().equals("RUNNING")) {
            throw new InvalidBidException("Lỗi: Phiên đấu giá hiện không trong trạng thái mở bán.");
        }

        if (auction.getSellerId() != null && auction.getSellerId().equals(bidder.getId())) {
            throw new InvalidBidException("Lỗi: Bạn không thể tự đặt giá cho sản phẩm của mình.");
        }

        // 2. Chắt lọc từ code của bạn: Kiểm tra tính hợp lệ của bước giá
        if (this.increment < auction.getMinBidIncrement()) {
            throw new InvalidBidException(
                    String.format("Lỗi: Bước giá tự động (%.2f) quá nhỏ, phải >= bước giá tối thiểu của phiên (%.2f).",
                            this.increment, auction.getMinBidIncrement())
            );
        }

        // 3. Tính toán giá tiếp theo (Có xử lý case chưa ai bid)
        double currentPrice = auction.getCurrentPrice();
        double nextBid = (currentPrice == 0) ? auction.getStartPrice() : currentPrice + this.increment;

        // 4. Chắt lọc ưu điểm từ code của bạn: Dừng lại nếu vượt maxBid
        if (nextBid > this.maxBid) {
            throw new InvalidBidException(
                    String.format("Auto-bid đã dừng: Giá thầu tiếp theo (%.2f) vượt quá giới hạn tối đa (%.2f) của bạn.",
                            nextBid, this.maxBid)
            );
        }
    }

    @Override
    public double calculateNewPrice(Auction auction, double amount) {
        // Chắt lọc từ code của bạn: Giá mới = giá hiện tại + increment
        double currentPrice = auction.getCurrentPrice();
        return (currentPrice == 0) ? auction.getStartPrice() : currentPrice + this.increment;
    }

    // Getter để Service có thể lấy thông tin cấu hình nếu cần
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
}
