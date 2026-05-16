package com.auction.shared.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidSetting Model Tests")
class AutoBidSettingTest {

    private AutoBidSetting setting;

    @BeforeEach
    void setUp() {
        setting = new AutoBidSetting(
                "abs-001", "bidder-001", "auction-001",
                5_000_000.0,  // maxBid
                100_000.0,    // increment
                true,
                LocalDateTime.now()
        );
    }

    // =========================================================
    // canBid()
    // =========================================================

    @Test
    @DisplayName("canBid trả về true khi currentPrice + increment <= maxBid và setting active")
    void canBid_activeAndAffordable_returnsTrue() {
        // currentPrice + increment = 4_900_000 + 100_000 = 5_000_000 <= 5_000_000
        assertTrue(setting.canBid(4_900_000.0));
    }

    @Test
    @DisplayName("canBid trả về false khi currentPrice + increment vượt maxBid")
    void canBid_exceedsMaxBid_returnsFalse() {
        // 4_950_000 + 100_000 = 5_050_000 > 5_000_000
        assertFalse(setting.canBid(4_950_000.0));
    }

    @Test
    @DisplayName("canBid trả về false khi setting không active dù còn đủ ngân sách")
    void canBid_inactive_returnsFalse() {
        setting.setActive(false);
        assertFalse(setting.canBid(1_000_000.0));
    }

    @Test
    @DisplayName("canBid với currentPrice = 0 và increment nhỏ hơn maxBid phải true")
    void canBid_zeroCurrent_returnsTrue() {
        assertTrue(setting.canBid(0));
    }

    @Test
    @DisplayName("canBid trả về false khi đã deactivate()")
    void canBid_afterDeactivate_returnsFalse() {
        setting.deactivate();
        assertFalse(setting.isActive());
        assertFalse(setting.canBid(1_000_000.0));
    }

    // =========================================================
    // calculateNextBid()
    // =========================================================

    @Test
    @DisplayName("calculateNextBid trả về currentPrice + increment")
    void calculateNextBid_returnsCorrectAmount() {
        double next = setting.calculateNextBid(2_000_000.0);
        assertEquals(2_100_000.0, next, 0.001);
    }

    @Test
    @DisplayName("calculateNextBid với giá 0 trả về đúng increment")
    void calculateNextBid_zeroPrice_returnsIncrement() {
        assertEquals(100_000.0, setting.calculateNextBid(0), 0.001);
    }

    // =========================================================
    // deactivate()
    // =========================================================

    @Test
    @DisplayName("deactivate() đặt active thành false")
    void deactivate_setsActiveFalse() {
        assertTrue(setting.isActive());
        setting.deactivate();
        assertFalse(setting.isActive());
    }

    // =========================================================
    // Constructor rỗng và registeredAt không null
    // =========================================================

    @Test
    @DisplayName("Constructor rỗng khởi tạo thành công")
    void defaultConstructor_createsInstance() {
        AutoBidSetting s = new AutoBidSetting();
        assertNotNull(s);
    }

    @Test
    @DisplayName("registeredAt được đặt bằng LocalDateTime.now() khi truyền null")
    void constructor_nullRegisteredAt_usesCurrentTime() {
        AutoBidSetting s = new AutoBidSetting("id", "b", "a", 1000, 100, true, null);
        assertNotNull(s.getRegisteredAt(), "registeredAt không được null khi truyền null");
    }

    // =========================================================
    // Getters / Setters
    // =========================================================

    @Test
    @DisplayName("Getter/setter đầy đủ hoạt động đúng")
    void gettersSetters_workCorrectly() {
        setting.setBidderId("new-bidder");
        setting.setAuctionId("new-auction");
        setting.setMaxBid(9_000_000.0);
        setting.setIncrement(200_000.0);
        setting.setActive(false);

        assertEquals("new-bidder",   setting.getBidderId());
        assertEquals("new-auction",  setting.getAuctionId());
        assertEquals(9_000_000.0,    setting.getMaxBid(), 0.001);
        assertEquals(200_000.0,      setting.getIncrement(), 0.001);
        assertFalse(setting.isActive());
    }
}