package com.auction.shared.dto.response;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) đại diện cho phản hồi từ Server sau khi xử lý một yêu cầu đặt giá.
 * Lớp này chứa thông tin về trạng thái thành công/thất bại của lượt đặt giá,
 * cùng với giá hiện tại và người dẫn đầu để Client cập nhật giao diện (Realtime).
 */
public class BidResponse implements Serializable {

    //ID phiên bản để đảm bảo tính tương thích trong quá trình Serialization (gửi qua Socket).
    private static final long serialVersionUID = 1L;

    //Trạng thái đặt giá: true nếu giá hợp lệ và đã được ghi nhận, false nếu có lỗi.
    private boolean success;

    //Giá cao nhất hiện tại của phiên đấu giá sau khi xử lý.
    private double currentPrice;

    //Tên (hoặc username) của người đang dẫn đầu phiên đấu giá hiện tại.
    private String bidderName;

    //Thông điệp phản hồi từ Server (ví dụ: "Đặt giá thành công" hoặc thông báo lỗi nếu có).
    private String message;

    /**
     * Thời gian hệ thống ghi nhận lượt đặt giá (tính bằng mili-giây).
     * Phục vụ cho tính năng vẽ biểu đồ giá realtime (Bid History Visualization).
     */
    private long timestamp;

    //Constructor mặc định không tham số để các công cụ Serialization/Deserialization có thể khởi tạo đối tượng.
    public BidResponse() {
    }

    //Constructor khởi tạo đầy đủ dữ liệu cho phản hồi đặt giá.
    public BidResponse(boolean success, double currentPrice, String bidderName, String message) {
        this.success = success;
        this.currentPrice = currentPrice;
        this.bidderName = bidderName;
        this.message = message;
        this.timestamp = System.currentTimeMillis(); // Tự động lấy thời gian hiện tại
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getBidderName() {
        return bidderName;
    }

    public void setBidderName(String bidderName) {
        this.bidderName = bidderName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
