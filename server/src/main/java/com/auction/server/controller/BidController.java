package com.auction.server.controller;

import com.auction.server.service.AutoBidService;
import com.auction.server.service.BidService;
import com.auction.shared.dto.request.AutoBidRequest;
import com.auction.shared.dto.request.BidRequest;
import com.auction.shared.dto.response.BidResponse;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;

import java.util.Map;

/**
 * Controller xử lý các tác vụ đặt giá thủ công và đặt giá tự động (Auto-bid).
 */
public class BidController {

  private final BidService bidService;
  private final AutoBidService autoBidService;

  public BidController(BidService bidService, AutoBidService autoBidService) {
    this.bidService = bidService;
    this.autoBidService = autoBidService;
  }

  /**
   * Xử lý hành động đặt giá (Bid) từ người dùng.
   */
  public ServerResponse placeBid(Message msg) {
    // Tận dụng sức mạnh của JSON Serializer (Jackson/Gson) bóc thẳng ra đối tượng DTO
    BidRequest req = msg.getData(BidRequest.class);

    // Gọi BidService để xử lý logic khóa đa luồng (Concurrent lock)
    BidResponse resp = bidService.placeBid(req, msg.getToken());
    return ServerResponse.ok(msg.getRequestId(), resp);
  }

  /**
   * Truy xuất lịch sử đặt giá để hiển thị trên biểu đồ thực tế (Real-time LineChart).
   */
  public ServerResponse getHistory(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    // Cập nhật: Trả về danh sách BidTransaction thực tế thay vì mock message
    return ServerResponse.ok(msg.getRequestId(), bidService.getHistory(auctionId));
  }

  /**
   * Đăng ký cấu hình Auto-bid (Proxy bidding) cho một user trên một sản phẩm.
   */
  public ServerResponse setAutoBid(Message msg) {
    AutoBidRequest req = msg.getData(AutoBidRequest.class);

    return ServerResponse.ok(msg.getRequestId(), autoBidService.register(req, msg.getToken()));
  }

  /**
   * Hủy đăng ký Auto-bid nếu người dùng muốn dừng tham gia đấu giá tự động.
   */
  public ServerResponse cancelAutoBid(Message msg) {
    Map payload = msg.getData(Map.class);
    String auctionId = (String) payload.get("auctionId");

    // Gọi dịch vụ để xóa trạng thái và dọn dẹp hàng đợi trong Cache
    return ServerResponse.ok(msg.getRequestId(), autoBidService.cancel(auctionId, msg.getToken()));
  }
}