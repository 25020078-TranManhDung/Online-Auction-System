package com.auction.server.pattern.singleton;

import com.auction.shared.model.Auction;
import com.auction.shared.enums.AuctionStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho AuctionManager (Singleton Pattern + Thread-safe Map).
 *
 * KEY: AuctionManager dung ConcurrentHashMap ben trong va la Singleton volatile.
 * Phai reset field `instance` ve null sau moi test de tranh state ro ri.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionManager Tests")
class AuctionManagerTest {

    private AuctionManager manager;

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Reset Singleton instance ve null truoc/sau moi test.
     */
    private void resetSingleton() throws Exception {
        Field field = AuctionManager.class.getDeclaredField("instance");
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
        a.setSellerId("seller-001");
        return a;
    }

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
        manager = AuctionManager.getInstance();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    // =========================================================
    //  Singleton Pattern
    // =========================================================
    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("getInstance() luon tra ve cung mot instance")
        void getInstance_returnsSameInstance() {
            AuctionManager first  = AuctionManager.getInstance();
            AuctionManager second = AuctionManager.getInstance();
            assertSame(first, second);
        }

        @Test
        @DisplayName("getInstance() tra ve non-null")
        void getInstance_returnsNonNull() {
            assertNotNull(AuctionManager.getInstance());
        }

        @Test
        @DisplayName("Sau khi reset, getInstance() tao instance moi")
        void getInstance_afterReset_createsNewInstance() throws Exception {
            AuctionManager before = AuctionManager.getInstance();
            resetSingleton();
            AuctionManager after = AuctionManager.getInstance();
            assertNotSame(before, after);
        }

        @Test
        @DisplayName("Instance moi sau reset khong chua du lieu cu")
        void getInstance_afterReset_hasCleanState() throws Exception {
            // Them du lieu vao instance cu
            manager.addAuction(buildAuction("auc-old"));
            assertTrue(manager.isActive("auc-old"));

            // Reset va lay instance moi
            resetSingleton();
            AuctionManager fresh = AuctionManager.getInstance();

            // Instance moi khong co du lieu cu
            assertFalse(fresh.isActive("auc-old"));
            assertTrue(fresh.getAll().isEmpty());
        }
    }

    // =========================================================
    //  addAuction
    // =========================================================
    @Nested
    @DisplayName("addAuction()")
    class AddAuctionTests {

        @Test
        @DisplayName("addAuction() them auction vao map")
        void addAuction_auctionIsStored() {
            Auction a = buildAuction("auc-1");
            manager.addAuction(a);

            assertNotNull(manager.getAuction("auc-1"));
        }

        @Test
        @DisplayName("addAuction() nhieu auction - tat ca duoc luu")
        void addAuction_multiple_allStored() {
            manager.addAuction(buildAuction("auc-A"));
            manager.addAuction(buildAuction("auc-B"));
            manager.addAuction(buildAuction("auc-C"));

            assertNotNull(manager.getAuction("auc-A"));
            assertNotNull(manager.getAuction("auc-B"));
            assertNotNull(manager.getAuction("auc-C"));
        }

        @Test
        @DisplayName("addAuction() voi cung id - ghi de auction cu")
        void addAuction_sameId_overwritesPrevious() {
            Auction original = buildAuction("auc-1");
            original.setCurrentPrice(100.0);

            Auction updated = buildAuction("auc-1");
            updated.setCurrentPrice(200.0);

            manager.addAuction(original);
            manager.addAuction(updated);

            assertEquals(200.0, manager.getAuction("auc-1").getCurrentPrice());
        }

        @Test
        @DisplayName("addAuction() luu dung doi tuong Auction (same reference)")
        void addAuction_storesSameReference() {
            Auction a = buildAuction("auc-ref");
            manager.addAuction(a);

            assertSame(a, manager.getAuction("auc-ref"));
        }
    }

    // =========================================================
    //  getAuction
    // =========================================================
    @Nested
    @DisplayName("getAuction()")
    class GetAuctionTests {

        @Test
        @DisplayName("getAuction() voi id ton tai - tra ve dung auction")
        void getAuction_existingId_returnsAuction() {
            Auction a = buildAuction("auc-get");
            manager.addAuction(a);

            Auction result = manager.getAuction("auc-get");

            assertNotNull(result);
            assertEquals("auc-get", result.getId());
        }

        @Test
        @DisplayName("getAuction() voi id khong ton tai - tra ve null")
        void getAuction_nonExistentId_returnsNull() {
            assertNull(manager.getAuction("auc-ghost"));
        }

        @Test
        @DisplayName("getAuction() voi id null - nem NullPointerException (ConcurrentHashMap khong cho phep null key)")
        void getAuction_nullId_throwsNPE() {
            assertThrows(NullPointerException.class, () -> manager.getAuction(null));
        }
    }

    // =========================================================
    //  removeAuction
    // =========================================================
    @Nested
    @DisplayName("removeAuction()")
    class RemoveAuctionTests {

        @Test
        @DisplayName("removeAuction() xoa auction khoi map")
        void removeAuction_auctionIsRemoved() {
            manager.addAuction(buildAuction("auc-rm"));
            manager.removeAuction("auc-rm");

            assertNull(manager.getAuction("auc-rm"));
        }

        @Test
        @DisplayName("removeAuction() chi xoa dung auction do, khong anh huong cai khac")
        void removeAuction_onlyRemovesTargetAuction() {
            manager.addAuction(buildAuction("auc-1"));
            manager.addAuction(buildAuction("auc-2"));
            manager.addAuction(buildAuction("auc-3"));

            manager.removeAuction("auc-2");

            assertNotNull(manager.getAuction("auc-1"));
            assertNull(manager.getAuction("auc-2"));
            assertNotNull(manager.getAuction("auc-3"));
        }

        @Test
        @DisplayName("removeAuction() voi id khong ton tai - khong throw exception")
        void removeAuction_nonExistentId_doesNotThrow() {
            assertDoesNotThrow(() -> manager.removeAuction("auc-ghost"));
        }

        @Test
        @DisplayName("removeAuction() voi id null - nem NullPointerException (ConcurrentHashMap khong cho phep null key)")
        void removeAuction_nullId_throwsNPE() {
            assertThrows(NullPointerException.class, () -> manager.removeAuction(null));
        }

        @Test
        @DisplayName("removeAuction() xoa xong -> isActive tra ve false")
        void removeAuction_afterRemove_isActiveReturnsFalse() {
            manager.addAuction(buildAuction("auc-rm"));
            manager.removeAuction("auc-rm");

            assertFalse(manager.isActive("auc-rm"));
        }
    }

    // =========================================================
    //  isActive
    // =========================================================
    @Nested
    @DisplayName("isActive()")
    class IsActiveTests {

        @Test
        @DisplayName("isActive() voi auction da them - tra ve true")
        void isActive_existingAuction_returnsTrue() {
            manager.addAuction(buildAuction("auc-live"));
            assertTrue(manager.isActive("auc-live"));
        }

        @Test
        @DisplayName("isActive() voi auction chua them - tra ve false")
        void isActive_nonExistentAuction_returnsFalse() {
            assertFalse(manager.isActive("auc-ghost"));
        }

        @Test
        @DisplayName("isActive() voi id null - nem NullPointerException (ConcurrentHashMap khong cho phep null key)")
        void isActive_nullId_throwsNPE() {
            assertThrows(NullPointerException.class, () -> manager.isActive(null));
        }

        @Test
        @DisplayName("isActive() sau khi remove - tra ve false")
        void isActive_afterRemove_returnsFalse() {
            manager.addAuction(buildAuction("auc-rm"));
            assertTrue(manager.isActive("auc-rm"));

            manager.removeAuction("auc-rm");
            assertFalse(manager.isActive("auc-rm"));
        }
    }

    // =========================================================
    //  getAll
    // =========================================================
    @Nested
    @DisplayName("getAll()")
    class GetAllTests {

        @Test
        @DisplayName("getAll() khi chua them gi - tra ve collection rong")
        void getAll_empty_returnsEmptyCollection() {
            Collection<Auction> all = manager.getAll();
            assertNotNull(all);
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("getAll() tra ve dung so luong auction da them")
        void getAll_returnsAllAuctions() {
            manager.addAuction(buildAuction("auc-1"));
            manager.addAuction(buildAuction("auc-2"));
            manager.addAuction(buildAuction("auc-3"));

            assertEquals(3, manager.getAll().size());
        }

        @Test
        @DisplayName("getAll() sau removeAuction() phan anh dung so luong con lai")
        void getAll_afterRemove_reflectsCurrentState() {
            manager.addAuction(buildAuction("auc-1"));
            manager.addAuction(buildAuction("auc-2"));
            manager.removeAuction("auc-1");

            Collection<Auction> all = manager.getAll();
            assertEquals(1, all.size());
            assertTrue(all.stream().anyMatch(a -> "auc-2".equals(a.getId())));
        }

        @Test
        @DisplayName("getAll() chua tat ca auction da duoc them")
        void getAll_containsAllAddedAuctions() {
            Auction a1 = buildAuction("auc-A");
            Auction a2 = buildAuction("auc-B");
            manager.addAuction(a1);
            manager.addAuction(a2);

            Collection<Auction> all = manager.getAll();
            assertTrue(all.stream().anyMatch(a -> "auc-A".equals(a.getId())));
            assertTrue(all.stream().anyMatch(a -> "auc-B".equals(a.getId())));
        }
    }

    // =========================================================
    //  Luong nghiep vu tong hop
    // =========================================================
    @Nested
    @DisplayName("Luong nghiep vu tong hop")
    class WorkflowTests {

        @Test
        @DisplayName("Add -> Get -> isActive -> Remove -> Get lai -> isActive lai")
        void fullLifecycle_addGetRemove() {
            Auction a = buildAuction("auc-lifecycle");

            // Chua add
            assertNull(manager.getAuction("auc-lifecycle"));
            assertFalse(manager.isActive("auc-lifecycle"));

            // Add
            manager.addAuction(a);
            assertNotNull(manager.getAuction("auc-lifecycle"));
            assertTrue(manager.isActive("auc-lifecycle"));
            assertEquals(1, manager.getAll().size());

            // Remove
            manager.removeAuction("auc-lifecycle");
            assertNull(manager.getAuction("auc-lifecycle"));
            assertFalse(manager.isActive("auc-lifecycle"));
            assertTrue(manager.getAll().isEmpty());
        }

        @Test
        @DisplayName("Nhieu auction - add/remove an toan, khong anh huong nhau")
        void multipleAuctions_independentLifecycle() {
            Auction a1 = buildAuction("auc-X");
            Auction a2 = buildAuction("auc-Y");
            Auction a3 = buildAuction("auc-Z");

            manager.addAuction(a1);
            manager.addAuction(a2);
            manager.addAuction(a3);
            assertEquals(3, manager.getAll().size());

            manager.removeAuction("auc-Y");
            assertEquals(2, manager.getAll().size());
            assertTrue(manager.isActive("auc-X"));
            assertFalse(manager.isActive("auc-Y"));
            assertTrue(manager.isActive("auc-Z"));
        }

        @Test
        @DisplayName("cap nhat currentPrice qua getAuction() - object la cung reference")
        void updateAuctionState_viaReference_isReflected() {
            Auction a = buildAuction("auc-update");
            manager.addAuction(a);

            // Lay ra va thay doi tren reference
            Auction retrieved = manager.getAuction("auc-update");
            retrieved.setCurrentPrice(500.0);

            // Lay lai lan nua phai thay gia moi
            assertEquals(500.0, manager.getAuction("auc-update").getCurrentPrice());
        }
    }
}