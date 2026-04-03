package com.auction.shared.dto.response;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object (DTO) chứa danh sách các phiên đấu giá.
 * Được sử dụng để hiển thị dữ liệu trên màn hình "Danh sách phiên đấu giá".
 */
public class AuctionListResponse implements Serializable {
    //ID phiên bản để đảm bảo tính tương thích khi truyền qua Socket.
    private static final long serialVersionUID = 1L;

    //Danh sách các đối tượng AuctionResponse chứa thông tin chi tiết từng phiên.
    private List<AuctionResponse> auctions;

    //Thông điệp bổ sung.
    private String message;

    //Constructor mặc định phục vụ cho quá trình Deserialization.
    public AuctionListResponse() {
        this.auctions = new ArrayList<>();
    }

    //Constructor khởi tạo với danh sách và thông điệp.
    public AuctionListResponse(List<AuctionResponse> auctions, String message) {
        this.auctions = auctions;
        this.message = message;
    }

    public List<AuctionResponse> getAuctions() {
        return auctions;
    }

    public void setAuctions(List<AuctionResponse> auctions) {
        this.auctions = auctions;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
