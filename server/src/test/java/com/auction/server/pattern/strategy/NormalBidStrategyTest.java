package com.auction.server.pattern.strategy;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho NormalBidStrategy.
 */
@DisplayName("NormalBidStrategy Tests")
class NormalBidStrategyTest {

    private NormalBidStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NormalBidStrategy();
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────
    private Auction buildRunningAuction() {
        Auction a = new Auction();
        a.setId("auction-001");
        a.setSellerId("seller-999");
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartPrice(100_000.0);
        a.setCurrentPrice(100_000.0);
        a.setMinBidIncrement(10_000.0);
        return a;
    }

    private Auction buildFreshAuction() {
        // Chua co ai dat gia: currentPrice = 0
        Auction a = buildRunningAuction();
        a.setCurrentPrice(0.0);
        return a;
    }

    private Bidder buildBidder() {
        Bidder b = new Bidder();
        b.setId("bidder-001");
        b.setUsername("alice");
        return b;
    }

    // =========================================================
    //  validateBid() — Auction status
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Trang thai phien")
    class AuctionStatusTests {

        @Test
        @DisplayName("Auction RUNNING + gia hop le -> khong nem exception")
        void validateBid_runningAuction_validAmount_passes() {
            Auction auction = buildRunningAuction(); // currentPrice=100k, minIncrement=10k
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 110_000.0)); // 100k + 10k = 110k ✓
        }

        @Test
        @DisplayName("Auction OPEN (chua bat dau) -> nem InvalidBidException")
        void validateBid_auctionOpen_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.OPEN);
            User bidder = buildBidder();

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 150_000.0));

            assertEquals("INVALID_BID", ex.getCode());
        }

        @Test
        @DisplayName("Auction FINISHED (da ket thuc) -> nem InvalidBidException")
        void validateBid_auctionFinished_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.FINISHED);
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 150_000.0));
        }

        @Test
        @DisplayName("Auction CANCELLED -> nem InvalidBidException")
        void validateBid_auctionCancelled_throwsException() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.CANCELED);
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 150_000.0));
        }
    }

    // =========================================================
    //  validateBid() — Shill bidding
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Chong Shill Bidding")
    class ShillBiddingTests {

        @Test
        @DisplayName("Seller tu dat gia cho san pham minh -> nem InvalidBidException")
        void validateBid_sellerBidsOwnAuction_throwsException() {
            Auction auction = buildRunningAuction();
            // Seller dung chinh id cua minh de dat gia
            Seller seller = new Seller();
            seller.setId("seller-999"); // Trung voi sellerId cua auction
            seller.setUsername("seller_account");

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, seller, 150_000.0));

            assertEquals("INVALID_BID", ex.getCode());
            assertTrue(ex.getMessage().contains("không thể") || ex.getMessage().contains("khong the")
                            || ex.getMessage().toLowerCase().contains("own"),
                    "Thong bao phai de cap viec tu dat gia");
        }

        @Test
        @DisplayName("Bidder khac seller -> duoc phep dat gia")
        void validateBid_differentUser_isAllowed() {
            Auction auction = buildRunningAuction();
            Bidder bidder = buildBidder(); // id = bidder-001, != seller-999

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 110_000.0));
        }

        @Test
        @DisplayName("sellerId = null (auction chua co seller) -> khong bi chan")
        void validateBid_sellerIdNull_isAllowed() {
            Auction auction = buildRunningAuction();
            auction.setSellerId(null); // Truong hop edge
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 110_000.0));
        }
    }

    // =========================================================
    //  validateBid() — Gia tien
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Gia tien hop le")
    class BidAmountTests {

        // --- Truong hop chua co ai dat gia (currentPrice = 0) ---

        @Test
        @DisplayName("Chua co ai dat gia, gia = startPrice -> hop le (bien ranh gioi)")
        void validateBid_freshAuction_amountEqualsStartPrice_passes() {
            Auction auction = buildFreshAuction(); // currentPrice=0, startPrice=100k
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 100_000.0)); // = startPrice ✓
        }

        @Test
        @DisplayName("Chua co ai dat gia, gia > startPrice -> hop le")
        void validateBid_freshAuction_amountAboveStartPrice_passes() {
            Auction auction = buildFreshAuction();
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 150_000.0));
        }

        @Test
        @DisplayName("Chua co ai dat gia, gia < startPrice -> nem InvalidBidException")
        void validateBid_freshAuction_amountBelowStartPrice_throwsException() {
            Auction auction = buildFreshAuction(); // startPrice=100k
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 90_000.0)); // < 100k
        }

        // --- Truong hop da co nguoi dat gia (currentPrice > 0) ---

        @Test
        @DisplayName("Gia dat = currentPrice + minIncrement -> hop le (bien ranh gioi)")
        void validateBid_amountEqualsMinRequired_passes() {
            Auction auction = buildRunningAuction(); // currentPrice=100k, minIncrement=10k
            User bidder = buildBidder();

            // 100k + 10k = 110k -> vua du
            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 110_000.0));
        }

        @Test
        @DisplayName("Gia dat > currentPrice + minIncrement -> hop le")
        void validateBid_amountAboveMinRequired_passes() {
            Auction auction = buildRunningAuction();
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 200_000.0));
        }

        @Test
        @DisplayName("Gia dat = currentPrice (thieu 1 buoc gia) -> nem InvalidBidException")
        void validateBid_amountEqualsCurrentPrice_throwsException() {
            Auction auction = buildRunningAuction(); // currentPrice=100k
            User bidder = buildBidder();

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 100_000.0)); // = currentPrice, thieu buoc gia

            assertEquals("INVALID_BID", ex.getCode());
            assertTrue(ex.getMessage().contains("110000") || ex.getMessage().contains("110,000")
                            || ex.getMessage().contains("ít nhất") || ex.getMessage().contains("it nhat"),
                    "Thong bao phai goi y gia toi thieu");
        }

        @Test
        @DisplayName("Gia dat < currentPrice + minIncrement -> nem InvalidBidException")
        void validateBid_amountBelowMinRequired_throwsException() {
            Auction auction = buildRunningAuction(); // minRequired = 110k
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 105_000.0)); // 110k - 5k = thieu
        }

        @Test
        @DisplayName("Gia dat = 0 -> nem InvalidBidException")
        void validateBid_zeroAmount_throwsException() {
            Auction auction = buildRunningAuction();
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 0.0));
        }

        @Test
        @DisplayName("Gia dat am -> nem InvalidBidException")
        void validateBid_negativeAmount_throwsException() {
            Auction auction = buildRunningAuction();
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, -50_000.0));
        }

        @ParameterizedTest(name = "Gia {0} VND < 110k -> khong hop le")
        @ValueSource(doubles = {1.0, 50_000.0, 100_000.0, 109_999.0})
        @DisplayName("Cac muc gia duoi nguong toi thieu -> deu nem exception")
        void validateBid_belowThreshold_throwsException(double amount) {
            Auction auction = buildRunningAuction(); // minRequired = 110k
            User bidder = buildBidder();

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, amount));
        }

        @ParameterizedTest(name = "Gia {0} VND >= 110k -> hop le")
        @ValueSource(doubles = {110_000.0, 150_000.0, 500_000.0, 1_000_000.0})
        @DisplayName("Cac muc gia tren nguong toi thieu -> deu hop le")
        void validateBid_aboveThreshold_passes(double amount) {
            Auction auction = buildRunningAuction(); // minRequired = 110k
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, amount));
        }
    }

    // =========================================================
    //  validateBid() — Uu tien kiem tra
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Thu tu uu tien kiem tra")
    class ValidationOrderTests {

        @Test
        @DisplayName("Auction CLOSED + Seller tu dat + Gia thap: lo i dau tien la AUCTION_CLOSED")
        void validateBid_closedTakesPriorityOverShillBidding() {
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.FINISHED); // Uu tien check status truoc

            Seller seller = new Seller();
            seller.setId("seller-999"); // Id trung voi sellerId

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, seller, 50_000.0));

            // Loi phai la ve status, khong phai shill bidding
            assertTrue(ex.getMessage().contains("trạng thái") || ex.getMessage().contains("trang thai")
                            || ex.getMessage().toLowerCase().contains("running"),
                    "Phai kiem tra status truoc shill bidding");
        }

        @Test
        @DisplayName("Seller tu dat + Gia thap: loi dau tien la shill bidding (sau khi check status)")
        void validateBid_shillBiddingTakesPriorityOverAmount() {
            Auction auction = buildRunningAuction(); // RUNNING

            Seller seller = new Seller();
            seller.setId("seller-999"); // Id trung voi sellerId

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, seller, 1.0)); // Ca 2 loi

            // Phai bao loi shill bidding (kiem tra truoc gia)
            assertTrue(ex.getMessage().contains("không thể") || ex.getMessage().contains("sản phẩm")
                            || ex.getMessage().toLowerCase().contains("own"),
                    "Phai kiem tra shill bidding truoc gia tien");
        }
    }

    // =========================================================
    //  calculateNewPrice()
    // =========================================================
    @Nested
    @DisplayName("calculateNewPrice()")
    class CalculateNewPriceTests {

        @Test
        @DisplayName("Normal bid: gia moi chinh la amount (khong co logic tinh toan them)")
        void calculateNewPrice_returnsAmountDirectly() {
            Auction auction = buildRunningAuction();

            double result = strategy.calculateNewPrice(auction, 150_000.0);

            assertEquals(150_000.0, result,
                    "NormalBidStrategy phai tra ve chinh xac amount nguoi dung dat");
        }

        @Test
        @DisplayName("Amount lon -> van tra ve chinh xac amount do")
        void calculateNewPrice_largeAmount_returnsCorrectly() {
            Auction auction = buildRunningAuction();

            assertEquals(999_999_999.0, strategy.calculateNewPrice(auction, 999_999_999.0));
        }

        @Test
        @DisplayName("calculateNewPrice khong phu thuoc vao currentPrice cua auction")
        void calculateNewPrice_ignoresCurrentPrice() {
            Auction a1 = buildRunningAuction();
            a1.setCurrentPrice(100_000.0);

            Auction a2 = buildRunningAuction();
            a2.setCurrentPrice(500_000.0);

            // Cung amount, ket qua phai giong nhau du currentPrice khac nhau
            double result1 = strategy.calculateNewPrice(a1, 120_000.0);
            double result2 = strategy.calculateNewPrice(a2, 120_000.0);

            assertEquals(result1, result2);
            assertEquals(120_000.0, result1);
        }
    }
}