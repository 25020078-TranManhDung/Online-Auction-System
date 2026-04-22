package com.auction.shared.exception;

/**
 * Ngoại lệ kiểm tra tính hợp lệ của dữ liệu khi tạo/sửa phiên đấu giá.
 * Các trường hợp sử dụng:
 * - Giá khởi điểm (startingPrice) bị nhập số âm.
 * - Thời gian kết thúc (endTime) được set trước thời gian bắt đầu (startTime).
 * - Tên sản phẩm hoặc mô tả bị để trống.
 */
public class InvalidAuctionRequestException extends AuctionException {
    public InvalidAuctionRequestException(String message) {
        super("BAD_REQUEST", message);
    }
}