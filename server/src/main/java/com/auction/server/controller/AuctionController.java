package com.auction.server.controller;

import com.auction.server.network.ClientHandler;
import com.auction.server.service.AuctionService;
import com.auction.shared.dto.request.CreateAuctionRequest;
import com.auction.shared.dto.request.UpdateAuctionRequest;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UnauthorizedException;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller xử lý các API Socket liên quan đến Phiên đấu giá.
 */
public class AuctionController {

  private final AuctionService auctionService;

  public AuctionController(AuctionService auctionService) {
    this.auctionService = auctionService;
  }

  /**
   * Lấy danh sách các phiên đấu giá (có phân trang và lọc theo trạng thái).
   */
  public ServerResponse getList(Message msg) {
    Map payload = msg.getData(Map.class);

    String status = payload != null ? (String) payload.get("status") : null;
    int page = payload != null && payload.get("page") != null ? ((Number) payload.get("page")).intValue() : 0;
    int size = payload != null && payload.get("size") != null ? ((Number) payload.get("size")).intValue() : Integer.MAX_VALUE;

    List<?> auctionList = auctionService.getList(status, page, size);

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("auctions", auctionList);
    responseData.put("total", auctionList != null ? auctionList.size() : 0);

    return ServerResponse.ok(msg.getRequestId(), responseData);
  }

  /**
   * Xem chi tiết một phiên đấu giá.
   */
  public ServerResponse getDetail(Message msg, ClientHandler sender) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    sender.setWatchingAuction(auctionId);

    return ServerResponse.ok(msg.getRequestId(), auctionService.getDetail(auctionId));
  }

  /**
   * Dành cho Seller tạo một phiên đấu giá mới.
   */
  public ServerResponse create(Message msg) {
    CreateAuctionRequest req = msg.getData(CreateAuctionRequest.class);
    return ServerResponse.ok(msg.getRequestId(), auctionService.createAuction(req, msg.getToken()));
  }

  /**
   * [MỚI] Dành cho Seller sửa thông tin phiên đấu giá khi còn ở trạng thái OPEN.
   * Bắt đầy đủ exception để trả lỗi rõ ràng về Client.
   */
  public ServerResponse update(Message msg) {
    try {
      UpdateAuctionRequest req = msg.getData(UpdateAuctionRequest.class);
      if (req == null || req.getAuctionId() == null || req.getAuctionId().isBlank()) {
        return ServerResponse.fail(msg.getRequestId(), "BAD_REQUEST",
            "Thiếu auctionId trong yêu cầu cập nhật.");
      }
      return ServerResponse.ok(msg.getRequestId(),
          auctionService.updateAuction(req, msg.getToken()));
    } catch (UnauthorizedException e) {
      return ServerResponse.fail(msg.getRequestId(), "UNAUTHORIZED", e.getMessage());
    } catch (ResourceNotFoundException e) {
      return ServerResponse.fail(msg.getRequestId(), e.getCode(), e.getMessage());
    } catch (AuctionException e) {
      return ServerResponse.fail(msg.getRequestId(), e.getCode(), e.getMessage());
    } catch (Exception e) {
      System.err.println("[AuctionController.update] " + e.getMessage());
      e.printStackTrace();
      return ServerResponse.fail(msg.getRequestId(), "INTERNAL_SERVER_ERROR",
          "Lỗi hệ thống khi cập nhật phiên đấu giá: " + e.getMessage());
    }
  }

  /**
   * Dành cho Seller bắt đầu phiên đấu giá (Chuyển trạng thái từ OPEN sang RUNNING).
   */
  public ServerResponse start(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    return ServerResponse.ok(msg.getRequestId(), auctionService.startAuction(auctionId, msg.getToken()));
  }

  /**
   * Dành cho Seller hoặc Hệ thống tự động đóng phiên đấu giá.
   */
  public ServerResponse close(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    auctionService.closeAuction(auctionId);
    return ServerResponse.ok(msg.getRequestId(), Map.of("message", "Đã đóng phiên đấu giá thành công."));
  }

  /**
   * Admin xác nhận thanh toán thủ công: FINISHED → PAID (không qua settle ví).
   */
  public ServerResponse markAsPaid(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    auctionService.markAsPaid(auctionId, msg.getToken());
    return ServerResponse.ok(msg.getRequestId(),
        Map.of("message", "Phiên đấu giá đã được xác nhận thanh toán thủ công."));
  }

  /**
   * Winner xác nhận thanh toán (FINISHED → PAID).
   */
  public ServerResponse confirmPayment(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    auctionService.confirmPayment(auctionId, msg.getToken());

    return ServerResponse.ok(msg.getRequestId(),
        Map.of("message", "Thanh toán thành công! Tiền đã được chuyển cho Người bán."));
  }

  /**
   * Admin/Seller hủy phiên đấu giá.
   */
  public ServerResponse cancelAuction(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    auctionService.cancelAuction(auctionId, msg.getToken());
    return ServerResponse.ok(msg.getRequestId(),
        Map.of("message", "Phiên đấu giá đã bị hủy thành công."));
  }
}