package com.auction.shared.dto.response;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) đại diện cho các phản hồi lỗi từ Server.
 * Dùng để bọc các ngoại lệ (Exception) thành một đối tượng tiêu chuẩn
 * trước khi gửi qua Socket, giúp Client dễ dàng hiển thị thông báo lỗi cho người dùng.
 */
public class ErrorResponse implements Serializable {

    //ID phiên bản để đảm bảo tính tương thích trong quá trình Serialization.
    private static final long serialVersionUID = 1L;

    //Mã lỗi tùy chỉnh do hệ thống định nghĩa
    private String errorCode;

    //Thông điệp giải thích chi tiết về lỗi (VD: "Giá đặt phải cao hơn giá hiện hành").
    private String errorMessage;

    private long timestamp;

    //Constructor mặc định không tham số phục vụ cho Deserialization.
    public ErrorResponse() {}

    //Constructor khởi tạo phản hồi lỗi với mã và thông điệp cụ thể.
    public ErrorResponse(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis(); // Tự động lấy thời gian hiện tại lúc xảy ra lỗi
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
