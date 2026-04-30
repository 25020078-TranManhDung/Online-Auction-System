package com.auction.server.service;

import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.pattern.factory.ItemFactory;
import com.auction.server.util.TokenUtil;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UnauthorizedException;
import com.auction.shared.exception.UserNotFoundException;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;

import java.util.Map;

/**
 * ItemService chịu trách nhiệm xử lý logic nghiệp vụ cho Sản phẩm.
 * Hoàn toàn tách biệt khỏi mạng (Network/Message) và giao diện.
 */
public class ItemService {

    // Áp dụng Encapsulation: Sử dụng private final và tiêm (inject) qua constructor để dễ viết Unit Test
    private final ItemDAO itemDAO;
    private final UserDAO userDAO;

    public ItemService(ItemDAO itemDAO, UserDAO userDAO) {
        this.itemDAO = itemDAO;
        this.userDAO = userDAO;
    }

    /**
     * Tạo sản phẩm mới thông qua Factory Method.
     */
    public Item createItem(Map<String, Object> data, String token) throws AuctionException, UserNotFoundException {
        // 1. Xác thực người dùng
        String userId = TokenUtil.getUserId(token);
        if (userId == null) {
            throw new UnauthorizedException("Token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
        }
        User user = userDAO.findById(userId);

        if (user == null) {
            throw new UserNotFoundException("Không tìm thấy thông tin người dùng yêu cầu.");
        }

        // 2. Kiểm tra phân quyền: Chỉ Seller mới được quyền đăng sản phẩm
        if (!(user instanceof Seller)) {
            throw new UnauthorizedException("Từ chối truy cập: Chỉ tài khoản Seller mới có thể đăng sản phẩm đấu giá.");
        }

        // 3. Sử dụng Design Pattern: Factory Method để tạo đúng loại sản phẩm
        String itemCategoryStr = (String) data.get("category");

        if (itemCategoryStr == null || itemCategoryStr.isEmpty()) {
            throw new AuctionException("INVALID_DATA", "Bắt buộc phải cung cấp danh mục sản phẩm (category).");
        }

        // Chuyển đổi chuỗi String sang Enum ItemCategory
        ItemCategory categoryEnum;
        try {
            // Sử dụng toUpperCase() để tránh lỗi do Client gõ chữ thường (ví dụ: "electronics" thay vì "ELECTRONICS")
            categoryEnum = ItemCategory.valueOf(itemCategoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Bắt lỗi nếu Client gửi lên một category rác không có trong Enum
            throw new AuctionException("INVALID_DATA", "Danh mục sản phẩm không hợp lệ: " + itemCategoryStr);
        }

        // Truyền đúng kiểu dữ liệu ItemCategory vào Factory
        Item newItem = ItemFactory.createItem(categoryEnum, data);

        // Gắn ID của người bán vào sản phẩm để quản lý quyền sở hữu
        newItem.setSellerId(userId);

        // 4. Lưu vào Database thông qua DAO
        itemDAO.save(newItem);

        return newItem;
    }

    /**
     * Lấy chi tiết một sản phẩm.
     */
    public Item getItem(String itemId) throws AuctionException {
        Item item = itemDAO.findById(itemId);
        if (item == null) {
            // Sử dụng Exception con chuyên biệt cho việc không tìm thấy tài nguyên
            throw new ResourceNotFoundException("ITEM_NOT_FOUND", "Sản phẩm không tồn tại trên hệ thống.");
        }
        return item;
    }

    /**
     * Cập nhật thông tin sản phẩm. Đòi hỏi phải kiểm tra quyền sở hữu.
     */
    public Item updateItem(Map<String, Object> data, String token) throws AuctionException {
        String itemId = (String) data.get("itemId");
        if (itemId == null) {
            // Truyền đủ 2 tham số: Mã lỗi và Thông báo
            throw new AuctionException("INVALID_REQUEST", "Thiếu ID sản phẩm để cập nhật.");
        }

        // 1. Lấy sản phẩm hiện tại
        Item existingItem = getItem(itemId);

        // 2. Xác thực quyền sở hữu: Chỉ chủ sản phẩm mới được sửa
        String currentUserId = TokenUtil.getUserId(token);

        if (currentUserId == null) {
            throw new UnauthorizedException("Token không hợp lệ hoặc đã hết hạn.");
        }
        if (!existingItem.getSellerId().equals(currentUserId)) {
            // Sử dụng lớp con chuyên biệt cho lỗi phân quyền
            throw new UnauthorizedException("Bạn không có quyền chỉnh sửa sản phẩm của người khác.");
        }

        // 3. Thực hiện cập nhật các trường được phép (Ví dụ: tên, mô tả, giá khởi điểm)
        // Lưu ý: Không cho phép sửa nếu sản phẩm đã nằm trong một phiên đấu giá đang chạy (RUNNING)
        if (data.containsKey("title")) {
            existingItem.setTitle((String) data.get("title"));
        }
        if (data.containsKey("description")) {
            existingItem.setDescription((String) data.get("description"));
        }
        if (data.containsKey("startingPrice")) {
            // Ép kiểu an toàn hơn từ Map ra double
            existingItem.setStartingPrice(Double.parseDouble(data.get("startingPrice").toString()));
        }

        itemDAO.update(existingItem);
        return existingItem;
    }

    /**
     * Xóa sản phẩm khỏi hệ thống.
     */
    public void deleteItem(String itemId, String token) throws AuctionException {
        Item existingItem = getItem(itemId);
        String currentUserId = TokenUtil.getUserId(token);

        if (!existingItem.getSellerId().equals(currentUserId)) {
            // Áp dụng lớp Exception chuyên biệt cho lỗi phân quyền
            throw new UnauthorizedException("Bạn không có quyền xóa sản phẩm này.");
        }

        // Cần thêm logic kiểm tra: Nếu Auction chứa Item này đang diễn ra thì ném lỗi không cho xóa

        itemDAO.delete(itemId);
    }
}