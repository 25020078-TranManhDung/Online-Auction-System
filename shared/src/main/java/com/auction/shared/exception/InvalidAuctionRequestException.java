package com.auction.shared.exception;

/**
 * Ngoại lệ kiểm tra tính hợp lệ của yêu cầu tạo phiên đấu giá (CreateAuctionRequest).
 * Kiểm tra các logic: giá khởi điểm > 0, thời gian bắt đầu trước thời gian kết thúc,
 * và ItemCategory phải thuộc danh mục hệ thống hỗ trợ.
 */
public class InvalidAuctionRequestException extends AuctionException {
    public InvalidAuctionRequestException(String message) {
        super(message);
    }
}