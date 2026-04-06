package com.auction.shared.dto.response;

import java.io.Serializable;

//DTO dùng để gửi thông báo thời gian thực từ Server tới tất cả Client.
public class NotificationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // Các loại thông báo: BID_PLACED, AUCTION_CLOSED, v.v.
    private String type;

    // Dữ liệu đi kèm (có thể là một BidResponse hoặc AuctionResponse)
    private Object data;

    public NotificationDTO() {}

    public NotificationDTO(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    public String getType() { return type; }
    public Object getData() { return data; }
}
