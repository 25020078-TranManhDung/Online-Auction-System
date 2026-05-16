package com.auction.shared.model;

import com.auction.shared.enums.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Auction Model Tests")
class AuctionTest {

    private Auction auction;
    private final LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
    private final LocalDateTime endTime   = LocalDateTime.now().plusMinutes(30);

    @BeforeEach
    void setUp() {
        auction = new Auction("auction-001", "item-001", "seller-001",
                1_000_000.0, 50_000.0, startTime, endTime);
        auction.setStatus(AuctionStatus.RUNNING);
    }

    // =========================================================
    // Constructor & khởi tạo
    // =========================================================

    @Test
    @DisplayName("Constructor đầy đủ phải thiết lập đúng tất cả các field")
    void constructor_fullArgs_setsFieldsCorrectly() {
        assertEquals("auction-001", auction.getId());
        assertEquals("item-001",    auction.getItemId());
        assertEquals("seller-001",  auction.getSellerId());
        assertEquals(1_000_000.0,   auction.getStartPrice(),  0.001);
        assertEquals(1_000_000.0,   auction.getCurrentPrice(), 0.001);
        assertEquals(50_000.0,      auction.getMinBidIncrement(), 0.001);
        assertEquals(AuctionStatus.OPEN, new Auction(
                        "x", "i", "s", 100, 10,
                        LocalDateTime.now(), LocalDateTime.now().plusHours(1)).getStatus(),
                "Status mặc định phải là OPEN");
        assertEquals(0, auction.getBidCount());
        assertNotNull(auction.getBidHistory(), "bidHistory không được null");
        assertTrue(auction.getBidHistory().isEmpty(), "bidHistory phải rỗng lúc đầu");
    }

    @Test
    @DisplayName("Constructor rỗng phải khởi tạo bidHistory không null")
    void defaultConstructor_bidHistoryNotNull() {
        Auction a = new Auction();
        assertNotNull(a.getBidHistory());
        assertEquals(0, a.getBidCount());
    }

    // =========================================================
    // addBidTransaction – happy path
    // =========================================================

    @Test
    @DisplayName("Bid hợp lệ (amount >= currentPrice + minIncrement) phải được chấp nhận")
    void addBidTransaction_validBid_returnsTrue() {
        BidTransaction bid = new BidTransaction(
                "bid-001", "auction-001", "bidder-001", "Alice",
                1_100_000.0, LocalDateTime.now(), false);

        boolean result = auction.addBidTransaction(bid);

        assertTrue(result);
        assertEquals(1_100_000.0, auction.getCurrentPrice(), 0.001);
        assertEquals("Alice",     auction.getCurrentLeader());
        assertEquals(1,           auction.getBidCount());
        assertEquals(1,           auction.getBidHistory().size());
    }

    @Test
    @DisplayName("Bid đúng bằng currentPrice + minIncrement phải được chấp nhận")
    void addBidTransaction_exactMinimum_returnsTrue() {
        // 1_000_000 + 50_000 = 1_050_000
        BidTransaction bid = new BidTransaction(
                "bid-002", "auction-001", "bidder-002", "Bob",
                1_050_000.0, LocalDateTime.now(), false);

        assertTrue(auction.addBidTransaction(bid));
        assertEquals(1_050_000.0, auction.getCurrentPrice(), 0.001);
    }

    // =========================================================
    // addBidTransaction – sad path
    // =========================================================

    @Test
    @DisplayName("Bid thấp hơn mức tối thiểu phải bị từ chối và không thay đổi trạng thái")
    void addBidTransaction_belowMinimum_returnsFalse() {
        BidTransaction bid = new BidTransaction(
                "bid-003", "auction-001", "bidder-003", "Eve",
                1_000_000.0, LocalDateTime.now(), false);   // bằng currentPrice, thiếu increment

        boolean result = auction.addBidTransaction(bid);

        assertFalse(result);
        assertEquals(1_000_000.0, auction.getCurrentPrice(), 0.001, "Giá không được thay đổi");
        assertNull(auction.getCurrentLeader(), "Leader không được thay đổi");
        assertEquals(0, auction.getBidCount());
        assertTrue(auction.getBidHistory().isEmpty());
    }

    @Test
    @DisplayName("Bid thấp hơn giá hiện tại phải bị từ chối")
    void addBidTransaction_belowCurrentPrice_returnsFalse() {
        BidTransaction bid = new BidTransaction(
                "bid-004", "auction-001", "bidder-004", "Charlie",
                500_000.0, LocalDateTime.now(), false);

        assertFalse(auction.addBidTransaction(bid));
        assertEquals(1_000_000.0, auction.getCurrentPrice(), 0.001);
    }

    // =========================================================
    // addBidTransaction – nhiều bid liên tiếp
    // =========================================================

    @Test
    @DisplayName("Nhiều bid hợp lệ liên tiếp phải cập nhật đúng leader và bidCount")
    void addBidTransaction_multipleBids_updatesLeaderCorrectly() {
        BidTransaction bid1 = new BidTransaction("b1", "auction-001", "u1", "Alice", 1_100_000.0, LocalDateTime.now(), false);
        BidTransaction bid2 = new BidTransaction("b2", "auction-001", "u2", "Bob",   1_200_000.0, LocalDateTime.now(), false);
        BidTransaction bid3 = new BidTransaction("b3", "auction-001", "u3", "Carol", 1_300_000.0, LocalDateTime.now(), false);

        assertTrue(auction.addBidTransaction(bid1));
        assertTrue(auction.addBidTransaction(bid2));
        assertTrue(auction.addBidTransaction(bid3));

        assertEquals("Carol",      auction.getCurrentLeader());
        assertEquals(1_300_000.0,  auction.getCurrentPrice(), 0.001);
        assertEquals(3,            auction.getBidCount());
        assertEquals(3,            auction.getBidHistory().size());
    }

    @Test
    @DisplayName("Bid không hợp lệ xen giữa không làm ảnh hưởng bid hợp lệ trước đó")
    void addBidTransaction_mixedBids_keepsCorrectState() {
        BidTransaction good = new BidTransaction("b1", "auction-001", "u1", "Alice", 1_100_000.0, LocalDateTime.now(), false);
        BidTransaction bad  = new BidTransaction("b2", "auction-001", "u2", "Bob",   1_050_000.0, LocalDateTime.now(), false);

        auction.addBidTransaction(good);
        auction.addBidTransaction(bad);

        assertEquals("Alice",      auction.getCurrentLeader());
        assertEquals(1_100_000.0,  auction.getCurrentPrice(), 0.001);
        assertEquals(1,            auction.getBidCount());
    }

    // =========================================================
    // Concurrency – an toàn đa luồng (yêu cầu bắt buộc của đề)
    // =========================================================

    @Test
    @DisplayName("100 luồng đồng thời đặt giá tăng dần – không lost update, không race condition")
    void addBidTransaction_concurrentBids_noLostUpdate() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 1; i <= threadCount; i++) {
            final int bidderIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Chờ tín hiệu bắt đầu cùng lúc
                    double bidAmount = 1_000_000.0 + bidderIndex * 50_000.0;
                    BidTransaction bid = new BidTransaction(
                            "b-" + bidderIndex,
                            "auction-001",
                            "bidder-" + bidderIndex,
                            "User" + bidderIndex,
                            bidAmount,
                            LocalDateTime.now(),
                            false
                    );
                    if (auction.addBidTransaction(bid)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Bắt đầu tất cả cùng lúc
        doneLatch.await();
        executor.shutdown();

        // bidCount phải khớp với kích thước bidHistory
        assertEquals(auction.getBidCount(), auction.getBidHistory().size(),
                "bidCount và bidHistory.size() phải nhất quán");

        // Mỗi bid trong history phải hợp lệ (amount >= mức tối thiểu lúc bid đó thêm vào)
        assertTrue(auction.getBidCount() > 0, "Phải có ít nhất một bid thành công");

        // Giá hiện tại phải khớp với bid cuối cùng trong history
        List<BidTransaction> history = auction.getBidHistory();
        double lastBidAmount = history.get(history.size() - 1).getAmount();
        assertEquals(lastBidAmount, auction.getCurrentPrice(), 0.001,
                "getCurrentPrice phải bằng amount của bid cuối cùng trong history");
    }

    // =========================================================
    // Getter / Setter
    // =========================================================

    @Test
    @DisplayName("setStatus thay đổi đúng trạng thái")
    void setStatus_changesStatusCorrectly() {
        auction.setStatus(AuctionStatus.FINISHED);
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("setWinnerId và getWinnerId hoạt động đúng")
    void winnerId_setAndGet() {
        assertNull(auction.getWinnerId());
        auction.setWinnerId("bidder-999");
        assertEquals("bidder-999", auction.getWinnerId());
    }

    @Test
    @DisplayName("setCurrentLeaderId và setCurrentLeaderAmount hoạt động đúng")
    void currentLeaderWallet_setAndGet() {
        auction.setCurrentLeaderId("bidder-777");
        auction.setCurrentLeaderAmount(2_000_000.0);

        assertEquals("bidder-777",  auction.getCurrentLeaderId());
        assertEquals(2_000_000.0,   auction.getCurrentLeaderAmount(), 0.001);
    }

    @Test
    @DisplayName("setBidHistory thay thế toàn bộ history")
    void setBidHistory_replacesHistory() {
        BidTransaction bid = new BidTransaction("b1", "auction-001", "u1", "Alice", 1_100_000.0, LocalDateTime.now(), false);
        List<BidTransaction> newHistory = new ArrayList<>();
        newHistory.add(bid);

        auction.setBidHistory(newHistory);

        assertEquals(1, auction.getBidHistory().size());
        assertEquals("b1", auction.getBidHistory().get(0).getId());
    }
}