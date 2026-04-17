package com.auction.shared.model.user;

import com.auction.shared.enums.UserRole;

public class Bidder extends User {

    // Đã gỡ bỏ maxBid, increment và autoBiddingEnabled vì đây thuộc tính năng nâng cao (3.2.1)

    // Cập nhật Constructor: Thêm String id và truyền UserRole.BIDDER vào super()
    public Bidder(String id, String username, String password, String email) {
        super(id, username, password, email, UserRole.BIDDER);
    }

    @Override
    public void showRole() {
        System.out.println("Vai trò: Người tham gia đấu giá (Bidder)");
    }
}