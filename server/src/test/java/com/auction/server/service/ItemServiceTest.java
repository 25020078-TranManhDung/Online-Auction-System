
package com.auction.server.service;

import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.util.TokenUtil;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.exception.AuctionException;
import com.auction.shared.exception.ResourceNotFoundException;
import com.auction.shared.exception.UnauthorizedException;
import com.auction.shared.model.item.Item;
import com.auction.shared.model.user.Bidder;
import com.auction.shared.model.user.Seller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho ItemService.
 *
 * ItemService khong co Singleton nen don gian hon:
 * chi can mock ItemDAO, UserDAO va TokenUtil (static).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService Tests")
class ItemServiceTest {

    @Mock private ItemDAO itemDAO;
    @Mock private UserDAO userDAO;

    private ItemService itemService;

    private static final String VALID_TOKEN   = "valid-token";
    private static final String SELLER_ID     = "seller-001";
    private static final String OTHER_SELLER  = "seller-002";
    private static final String ITEM_ID       = "item-abc";

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemDAO, userDAO);
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────
    private Seller buildSeller() {
        Seller s = new Seller();
        s.setId(SELLER_ID);
        s.setUsername("bob_seller");
        return s;
    }

    private Bidder buildBidder() {
        Bidder b = new Bidder();
        b.setId("bidder-001");
        b.setUsername("alice");
        return b;
    }

    private Map<String, Object> buildItemData() {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Laptop Gaming");
        data.put("description", "RTX 4090, RAM 32GB");
        data.put("category", "ELECTRONICS");
        data.put("startingPrice", 5_000_000.0);
        return data;
    }

    private Item buildItem(String sellerId) {
        // Dung ItemFactory de tao item that
        Map<String, Object> data = buildItemData();
        Item item = com.auction.server.pattern.factory.ItemFactory.createItem(
                ItemCategory.ELECTRONICS, data);
        item.setId(ITEM_ID);
        item.setSellerId(sellerId);
        return item;
    }

    // =========================================================
    //  createItem()
    // =========================================================
    @Nested
    @DisplayName("createItem()")
    class CreateItemTests {

        @Test
        @DisplayName("Seller hop le tao item -> tra ve Item da luu")
        void createItem_seller_success() {
            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDAO.save(any(Item.class))).thenReturn(true);

                Item result = itemService.createItem(buildItemData(), VALID_TOKEN);

                assertNotNull(result);
                assertEquals("Laptop Gaming", result.getTitle());
                assertEquals(SELLER_ID, result.getSellerId());
                assertEquals(ItemCategory.ELECTRONICS, result.getCategory());

                verify(itemDAO, times(1)).save(any(Item.class));
            }
        }

        @Test
        @DisplayName("Token khong hop le -> nem UnauthorizedException")
        void createItem_invalidToken_throwsUnauthorized() {
            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId("bad-token")).thenReturn(null);

                assertThrows(UnauthorizedException.class,
                        () -> itemService.createItem(buildItemData(), "bad-token"));

                verify(itemDAO, never()).save(any());
            }
        }

        @Test
        @DisplayName("User khong ton tai -> nem UserNotFoundException")
        void createItem_userNotFound_throwsException() {
            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(null);

                assertThrows(Exception.class,
                        () -> itemService.createItem(buildItemData(), VALID_TOKEN));

                verify(itemDAO, never()).save(any());
            }
        }

        @Test
        @DisplayName("Bidder co gang tao item -> nem UnauthorizedException")
        void createItem_bidder_throwsUnauthorized() {
            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn("bidder-001");
                when(userDAO.findById("bidder-001")).thenReturn(buildBidder());

                UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                        () -> itemService.createItem(buildItemData(), VALID_TOKEN));

                assertTrue(ex.getMessage().contains("Seller"),
                        "Thong bao phai de cap quyen Seller");
                verify(itemDAO, never()).save(any());
            }
        }

        @Test
        @DisplayName("Category null -> nem AuctionException INVALID_DATA")
        void createItem_nullCategory_throwsException() {
            Map<String, Object> data = buildItemData();
            data.remove("category"); // Xoa category

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(buildSeller());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> itemService.createItem(data, VALID_TOKEN));

                assertEquals("INVALID_DATA", ex.getCode());
                verify(itemDAO, never()).save(any());
            }
        }

        @Test
        @DisplayName("Category rong -> nem AuctionException INVALID_DATA")
        void createItem_emptyCategory_throwsException() {
            Map<String, Object> data = buildItemData();
            data.put("category", "");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(buildSeller());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> itemService.createItem(data, VALID_TOKEN));

                assertEquals("INVALID_DATA", ex.getCode());
            }
        }

        @Test
        @DisplayName("Category khong hop le (rac) -> nem AuctionException INVALID_DATA")
        void createItem_invalidCategory_throwsException() {
            Map<String, Object> data = buildItemData();
            data.put("category", "INVALID_CATEGORY_XYZ");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(buildSeller());

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> itemService.createItem(data, VALID_TOKEN));

                assertEquals("INVALID_DATA", ex.getCode());
                assertTrue(ex.getMessage().contains("INVALID_CATEGORY_XYZ"));
            }
        }

        @Test
        @DisplayName("Category chu thuong -> tu dong chuyen hoa, hop le")
        void createItem_lowercaseCategory_normalizedSuccessfully() {
            Map<String, Object> data = buildItemData();
            data.put("category", "electronics"); // Chu thuong

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDAO.save(any())).thenReturn(true);

                Item result = itemService.createItem(data, VALID_TOKEN);

                assertNotNull(result);
                assertEquals(ItemCategory.ELECTRONICS, result.getCategory());
            }
        }

        @Test
        @DisplayName("sellerId duoc gan dung tu token, khong tu client")
        void createItem_sellerIdSetFromToken_notFromClient() {
            Map<String, Object> data = buildItemData();
            data.put("sellerId", "fake-seller-id"); // Client co gang chen sellerId

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);
                when(userDAO.findById(SELLER_ID)).thenReturn(buildSeller());
                when(itemDAO.save(any())).thenReturn(true);

                Item result = itemService.createItem(data, VALID_TOKEN);

                // sellerId phai la tu token (SELLER_ID), khong phai fake-seller-id
                assertEquals(SELLER_ID, result.getSellerId());
                assertNotEquals("fake-seller-id", result.getSellerId());
            }
        }
    }

    // =========================================================
    //  getItem()
    // =========================================================
    @Nested
    @DisplayName("getItem()")
    class GetItemTests {

        @Test
        @DisplayName("Item ton tai -> tra ve Item dung")
        void getItem_exists_returnsItem() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Item result = itemService.getItem(ITEM_ID);

            assertNotNull(result);
            assertEquals(ITEM_ID, result.getId());
            assertEquals("Laptop Gaming", result.getTitle());
            assertEquals(SELLER_ID, result.getSellerId());
        }

        @Test
        @DisplayName("Item khong ton tai -> nem ResourceNotFoundException ITEM_NOT_FOUND")
        void getItem_notFound_throwsException() {
            when(itemDAO.findById("bad-id")).thenReturn(null);

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> itemService.getItem("bad-id"));

            assertEquals("ITEM_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("itemId null -> nem ResourceNotFoundException")
        void getItem_nullId_throwsException() {
            when(itemDAO.findById(null)).thenReturn(null);

            assertThrows(ResourceNotFoundException.class,
                    () -> itemService.getItem(null));
        }
    }

    // =========================================================
    //  updateItem()
    // =========================================================
    @Nested
    @DisplayName("updateItem()")
    class UpdateItemTests {

        @Test
        @DisplayName("Chu so huu cap nhat title -> tra ve Item da cap nhat")
        void updateItem_owner_success() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", ITEM_ID);
            data.put("title", "Laptop Gaming Pro");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);

                Item result = itemService.updateItem(data, VALID_TOKEN);

                assertEquals("Laptop Gaming Pro", result.getTitle());
                verify(itemDAO, times(1)).update(item);
            }
        }

        @Test
        @DisplayName("Cap nhat description -> chi doi description, title giu nguyen")
        void updateItem_description_onlyDescriptionChanges() {
            Item item = buildItem(SELLER_ID);
            String originalTitle = item.getTitle();
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", ITEM_ID);
            data.put("description", "Mo ta moi");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);

                Item result = itemService.updateItem(data, VALID_TOKEN);

                assertEquals("Mo ta moi", result.getDescription());
                assertEquals(originalTitle, result.getTitle()); // Title khong doi
            }
        }

        @Test
        @DisplayName("Cap nhat startingPrice -> gia duoc doi chinh xac")
        void updateItem_startingPrice_updatesCorrectly() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", ITEM_ID);
            data.put("startingPrice", "8000000.0");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);

                Item result = itemService.updateItem(data, VALID_TOKEN);

                assertEquals(8_000_000.0, result.getStartingPrice());
            }
        }

        @Test
        @DisplayName("Khong phai chu so huu -> nem UnauthorizedException")
        void updateItem_notOwner_throwsUnauthorized() {
            Item item = buildItem(SELLER_ID); // Chu la SELLER_ID
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", ITEM_ID);
            data.put("title", "Hack title");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(OTHER_SELLER); // Seller khac

                assertThrows(UnauthorizedException.class,
                        () -> itemService.updateItem(data, VALID_TOKEN));

                verify(itemDAO, never()).update(any());
            }
        }

        @Test
        @DisplayName("Token het han -> nem UnauthorizedException")
        void updateItem_invalidToken_throwsUnauthorized() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", ITEM_ID);
            data.put("title", "New Title");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId("expired-token")).thenReturn(null);

                assertThrows(UnauthorizedException.class,
                        () -> itemService.updateItem(data, "expired-token"));

                verify(itemDAO, never()).update(any());
            }
        }

        @Test
        @DisplayName("Khong co itemId trong data -> nem AuctionException INVALID_REQUEST")
        void updateItem_missingItemId_throwsException() {
            Map<String, Object> data = new HashMap<>();
            data.put("title", "New Title"); // Thieu itemId

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                AuctionException ex = assertThrows(AuctionException.class,
                        () -> itemService.updateItem(data, VALID_TOKEN));

                assertEquals("INVALID_REQUEST", ex.getCode());
                verify(itemDAO, never()).update(any());
            }
        }

        @Test
        @DisplayName("Item khong ton tai -> nem ResourceNotFoundException")
        void updateItem_itemNotFound_throwsException() {
            when(itemDAO.findById("ghost-id")).thenReturn(null);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", "ghost-id");
            data.put("title", "New Title");

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                assertThrows(ResourceNotFoundException.class,
                        () -> itemService.updateItem(data, VALID_TOKEN));

                verify(itemDAO, never()).update(any());
            }
        }

        @Test
        @DisplayName("Data rong (khong co field nao) -> khong thay doi, van goi update")
        void updateItem_emptyData_callsUpdateWithUnchangedItem() {
            Item item = buildItem(SELLER_ID);
            String originalTitle = item.getTitle();
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            Map<String, Object> data = new HashMap<>();
            data.put("itemId", ITEM_ID); // Chi co itemId, khong co gi de cap nhat

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);

                Item result = itemService.updateItem(data, VALID_TOKEN);

                // Item khong doi
                assertEquals(originalTitle, result.getTitle());
                // Nhung van goi update (de cap nhat timestamp neu co)
                verify(itemDAO, times(1)).update(item);
            }
        }
    }

    // =========================================================
    //  deleteItem()
    // =========================================================
    @Nested
    @DisplayName("deleteItem()")
    class DeleteItemTests {

        @Test
        @DisplayName("Chu so huu xoa item -> xoa thanh cong")
        void deleteItem_owner_success() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);

                assertDoesNotThrow(() -> itemService.deleteItem(ITEM_ID, VALID_TOKEN));

                verify(itemDAO, times(1)).delete(ITEM_ID);
            }
        }

        @Test
        @DisplayName("Khong phai chu so huu -> nem UnauthorizedException, KHONG xoa")
        void deleteItem_notOwner_throwsUnauthorized() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(OTHER_SELLER);

                assertThrows(UnauthorizedException.class,
                        () -> itemService.deleteItem(ITEM_ID, VALID_TOKEN));

                verify(itemDAO, never()).delete(any());
            }
        }

        @Test
        @DisplayName("Item khong ton tai -> nem ResourceNotFoundException, KHONG xoa")
        void deleteItem_itemNotFound_throwsException() {
            when(itemDAO.findById("ghost-id")).thenReturn(null);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                assertThrows(ResourceNotFoundException.class,
                        () -> itemService.deleteItem("ghost-id", VALID_TOKEN));

                verify(itemDAO, never()).delete(any());
            }
        }

        @Test
        @DisplayName("Token null -> nem exception, KHONG xoa")
        void deleteItem_nullToken_throwsException() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(null)).thenReturn(null);

                // getSellerId().equals(null) -> NullPointerException hoac UnauthorizedException
                assertThrows(Exception.class,
                        () -> itemService.deleteItem(ITEM_ID, null));

                verify(itemDAO, never()).delete(any());
            }
        }

        @Test
        @DisplayName("Xoa dung item theo ID, khong xoa nham item khac")
        void deleteItem_deletesCorrectItemId() {
            Item item = buildItem(SELLER_ID);
            when(itemDAO.findById(ITEM_ID)).thenReturn(item);

            try (MockedStatic<TokenUtil> tu = mockStatic(TokenUtil.class)) {
                tu.when(() -> TokenUtil.getUserId(VALID_TOKEN)).thenReturn(SELLER_ID);

                itemService.deleteItem(ITEM_ID, VALID_TOKEN);

                // Xac nhan xoa dung ITEM_ID, khong phai bat ky id nao khac
                verify(itemDAO).delete(ITEM_ID);
                verify(itemDAO, never()).delete(argThat(id -> !id.equals(ITEM_ID)));
            }
        }
    }
}
