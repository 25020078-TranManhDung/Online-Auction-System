package com.auction.shared.exception;

/**
 * Ngoại lệ chuyên biệt ném ra khi truy vấn dữ liệu nhưng không tìm thấy thông tin User.
 * Lớp này kế thừa từ ResourceNotFoundException nhằm đảm bảo cây phân cấp OOP rõ ràng.
 */
public class UserNotFoundException extends ResourceNotFoundException {

    /**
     * Constructor mặc định với thông báo lỗi tiêu chuẩn.
     * Áp dụng Encapsulation (Bao đóng): Mã lỗi "USER_NOT_FOUND" được giấu kín bên trong,
     * giúp các Service gọi đến không cần phải bận tâm về việc truyền mã lỗi.
     */
    public UserNotFoundException() {
        super("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng yêu cầu trong hệ thống.");
    }

    /**
     * Constructor cho phép tùy chỉnh thông báo lỗi chi tiết hơn từ tầng Service (ví dụ: in ra ID không tìm thấy).
     * Áp dụng Overloading (Nạp chồng phương thức) để tăng tính linh hoạt.
     *
     * @param message Thông báo lỗi chi tiết cần trả về cho Client
     */
    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }

    /**
     * Constructor hỗ trợ ném kèm nguyên nhân gốc rễ (Root cause) nếu lỗi xuất phát từ một Exception khác (như SQLException).
     *
     * @param message Thông báo lỗi
     * @param cause Ngoại lệ gốc gây ra lỗi này
     */
    public UserNotFoundException(String message, Throwable cause) {
        super("USER_NOT_FOUND", message);
        this.initCause(cause);
    }
}
