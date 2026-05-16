package com.auction.server.pattern.strategy;

import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.exception.InvalidBidException;
import com.auction.shared.model.Auction;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;
import com.auction.shared.model.user.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho AutoBidStrategy.
 */
@DisplayName("AutoBidStrategy Tests")
class AutoBidStrategyTest {

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
        // Chua co ai dat gia
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

    /**
     * Tao AutoBidStrategy voi cau hinh mac dinh hop le:
     * maxBid=200k, increment=10k (= minBidIncrement cua auction)
     */
    private AutoBidStrategy buildDefaultStrategy() {
        return new AutoBidStrategy(200_000.0, 10_000.0);
    }

    // =========================================================
    //  Constructor & Getters
    // =========================================================
    @Nested
    @DisplayName("Constructor & Getters")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor luu dung maxBid va increment")
        void constructor_storesValuesCorrectly() {
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 20_000.0);

            assertEquals(500_000.0, strategy.getMaxBid());
            assertEquals(20_000.0,  strategy.getIncrement());
        }

        @Test
        @DisplayName("Tao nhieu instance doc lap — khong anh huong nhau")
        void constructor_multipleInstances_areIndependent() {
            AutoBidStrategy s1 = new AutoBidStrategy(100_000.0, 5_000.0);
            AutoBidStrategy s2 = new AutoBidStrategy(999_000.0, 50_000.0);

            assertEquals(100_000.0, s1.getMaxBid());
            assertEquals(999_000.0, s2.getMaxBid());
            assertNotEquals(s1.getMaxBid(), s2.getMaxBid());
        }
    }

    // =========================================================
    //  validateBid() — Auction status
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Trang thai phien")
    class AuctionStatusTests {

        @Test
        @DisplayName("Auction RUNNING -> khong nem exception")
        void validateBid_runningAuction_passes() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();
            User bidder = buildBidder();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, bidder, 0.0));
        }

        @Test
        @DisplayName("Auction OPEN -> nem InvalidBidException")
        void validateBid_auctionOpen_throwsException() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.OPEN);
            User bidder = buildBidder();

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 0.0));

            assertEquals("INVALID_BID", ex.getCode());
        }

        @Test
        @DisplayName("Auction FINISHED -> nem InvalidBidException")
        void validateBid_auctionFinished_throwsException() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.FINISHED);

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("Auction CANCELED -> nem InvalidBidException")
        void validateBid_auctionCanceled_throwsException() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();
            auction.setStatus(AuctionStatus.CANCELED);

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));
        }
    }

    // =========================================================
    //  validateBid() — Shill bidding
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Chong Shill Bidding")
    class ShillBiddingTests {

        @Test
        @DisplayName("Seller tu setup AutoBid cho san pham minh -> nem InvalidBidException")
        void validateBid_sellerBidsOwnAuction_throwsException() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();

            Seller seller = new Seller();
            seller.setId("seller-999"); // Trung voi sellerId
            seller.setUsername("seller_account");

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, seller, 0.0));

            assertEquals("INVALID_BID", ex.getCode());
        }

        @Test
        @DisplayName("Bidder khac seller -> duoc phep AutoBid")
        void validateBid_differentUser_isAllowed() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("sellerId = null -> khong bi chan")
        void validateBid_sellerIdNull_isAllowed() {
            AutoBidStrategy strategy = buildDefaultStrategy();
            Auction auction = buildRunningAuction();
            auction.setSellerId(null);

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }
    }

    // =========================================================
    //  validateBid() — Increment validation (★ Dac thu AutoBid)
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Kiem tra Increment (★ Dac thu AutoBid)")
    class IncrementValidationTests {

        @Test
        @DisplayName("increment = minBidIncrement cua phien -> hop le (bien ranh gioi)")
        void validateBid_incrementEqualsMinBidIncrement_passes() {
            // minBidIncrement = 10k, increment = 10k -> vua du
            AutoBidStrategy strategy = new AutoBidStrategy(200_000.0, 10_000.0);
            Auction auction = buildRunningAuction();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("increment > minBidIncrement -> hop le")
        void validateBid_incrementAboveMinBidIncrement_passes() {
            // minBidIncrement = 10k, increment = 20k -> du dieu kien
            AutoBidStrategy strategy = new AutoBidStrategy(300_000.0, 20_000.0);
            Auction auction = buildRunningAuction();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("increment < minBidIncrement -> nem InvalidBidException")
        void validateBid_incrementBelowMinBidIncrement_throwsException() {
            // minBidIncrement = 10k, increment = 5k -> qua nho
            AutoBidStrategy strategy = new AutoBidStrategy(200_000.0, 5_000.0);
            Auction auction = buildRunningAuction(); // minBidIncrement = 10k

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));

            assertEquals("INVALID_BID", ex.getCode());
            assertTrue(ex.getMessage().contains("5000") || ex.getMessage().contains("5.000")
                            || ex.getMessage().toLowerCase().contains("bước giá")
                            || ex.getMessage().toLowerCase().contains("buoc gia"),
                    "Thong bao phai de cap gia tri increment va minBidIncrement");
        }

        @ParameterizedTest(name = "increment {0} < 10000 (minBidIncrement) -> khong hop le")
        @ValueSource(doubles = {1.0, 1_000.0, 5_000.0, 9_999.0})
        @DisplayName("Nhieu muc increment duoi nguong -> deu nem exception")
        void validateBid_variousIncrementsBelowMin_allThrow(double increment) {
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, increment);
            Auction auction = buildRunningAuction(); // minBidIncrement = 10k

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));
        }
    }

    // =========================================================
    //  validateBid() — MaxBid limit (★ Dac thu AutoBid)
    // =========================================================
    @Nested
    @DisplayName("validateBid() - Kiem tra MaxBid (★ Dac thu AutoBid)")
    class MaxBidLimitTests {

        @Test
        @DisplayName("nextBid = maxBid -> hop le (bien ranh gioi, vua du maxBid)")
        void validateBid_nextBidEqualsMaxBid_passes() {
            // currentPrice=100k, increment=10k -> nextBid=110k
            // maxBid=110k -> vua du
            AutoBidStrategy strategy = new AutoBidStrategy(110_000.0, 10_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("nextBid < maxBid -> hop le")
        void validateBid_nextBidBelowMaxBid_passes() {
            // nextBid = 110k, maxBid = 500k -> ok
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 10_000.0);
            Auction auction = buildRunningAuction();

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("nextBid > maxBid -> AutoBid dung lai, nem InvalidBidException")
        void validateBid_nextBidExceedsMaxBid_throwsException() {
            // currentPrice=100k, increment=10k -> nextBid=110k
            // maxBid=105k -> 110k > 105k -> dung lai
            AutoBidStrategy strategy = new AutoBidStrategy(105_000.0, 10_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));

            assertEquals("INVALID_BID", ex.getCode());
            assertTrue(ex.getMessage().contains("110000") || ex.getMessage().contains("110,000")
                            || ex.getMessage().contains("105000") || ex.getMessage().contains("105,000")
                            || ex.getMessage().toLowerCase().contains("tối đa")
                            || ex.getMessage().toLowerCase().contains("maxbid"),
                    "Thong bao phai de cap nextBid va maxBid");
        }

        @Test
        @DisplayName("Phien chua co ai dat gia: nextBid = startPrice, kiem tra voi maxBid")
        void validateBid_freshAuction_nextBidIsStartPrice() {
            // currentPrice=0 -> nextBid = startPrice = 100k
            // maxBid=150k -> 100k <= 150k -> ok
            AutoBidStrategy strategy = new AutoBidStrategy(150_000.0, 10_000.0);
            Auction auction = buildFreshAuction(); // currentPrice=0, startPrice=100k

            assertDoesNotThrow(() ->
                    strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("Phien chua co ai dat gia: startPrice > maxBid -> AutoBid dung ngay")
        void validateBid_freshAuction_startPriceExceedsMaxBid_throwsException() {
            // startPrice=100k, maxBid=80k -> 100k > 80k -> dung
            AutoBidStrategy strategy = new AutoBidStrategy(80_000.0, 10_000.0);
            Auction auction = buildFreshAuction(); // startPrice=100k

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));
        }

        @Test
        @DisplayName("Uu tien kiem tra: increment qua nho duoc phat hien truoc khi check maxBid")
        void validateBid_incrementTooSmall_checkedBeforeMaxBid() {
            // increment=1k < minBidIncrement=10k -> bi chan truoc khi tinh nextBid
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 1_000.0);
            Auction auction = buildRunningAuction();

            InvalidBidException ex = assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));

            // Thong bao phai la ve increment, khong phai maxBid
            assertTrue(ex.getMessage().toLowerCase().contains("bước giá")
                            || ex.getMessage().toLowerCase().contains("buoc gia")
                            || ex.getMessage().contains("1000") || ex.getMessage().contains("1.000"),
                    "Phai bao loi increment truoc, khong phai maxBid");
        }
    }

    // =========================================================
    //  calculateNewPrice() (★ Khac biet lon so voi NormalBid)
    // =========================================================
    @Nested
    @DisplayName("calculateNewPrice() - Tu dong tinh gia (★ Khac NormalBid)")
    class CalculateNewPriceTests {

        @Test
        @DisplayName("Co nguoi dat gia: gia moi = currentPrice + increment")
        void calculateNewPrice_withExistingBids_addsIncrement() {
            // currentPrice=100k, increment=10k -> 110k
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 10_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k

            double result = strategy.calculateNewPrice(auction, 0.0);

            assertEquals(110_000.0, result,
                    "Gia moi phai la currentPrice + increment");
        }

        @Test
        @DisplayName("Chua co ai dat gia (currentPrice=0): gia moi = startPrice")
        void calculateNewPrice_freshAuction_returnsStartPrice() {
            // currentPrice=0 -> gia dau tien = startPrice = 100k
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 10_000.0);
            Auction auction = buildFreshAuction(); // currentPrice=0, startPrice=100k

            double result = strategy.calculateNewPrice(auction, 0.0);

            assertEquals(100_000.0, result,
                    "Gia dau tien phai la startPrice khi chua co ai dat");
        }

        @Test
        @DisplayName("KHAC NormalBid: amount truyen vao bi bo qua, chi dung increment")
        void calculateNewPrice_ignoresAmountParameter() {
            // Diem khac biet cot loi: AutoBid tinh gia tu increment, khong phai amount
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 10_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k

            double resultWithAmount1 = strategy.calculateNewPrice(auction, 999_999.0);
            double resultWithAmount2 = strategy.calculateNewPrice(auction, 1.0);

            // Ket qua phai giong nhau du amount khac nhau
            assertEquals(resultWithAmount1, resultWithAmount2,
                    "AutoBid bo qua amount, chi dung currentPrice + increment");
            assertEquals(110_000.0, resultWithAmount1);
        }

        @Test
        @DisplayName("Increment lon: currentPrice + increment tinh dung")
        void calculateNewPrice_largeIncrement_calculatesCorrectly() {
            AutoBidStrategy strategy = new AutoBidStrategy(10_000_000.0, 500_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k

            double result = strategy.calculateNewPrice(auction, 0.0);

            assertEquals(600_000.0, result); // 100k + 500k
        }

        @Test
        @DisplayName("Nhieu buoc dau gia lien tiep: moi lan tang dung increment")
        void calculateNewPrice_consecutiveBids_incrementsCorrectly() {
            AutoBidStrategy strategy = new AutoBidStrategy(500_000.0, 10_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k

            // Buoc 1: 100k + 10k = 110k
            double step1 = strategy.calculateNewPrice(auction, 0.0);
            assertEquals(110_000.0, step1);

            // Cap nhat auction sau buoc 1
            auction.setCurrentPrice(step1);

            // Buoc 2: 110k + 10k = 120k
            double step2 = strategy.calculateNewPrice(auction, 0.0);
            assertEquals(120_000.0, step2);

            // Cap nhat auction sau buoc 2
            auction.setCurrentPrice(step2);

            // Buoc 3: 120k + 10k = 130k
            double step3 = strategy.calculateNewPrice(auction, 0.0);
            assertEquals(130_000.0, step3);
        }

        @Test
        @DisplayName("KHAC NormalBid: cung amount nhung ket qua khac nhau")
        void calculateNewPrice_autoBidVsNormalBid_differentResults() {
            // NormalBid tra ve amount truc tiep
            // AutoBid tra ve currentPrice + increment
            AutoBidStrategy autoBid    = new AutoBidStrategy(500_000.0, 10_000.0);
            NormalBidStrategy normalBid = new NormalBidStrategy();

            Auction auction = buildRunningAuction(); // currentPrice=100k
            double amount   = 150_000.0;

            double autoResult   = autoBid.calculateNewPrice(auction, amount);
            double normalResult = normalBid.calculateNewPrice(auction, amount);

            // AutoBid: 100k + 10k = 110k
            assertEquals(110_000.0, autoResult);
            // NormalBid: tra ve chinh amount = 150k
            assertEquals(150_000.0, normalResult);

            assertNotEquals(autoResult, normalResult,
                    "AutoBid va NormalBid phai cho ket qua khac nhau");
        }
    }

    // =========================================================
    //  Kich ban tich hop (End-to-end scenario)
    // =========================================================
    @Nested
    @DisplayName("Kich ban tich hop AutoBid")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("Kich ban dien hinh: AutoBid dat gia lien tiep den khi gap maxBid")
        void scenario_autoBidRunsUntilMaxBid() {
            // maxBid=130k, increment=10k, startPrice/currentPrice=100k
            AutoBidStrategy strategy = new AutoBidStrategy(130_000.0, 10_000.0);
            Auction auction = buildRunningAuction(); // currentPrice=100k
            User bidder = buildBidder();

            // Buoc 1: nextBid=110k <= maxBid=130k -> OK
            assertDoesNotThrow(() -> strategy.validateBid(auction, bidder, 0.0));
            auction.setCurrentPrice(strategy.calculateNewPrice(auction, 0.0)); // 110k

            // Buoc 2: nextBid=120k <= 130k -> OK
            assertDoesNotThrow(() -> strategy.validateBid(auction, bidder, 0.0));
            auction.setCurrentPrice(strategy.calculateNewPrice(auction, 0.0)); // 120k

            // Buoc 3: nextBid=130k <= 130k -> OK (dung bang maxBid)
            assertDoesNotThrow(() -> strategy.validateBid(auction, bidder, 0.0));
            auction.setCurrentPrice(strategy.calculateNewPrice(auction, 0.0)); // 130k

            // Buoc 4: nextBid=140k > maxBid=130k -> DUNG LAI
            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, bidder, 0.0));
        }

        @Test
        @DisplayName("Kich ban: AutoBid dung ngay tu dau neu currentPrice da vuot maxBid")
        void scenario_autoBidStopsImmediatelyIfAlreadyExceedsMaxBid() {
            // currentPrice=150k da cao hon maxBid=130k
            AutoBidStrategy strategy = new AutoBidStrategy(130_000.0, 10_000.0);
            Auction auction = buildRunningAuction();
            auction.setCurrentPrice(150_000.0); // Ai do da dat cao hon maxBid cua minh

            assertThrows(InvalidBidException.class,
                    () -> strategy.validateBid(auction, buildBidder(), 0.0));
        }
    }
}
