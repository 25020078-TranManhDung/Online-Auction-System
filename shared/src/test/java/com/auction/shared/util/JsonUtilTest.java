package com.auction.shared.util;

import com.auction.shared.dto.request.LoginRequest;
import com.auction.shared.dto.request.BidRequest;
import com.auction.shared.model.Auction;
import com.auction.shared.enums.AuctionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonUtil Tests")
class JsonUtilTest {

    // =========================================================
    // toJson
    // =========================================================

    @Nested
    @DisplayName("toJson()")
    class ToJsonTests {

        @Test
        @DisplayName("toJson(null) phải trả về null")
        void toJson_null_returnsNull() {
            assertNull(JsonUtil.toJson(null));
        }

        @Test
        @DisplayName("toJson với object đơn giản phải trả về chuỗi JSON hợp lệ")
        void toJson_simpleObject_returnsValidJson() {
            LoginRequest req = new LoginRequest("user@test.com", "password123");
            String json = JsonUtil.toJson(req);

            assertNotNull(json);
            assertFalse(json.trim().isEmpty());
            assertTrue(json.contains("user@test.com"), "JSON phải chứa username");
            assertTrue(json.contains("password123"), "JSON phải chứa password");
        }

        @Test
        @DisplayName("toJson với số nguyên phải ra JSON đúng")
        void toJson_primitiveInt_returnsCorrectJson() {
            String json = JsonUtil.toJson(42);
            assertEquals("42", json);
        }

        @Test
        @DisplayName("toJson với chuỗi phải ra JSON có dấu nháy kép")
        void toJson_string_returnsQuotedString() {
            String json = JsonUtil.toJson("hello");
            assertEquals("\"hello\"", json);
        }

        @Test
        @DisplayName("toJson với LocalDateTime phải serialize thành chuỗi ISO")
        void toJson_withLocalDateTime_serializesAsIsoString() {
            LocalDateTime now = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
            Auction auction = new Auction("a1", "item1", "seller1",
                    1_000_000.0, 50_000.0, now, now.plusHours(2));

            String json = JsonUtil.toJson(auction);

            assertNotNull(json);
            // LocalDateTime phải được lưu dạng chuỗi ISO, không phải dạng timestamp số
            assertTrue(json.contains("2025-06-15"), "JSON phải chứa ngày theo định dạng ISO");
        }

        @Test
        @DisplayName("toJson với object phức tạp (Auction) phải chứa các field quan trọng")
        void toJson_auctionObject_containsExpectedFields() {
            Auction auction = new Auction("auction-1", "item-1", "seller-1",
                    500_000.0, 10_000.0,
                    LocalDateTime.now(), LocalDateTime.now().plusDays(1));

            String json = JsonUtil.toJson(auction);

            assertNotNull(json);
            assertTrue(json.contains("auction-1"));
            assertTrue(json.contains("item-1"));
            assertTrue(json.contains("seller-1"));
        }
    }

    // =========================================================
    // fromJson
    // =========================================================

    @Nested
    @DisplayName("fromJson()")
    class FromJsonTests {

        @Test
        @DisplayName("fromJson(null, clazz) phải trả về null")
        void fromJson_nullJson_returnsNull() {
            assertNull(JsonUtil.fromJson(null, LoginRequest.class));
        }

        @Test
        @DisplayName("fromJson với chuỗi rỗng phải trả về null")
        void fromJson_emptyString_returnsNull() {
            assertNull(JsonUtil.fromJson("", LoginRequest.class));
        }

        @Test
        @DisplayName("fromJson với chuỗi chỉ chứa khoảng trắng phải trả về null")
        void fromJson_blankString_returnsNull() {
            assertNull(JsonUtil.fromJson("   ", LoginRequest.class));
        }

        @Test
        @DisplayName("fromJson với JSON hợp lệ phải deserialize đúng các field")
        void fromJson_validJson_deserializesCorrectly() {
            String json = "{\"username\":\"alice@test.com\",\"password\":\"secret\"}";
            LoginRequest req = JsonUtil.fromJson(json, LoginRequest.class);

            assertNotNull(req);
            assertEquals("alice@test.com", req.getUsername());
            assertEquals("secret", req.getPassword());
        }

        @Test
        @DisplayName("fromJson với LocalDateTime phải parse chuỗi ISO đúng")
        void fromJson_withLocalDateTimeField_parsesCorrectly() {
            String json = "{\"id\":\"a1\",\"itemId\":\"i1\",\"sellerId\":\"s1\","
                    + "\"startPrice\":100000.0,\"currentPrice\":100000.0,\"minBidIncrement\":5000.0,"
                    + "\"startTime\":\"2025-06-15T10:30:00\",\"endTime\":\"2025-06-15T12:30:00\","
                    + "\"status\":\"OPEN\",\"bidCount\":0,\"bidHistory\":[]}";

            Auction auction = JsonUtil.fromJson(json, Auction.class);

            assertNotNull(auction);
            assertEquals("a1", auction.getId());
            assertNotNull(auction.getStartTime());
            assertEquals(2025, auction.getStartTime().getYear());
            assertEquals(6, auction.getStartTime().getMonthValue());
            assertEquals(15, auction.getStartTime().getDayOfMonth());
        }

        @Test
        @DisplayName("fromJson với trường không có trong class phải bỏ qua (không throw)")
        void fromJson_unknownFields_ignored() {
            String json = "{\"username\":\"bob\",\"password\":\"pass\",\"unknownField\":\"value\"}";
            assertDoesNotThrow(() -> JsonUtil.fromJson(json, LoginRequest.class));
        }

        @Test
        @DisplayName("fromJson với enum AuctionStatus phải deserialize đúng")
        void fromJson_enumField_deserializesCorrectly() {
            String json = "{\"id\":\"a2\",\"status\":\"RUNNING\",\"bidHistory\":[],\"bidCount\":0}";
            Auction auction = JsonUtil.fromJson(json, Auction.class);

            assertNotNull(auction);
            assertEquals(AuctionStatus.RUNNING, auction.getStatus());
        }
    }

    // =========================================================
    // Tính nhất quán: toJson → fromJson round-trip
    // =========================================================

    @Nested
    @DisplayName("Round-trip (toJson → fromJson)")
    class RoundTripTests {

        @Test
        @DisplayName("LoginRequest phải round-trip chính xác")
        void roundTrip_loginRequest_isConsistent() {
            LoginRequest original = new LoginRequest("user@example.com", "mypassword");
            String json = JsonUtil.toJson(original);
            LoginRequest restored = JsonUtil.fromJson(json, LoginRequest.class);

            assertNotNull(restored);
            assertEquals(original.getUsername(), restored.getUsername());
            assertEquals(original.getPassword(), restored.getPassword());
        }

        @Test
        @DisplayName("BidRequest phải round-trip chính xác")
        void roundTrip_bidRequest_isConsistent() {
            BidRequest original = new BidRequest("auction-001", "bidder-001", 1_500_000.0, false);
            String json = JsonUtil.toJson(original);
            BidRequest restored = JsonUtil.fromJson(json, BidRequest.class);

            assertNotNull(restored);
            assertEquals(original.getAuctionId(), restored.getAuctionId());
            assertEquals(original.getAmount(), restored.getAmount(), 0.001);
        }

        @Test
        @DisplayName("Auction với LocalDateTime phải round-trip giữ nguyên thời gian")
        void roundTrip_auctionWithLocalDateTime_preservesTime() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 1, 9, 0, 0);
            LocalDateTime end   = LocalDateTime.of(2025, 7, 1, 21, 0, 0);
            Auction original = new Auction("a-rt", "item-rt", "seller-rt",
                    2_000_000.0, 100_000.0, start, end);
            original.setStatus(AuctionStatus.RUNNING);

            String json = JsonUtil.toJson(original);
            Auction restored = JsonUtil.fromJson(json, Auction.class);

            assertNotNull(restored);
            assertEquals(original.getId(), restored.getId());
            assertEquals(original.getStartTime(), restored.getStartTime(),
                    "StartTime phải được khôi phục nguyên vẹn sau round-trip");
            assertEquals(original.getEndTime(), restored.getEndTime(),
                    "EndTime phải được khôi phục nguyên vẹn sau round-trip");
            assertEquals(original.getStatus(), restored.getStatus());
        }
    }

    // =========================================================
    // convertData
    // =========================================================

    @Nested
    @DisplayName("convertData()")
    class ConvertDataTests {

        @Test
        @DisplayName("convertData(null, clazz) phải trả về null")
        void convertData_null_returnsNull() {
            assertNull(JsonUtil.convertData(null, LoginRequest.class));
        }

        @Test
        @DisplayName("convertData từ Map-like object phải ép kiểu đúng")
        void convertData_fromRawObjectToDto_convertsCorrectly() {
            // Mô phỏng: khi nhận data dạng Object thô từ Gson (LinkedTreeMap),
            // convertData phải chuyển đúng sang DTO mong muốn
            LoginRequest original = new LoginRequest("convert@test.com", "convertpass");
            // toJson → fromJson(Object.class) sẽ cho ra LinkedTreeMap (object thô)
            String json = JsonUtil.toJson(original);
            Object rawObject = JsonUtil.fromJson(json, Object.class);

            LoginRequest converted = JsonUtil.convertData(rawObject, LoginRequest.class);

            assertNotNull(converted);
            assertEquals("convert@test.com", converted.getUsername());
            assertEquals("convertpass", converted.getPassword());
        }

        @Test
        @DisplayName("convertData nhiều lần từ cùng một nguồn phải cho kết quả nhất quán")
        void convertData_calledMultipleTimes_isIdempotent() {
            BidRequest original = new BidRequest("auction-x", "bidder-x", 999_000.0, false);
            String json = JsonUtil.toJson(original);
            Object raw = JsonUtil.fromJson(json, Object.class);

            BidRequest first  = JsonUtil.convertData(raw, BidRequest.class);
            BidRequest second = JsonUtil.convertData(raw, BidRequest.class);

            assertEquals(first.getAuctionId(), second.getAuctionId());
            assertEquals(first.getAmount(), second.getAmount(), 0.001);
        }
    }

    // =========================================================
    // Utility class – không được khởi tạo
    // =========================================================

    @Nested
    @DisplayName("Utility class")
    class UtilityClassTests {

        @Test
        @DisplayName("Constructor của JsonUtil phải throw UnsupportedOperationException")
        void constructor_throwsUnsupportedOperationException() {
            assertThrows(Exception.class, () -> {
                var constructor = JsonUtil.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                try {
                    constructor.newInstance();
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause(); // unwrap để lộ UnsupportedOperationException
                }
            });
        }
    }
}