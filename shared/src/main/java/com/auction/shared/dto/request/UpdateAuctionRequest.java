package com.auction.shared.dto.request;

import java.time.LocalDateTime;

/**
 * DTO gửi từ Client (Seller) lên Server khi sửa thông tin phiên đấu giá.
 * Chỉ cho phép sửa khi phiên đang ở trạng thái OPEN.
 */
public class UpdateAuctionRequest {

  private String        auctionId;
  private String        title;
  private String        description;
  private String        category;
  private double        startingPrice;
  private double        minBidIncrement;
  private LocalDateTime startTime;
  private LocalDateTime endTime;

  public UpdateAuctionRequest() {}

  public String getAuctionId()        { return auctionId; }
  public String getTitle()            { return title; }
  public String getDescription()      { return description; }
  public String getCategory()         { return category; }
  public double getStartingPrice()    { return startingPrice; }
  public double getMinBidIncrement()  { return minBidIncrement; }
  public LocalDateTime getStartTime() { return startTime; }
  public LocalDateTime getEndTime()   { return endTime; }

  public void setAuctionId(String auctionId)           { this.auctionId = auctionId; }
  public void setTitle(String title)                   { this.title = title; }
  public void setDescription(String description)       { this.description = description; }
  public void setCategory(String category)             { this.category = category; }
  public void setStartingPrice(double startingPrice)   { this.startingPrice = startingPrice; }
  public void setMinBidIncrement(double minBidIncrement){ this.minBidIncrement = minBidIncrement; }
  public void setStartTime(LocalDateTime startTime)    { this.startTime = startTime; }
  public void setEndTime(LocalDateTime endTime)        { this.endTime = endTime; }
}