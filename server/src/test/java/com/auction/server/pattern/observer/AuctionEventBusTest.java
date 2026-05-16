package com.auction.server.pattern.observer;

import com.auction.server.observer.AuctionEventBus;
import com.auction.server.observer.AuctionObserver;
import com.auction.shared.model.Auction;
import com.auction.shared.model.BidTransaction;
import com.auction.shared.enums.AuctionStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho AuctionEventBus (Observer Pattern + Singleton Pattern).
 *
 * KEY: AuctionEventBus là Singleton volatile — phải reset field `instance`
 * về null sau mỗi test để tránh state rò rỉ giữa các test case.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionEventBus Tests")
class AuctionEventBusTest {

    @Mock private AuctionObserver observerA;
    @Mock private AuctionObserver observerB;

    private AuctionEventBus bus;

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Reset Singleton instance về null trước/sau mỗi test
     */
    private void resetSingleton() throws Exception {
        Field field = AuctionEventBus.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private Auction buildAuction(String id) {
        Auction a = new Auction();
        a.setId(id);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartPrice(100.0);
        a.setCurrentPrice(100.0);
        a.setEndTime(LocalDateTime.now().plusHours(1));
        return a;
    }

    private BidTransaction buildBid(String auctionId, String bidderId, double amount) {
        return new BidTransaction(
                "tx-001", auctionId, bidderId, "alice", amount, LocalDateTime.now(), false);
    }

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
        bus = AuctionEventBus.getInstance();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    // =========================================================
    //  Singleton
    // =========================================================
    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("getInstance() luon tra ve cung mot instance")
        void getInstance_returnsSameInstance() {
            AuctionEventBus first  = AuctionEventBus.getInstance();
            AuctionEventBus second = AuctionEventBus.getInstance();
            assertSame(first, second);
        }

        @Test
        @DisplayName("Sau khi reset, getInstance() tao instance moi")
        void getInstance_afterReset_createsNewInstance() throws Exception {
            AuctionEventBus before = AuctionEventBus.getInstance();
            resetSingleton();
            AuctionEventBus after = AuctionEventBus.getInstance();
            assertNotSame(before, after);
        }
    }

    // =========================================================
    //  subscribe / unsubscribe
    // =========================================================
    @Nested
    @DisplayName("subscribe() va unsubscribe()")
    class SubscriptionTests {

        @Test
        @DisplayName("subscribe() them observer vao danh sach")
        void subscribe_addsObserver() {
            bus.subscribe(observerA);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStarted(a);

            verify(observerA, times(1)).onAuctionStarted(a);
        }

        @Test
        @DisplayName("subscribe() cung mot observer 2 lan - chi goi 1 lan")
        void subscribe_duplicate_doesNotAddTwice() {
            bus.subscribe(observerA);
            bus.subscribe(observerA); // lan 2, phai bi bo qua

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStarted(a);

            // Chi duoc goi 1 lan, khong phai 2
            verify(observerA, times(1)).onAuctionStarted(a);
        }

        @Test
        @DisplayName("unsubscribe() xoa observer khoi danh sach")
        void unsubscribe_removesObserver() {
            bus.subscribe(observerA);
            bus.unsubscribe(observerA);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStarted(a);

            verify(observerA, never()).onAuctionStarted(any());
        }

        @Test
        @DisplayName("unsubscribe() observer chua dang ky - khong throw exception")
        void unsubscribe_notRegistered_doesNotThrow() {
            assertDoesNotThrow(() -> bus.unsubscribe(observerA));
        }

        @Test
        @DisplayName("Nhieu observer - tat ca deu nhan su kien")
        void subscribe_multipleObservers_allReceiveEvent() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStarted(a);

            verify(observerA, times(1)).onAuctionStarted(a);
            verify(observerB, times(1)).onAuctionStarted(a);
        }

        @Test
        @DisplayName("Unsubscribe 1 trong 2 observer - observer con lai van nhan duoc")
        void unsubscribe_one_otherStillReceives() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);
            bus.unsubscribe(observerA);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStarted(a);

            verify(observerA, never()).onAuctionStarted(any());
            verify(observerB, times(1)).onAuctionStarted(a);
        }
    }

    // =========================================================
    //  publishBidPlaced
    // =========================================================
    @Nested
    @DisplayName("publishBidPlaced()")
    class PublishBidPlacedTests {

        @Test
        @DisplayName("Goi onBidPlaced() tren tat ca observer dang ky")
        void publishBidPlaced_callsAllObservers() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);

            Auction a      = buildAuction("auc-1");
            BidTransaction b = buildBid("auc-1", "bidder-1", 150.0);

            bus.publishBidPlaced(a, b);

            verify(observerA, times(1)).onBidPlaced(a, b);
            verify(observerB, times(1)).onBidPlaced(a, b);
        }

        @Test
        @DisplayName("Khong co observer nao - khong throw exception")
        void publishBidPlaced_noObservers_doesNotThrow() {
            Auction a      = buildAuction("auc-1");
            BidTransaction b = buildBid("auc-1", "bidder-1", 150.0);
            assertDoesNotThrow(() -> bus.publishBidPlaced(a, b));
        }

        @Test
        @DisplayName("Truyen dung doi tuong Auction va BidTransaction vao observer")
        void publishBidPlaced_passesCorrectArguments() {
            bus.subscribe(observerA);

            Auction a      = buildAuction("auc-XYZ");
            BidTransaction b = buildBid("auc-XYZ", "bidder-99", 999.0);

            bus.publishBidPlaced(a, b);

            verify(observerA).onBidPlaced(
                    argThat(auction -> "auc-XYZ".equals(auction.getId())),
                    argThat(bid    -> bid.getAmount() == 999.0)
            );
        }
    }

    // =========================================================
    //  publishAuctionStarted
    // =========================================================
    @Nested
    @DisplayName("publishAuctionStarted()")
    class PublishAuctionStartedTests {

        @Test
        @DisplayName("Goi onAuctionStarted() tren tat ca observer")
        void publishAuctionStarted_callsAllObservers() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);

            Auction a = buildAuction("auc-start");
            bus.publishAuctionStarted(a);

            verify(observerA, times(1)).onAuctionStarted(a);
            verify(observerB, times(1)).onAuctionStarted(a);
        }

        @Test
        @DisplayName("Khong goi cac event khac khi publish AuctionStarted")
        void publishAuctionStarted_doesNotCallOtherEvents() {
            bus.subscribe(observerA);

            Auction a = buildAuction("auc-start");
            bus.publishAuctionStarted(a);

            verify(observerA, never()).onBidPlaced(any(), any());
            verify(observerA, never()).onAuctionClosed(any());
            verify(observerA, never()).onAuctionExtended(any(), anyLong());
        }
    }

    // =========================================================
    //  publishAuctionClosed
    // =========================================================
    @Nested
    @DisplayName("publishAuctionClosed()")
    class PublishAuctionClosedTests {

        @Test
        @DisplayName("Goi onAuctionClosed() tren tat ca observer")
        void publishAuctionClosed_callsAllObservers() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);

            Auction a = buildAuction("auc-close");
            bus.publishAuctionClosed(a);

            verify(observerA, times(1)).onAuctionClosed(a);
            verify(observerB, times(1)).onAuctionClosed(a);
        }
    }

    // =========================================================
    //  publishAuctionStatusChanged
    // =========================================================
    @Nested
    @DisplayName("publishAuctionStatusChanged()")
    class PublishAuctionStatusChangedTests {

        @Test
        @DisplayName("Goi onAuctionStatusChanged() voi status PAID")
        void publishAuctionStatusChanged_paid_callsObservers() {
            bus.subscribe(observerA);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStatusChanged(a, "PAID");

            verify(observerA, times(1)).onAuctionStatusChanged(a, "PAID");
        }

        @Test
        @DisplayName("Goi onAuctionStatusChanged() voi status CANCELED")
        void publishAuctionStatusChanged_canceled_callsObservers() {
            bus.subscribe(observerA);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStatusChanged(a, "CANCELED");

            verify(observerA, times(1)).onAuctionStatusChanged(a, "CANCELED");
        }

        @Test
        @DisplayName("Truyen dung ten status vao observer")
        void publishAuctionStatusChanged_passesCorrectStatus() {
            bus.subscribe(observerA);

            Auction a = buildAuction("auc-1");
            bus.publishAuctionStatusChanged(a, "PAID");

            verify(observerA).onAuctionStatusChanged(any(), eq("PAID"));
        }
    }

    // =========================================================
    //  publishAuctionExtended
    // =========================================================
    @Nested
    @DisplayName("publishAuctionExtended()")
    class PublishAuctionExtendedTests {

        @Test
        @DisplayName("Goi onAuctionExtended() voi dung so giay gia han")
        void publishAuctionExtended_callsObserversWithCorrectSeconds() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);

            Auction a = buildAuction("auc-extend");
            bus.publishAuctionExtended(a, 60L);

            verify(observerA, times(1)).onAuctionExtended(a, 60L);
            verify(observerB, times(1)).onAuctionExtended(a, 60L);
        }

        @Test
        @DisplayName("Truyen dung gia tri extraSeconds vao observer")
        void publishAuctionExtended_passesCorrectExtraSeconds() {
            bus.subscribe(observerA);

            Auction a = buildAuction("auc-extend");
            bus.publishAuctionExtended(a, 120L);

            verify(observerA).onAuctionExtended(any(), eq(120L));
        }
    }

    // =========================================================
    //  publishError
    // =========================================================
    @Nested
    @DisplayName("publishError()")
    class PublishErrorTests {

        @Test
        @DisplayName("Goi onError() tren tat ca observer voi dung errorCode va message")
        void publishError_callsAllObserversWithCorrectArgs() {
            bus.subscribe(observerA);
            bus.subscribe(observerB);

            Auction a = buildAuction("auc-err");
            bus.publishError(a, "AUTO_BID_FAILED", "So du khong du");

            verify(observerA, times(1)).onError(a, "AUTO_BID_FAILED", "So du khong du");
            verify(observerB, times(1)).onError(a, "AUTO_BID_FAILED", "So du khong du");
        }

        @Test
        @DisplayName("Khong co observer - publishError khong throw exception")
        void publishError_noObservers_doesNotThrow() {
            Auction a = buildAuction("auc-err");
            assertDoesNotThrow(() -> bus.publishError(a, "AUTO_BID_FAILED", "loi gi do"));
        }
    }

    // =========================================================
    //  Isolation giua cac test
    // =========================================================
    @Nested
    @DisplayName("Test isolation - khong ro ri state giua cac test")
    class IsolationTests {

        @Test
        @DisplayName("Observer dang ky o test nay khong anh huong test khac (test 1)")
        void isolation_test1_subscriberDoesNotLeak() {
            // Subscribe trong test nay
            bus.subscribe(observerA);
            // Chi verify trong pham vi test nay
            Auction a = buildAuction("iso-1");
            bus.publishAuctionStarted(a);
            verify(observerA, times(1)).onAuctionStarted(a);
        }

        @Test
        @DisplayName("Observer dang ky o test nay khong anh huong test khac (test 2)")
        void isolation_test2_freshBusHasNoObservers() {
            // Bus moi (sau @BeforeEach reset) khong co observer nao
            Auction a = buildAuction("iso-2");
            bus.publishAuctionStarted(a);
            // observerA chua subscribe nen khong duoc goi
            verify(observerA, never()).onAuctionStarted(any());
        }
    }
}