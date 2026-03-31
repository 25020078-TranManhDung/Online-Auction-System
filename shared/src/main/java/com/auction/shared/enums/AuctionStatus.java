package com.auction.shared.enums;

// Định nghĩa trạng thái phiên đấu giá
public enum AuctionStatus {
    OPEN, // Mở để nhận đăng kí hoặc chuẩn bị bắt đầu
    RUNNING, // Đang diễn ra, cho phép người dùng đặt giá (bid)
    FINISHED,  // Đã kết thúc thời gian đấu giá, đang chờ thanh toán
    PAID,      // Người thắng cuộc đã thanh toán thành công
    CANCELED   // Bị hủy (do không ai mua, lỗi hệ thống, hoặc Admin hủy)
}
