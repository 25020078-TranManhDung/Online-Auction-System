package com.auction.shared.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BidTransaction Model Tests")
class BidTransactionTest {

    @Test
    @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
    void constructor_fullArgs_setsFieldsCorrectly() {
        LocalDateTime ts = LocalDateTime.of(2026, 5, 15, 10, 0, 0);
        BidTransaction bid = new BidTransaction(
                "bid-001", "auction-001", "bidder-001", "Alice",
                1_500_000.0, ts, false
        );

        assertEquals("bid-001",     bid.getId());
        assertEquals("auction-001", bid.getAuctionId());
        assertEquals("bidder-001",  bid.getBidderId());
        assertEquals("Alice",       bid.getBidderName());
        assertEquals(1_500_000.0,   bid.getAmount(), 0.001);
        assertEquals(ts,            bid.getTimestamp());
        assertFalse(bid.isAutoBid());
    }

    @Test
    @DisplayName("Constructor với timestamp null phải dùng LocalDateTime.now()")
    void constructor_nullTimestamp_usesNow() {
        BidTransaction bid = new BidTransaction("b1", "a1", "u1", "Bob", 1000.0, null, false);
        assertNotNull(bid.getTimestamp(), "Timestamp không được null");
    }

    @Test
    @DisplayName("Constructor rỗng khởi tạo thành công")
    void defaultConstructor_createsInstance() {
        BidTransaction bid = new BidTransaction();
        assertNotNull(bid);
    }

    @Test
    @DisplayName("isAutoBid = true khi bid là auto-bid")
    void autoBid_flagSetCorrectly() {
        BidTransaction bid = new BidTransaction("b2", "a1", "u2", "Carol", 2000.0, LocalDateTime.now(), true);
        assertTrue(bid.isAutoBid());
    }

    @Test
    @DisplayName("Getter/Setter hoạt động đúng")
    void gettersSetters_workCorrectly() {
        BidTransaction bid = new BidTransaction();
        bid.setId("bid-x");
        bid.setAuctionId("auction-x");
        bid.setBidderId("bidder-x");
        bid.setBidderName("Dave");
        bid.setAmount(999_999.0);
        bid.setAutoBid(true);
        bid.setProductTitle("iPhone 15 Pro Max");

        assertEquals("bid-x",            bid.getId());
        assertEquals("auction-x",        bid.getAuctionId());
        assertEquals("bidder-x",         bid.getBidderId());
        assertEquals("Dave",             bid.getBidderName());
        assertEquals(999_999.0,          bid.getAmount(), 0.001);
        assertTrue(bid.isAutoBid());
        assertEquals("iPhone 15 Pro Max", bid.getProductTitle());
    }
}