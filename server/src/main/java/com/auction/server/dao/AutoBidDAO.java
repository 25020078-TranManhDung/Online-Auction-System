package com.auction.server.dao;

import com.auction.shared.model.AutoBidSetting;
import java.util.List;

/**
 * Interface định nghĩa các thao tác với bảng cấu hình Đặt giá tự động.
 */
public interface AutoBidDAO {
    // Lưu một cấu hình Auto-bid mới vào DB
    boolean save(AutoBidSetting setting);

    // Cập nhật cấu hình (Ví dụ: Đổi trạng thái active thành false khi hết tiền)
    boolean update(AutoBidSetting setting);

    // Lấy danh sách các Auto-bid ĐANG HOẠT ĐỘNG của một phiên đấu giá cụ thể
    // (Phục vụ cho việc restore queue nếu Server bị restart đột ngột)
    List<AutoBidSetting> findActiveByAuction(String auctionId);
}