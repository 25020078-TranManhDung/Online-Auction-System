package com.auction.server.controller;

import com.auction.server.service.ItemService;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UnauthorizedException;
import com.auction.shared.exception.UserNotFoundException;
import com.auction.shared.model.item.Item;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;

import java.util.Map;

/**
 * ItemController xử lý luồng yêu cầu (Request) từ Client,
 * gọi xuống ItemService và đóng gói phản hồi (Response) theo đúng PROTOCOL.
 */
public class ItemController {

  private final ItemService itemService;

  public ItemController(ItemService itemService) {
    this.itemService = itemService;
  }

  public ServerResponse create(Message msg) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) msg.getData();

      Item item = itemService.createItem(data, msg.getToken());

      return ServerResponse.ok(msg.getRequestId(), Map.of(
          "itemId", item.getId(),
          "message", "Thêm sản phẩm thành công",
          "itemType", item.getClass().getSimpleName()
      ));
    } catch (UnauthorizedException e) {
      // Xử lý lỗi phân quyền (chỉ Seller mới được tạo)
      return ServerResponse.fail(msg.getRequestId(), "PERMISSION_DENIED", e.getMessage());
    } catch (UserNotFoundException e) {
      // Xử lý lỗi không tìm thấy user từ token
      return ServerResponse.fail(msg.getRequestId(), "USER_NOT_FOUND", e.getMessage());
    } catch (AuctionException e) {
      // Bắt lỗi nghiệp vụ chung (ví dụ: INVALID_DATA)
      return ServerResponse.fail(msg.getRequestId(), "INVALID_DATA", e.getMessage());
    } catch (Exception e) {
      return ServerResponse.fail(msg.getRequestId(), "INTERNAL_ERROR", "Lỗi server nội bộ khi tạo sản phẩm");
    }
  }

  public ServerResponse get(Message msg) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) msg.getData();
      String itemId = (String) data.get("itemId");

      if (itemId == null || itemId.trim().isEmpty()) {
        return ServerResponse.fail(msg.getRequestId(), "INVALID_DATA", "ID sản phẩm không được để trống");
      }

      Item item = itemService.getItem(itemId);
      return ServerResponse.ok(msg.getRequestId(), item);

    } catch (ResourceNotFoundException e) {
      // Xử lý lỗi không tìm thấy tài nguyên chuyên biệt
      return ServerResponse.fail(msg.getRequestId(), "ITEM_NOT_FOUND", e.getMessage());
    } catch (AuctionException e) {
      return ServerResponse.fail(msg.getRequestId(), "INVALID_DATA", e.getMessage());
    } catch (Exception e) {
      return ServerResponse.fail(msg.getRequestId(), "INTERNAL_ERROR", "Lỗi hệ thống khi truy xuất thông tin sản phẩm");
    }
  }

  public ServerResponse update(Message msg) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) msg.getData();

      Item updatedItem = itemService.updateItem(data, msg.getToken());

      return ServerResponse.ok(msg.getRequestId(), Map.of(
          "itemId", updatedItem.getId(),
          "message", "Cập nhật thông tin sản phẩm thành công"
      ));
    } catch (UnauthorizedException e) {
      // Trả về PERMISSION_DENIED nếu user không phải chủ sở hữu sản phẩm
      return ServerResponse.fail(msg.getRequestId(), "PERMISSION_DENIED", e.getMessage());
    } catch (ResourceNotFoundException e) {
      return ServerResponse.fail(msg.getRequestId(), "ITEM_NOT_FOUND", e.getMessage());
    } catch (AuctionException e) {
      return ServerResponse.fail(msg.getRequestId(), "INVALID_REQUEST", e.getMessage());
    } catch (Exception e) {
      return ServerResponse.fail(msg.getRequestId(), "INTERNAL_ERROR", "Lỗi hệ thống khi cập nhật sản phẩm");
    }
  }

  public ServerResponse delete(Message msg) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) msg.getData();
      String itemId = (String) data.get("itemId");

      if (itemId == null || itemId.trim().isEmpty()) {
        return ServerResponse.fail(msg.getRequestId(), "INVALID_DATA", "ID sản phẩm không được để trống");
      }

      itemService.deleteItem(itemId, msg.getToken());

      return ServerResponse.ok(msg.getRequestId(), Map.of(
          "message", "Xóa sản phẩm thành công"
      ));
    } catch (UnauthorizedException e) {
      return ServerResponse.fail(msg.getRequestId(), "PERMISSION_DENIED", e.getMessage());
    } catch (ResourceNotFoundException e) {
      return ServerResponse.fail(msg.getRequestId(), "ITEM_NOT_FOUND", e.getMessage());
    } catch (AuctionException e) {
      return ServerResponse.fail(msg.getRequestId(), "INVALID_REQUEST", e.getMessage());
    } catch (Exception e) {
      return ServerResponse.fail(msg.getRequestId(), "INTERNAL_ERROR", "Lỗi hệ thống khi xóa sản phẩm");
    }
  }
}