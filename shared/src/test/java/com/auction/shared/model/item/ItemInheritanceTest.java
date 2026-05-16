package com.auction.shared.model.item;

import com.auction.shared.enums.ItemCategory;
import com.auction.shared.model.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Item – Cây kế thừa (Inheritance & Encapsulation)")
class ItemInheritanceTest {

    // =====================================================================
    // 1. CẤU TRÚC CÂY KẾ THỪA – kiểm tra bằng instanceof / isAssignableFrom
    // =====================================================================

    @Nested
    @DisplayName("1. Cấu trúc cây kế thừa")
    class InheritanceHierarchy {

        @Test
        @DisplayName("Art IS-A Item")
        void art_isA_Item() {
            Art art = new Art("id", "Mona Lisa", "desc", "seller-1", "da Vinci", "Oil", 1503);
            assertInstanceOf(Item.class, art);
        }

        @Test
        @DisplayName("Art IS-A Entity (kế thừa 2 cấp)")
        void art_isA_Entity() {
            Art art = new Art("id", "Mona Lisa", "desc", "seller-1", "da Vinci", "Oil", 1503);
            assertInstanceOf(Entity.class, art);
        }

        @Test
        @DisplayName("Electronics IS-A Item")
        void electronics_isA_Item() {
            Electronics e = new Electronics("id", "iPhone 15", "desc", "seller-1", "Apple", "15 Pro", 12);
            assertInstanceOf(Item.class, e);
        }

        @Test
        @DisplayName("Electronics IS-A Entity (kế thừa 2 cấp)")
        void electronics_isA_Entity() {
            Electronics e = new Electronics("id", "iPhone 15", "desc", "seller-1", "Apple", "15 Pro", 12);
            assertInstanceOf(Entity.class, e);
        }

        @Test
        @DisplayName("Vehicle IS-A Item")
        void vehicle_isA_Item() {
            Vehicle v = new Vehicle("id", "Toyota Camry", "desc", "seller-1", "Toyota", "Camry", 2022, 50000);
            assertInstanceOf(Item.class, v);
        }

        @Test
        @DisplayName("Vehicle IS-A Entity (kế thừa 2 cấp)")
        void vehicle_isA_Entity() {
            Vehicle v = new Vehicle("id", "Toyota Camry", "desc", "seller-1", "Toyota", "Camry", 2022, 50000);
            assertInstanceOf(Entity.class, v);
        }

        @Test
        @DisplayName("Item là abstract – không thể khởi tạo trực tiếp")
        void item_isAbstract() {
            // Kiểm tra tính Abstraction: Item.class.isAbstract() == true
            assertTrue(
                    java.lang.reflect.Modifier.isAbstract(Item.class.getModifiers()),
                    "Item phải là abstract class theo yêu cầu OOP"
            );
        }

        @Test
        @DisplayName("Art, Electronics, Vehicle là các lớp cụ thể (concrete)")
        void subclasses_areConcrete() {
            assertFalse(java.lang.reflect.Modifier.isAbstract(Art.class.getModifiers()),
                    "Art không được là abstract");
            assertFalse(java.lang.reflect.Modifier.isAbstract(Electronics.class.getModifiers()),
                    "Electronics không được là abstract");
            assertFalse(java.lang.reflect.Modifier.isAbstract(Vehicle.class.getModifiers()),
                    "Vehicle không được là abstract");
        }

        @Test
        @DisplayName("Superclass trực tiếp của Art, Electronics, Vehicle phải là Item")
        void directSuperclass_isItem() {
            assertEquals(Item.class, Art.class.getSuperclass());
            assertEquals(Item.class, Electronics.class.getSuperclass());
            assertEquals(Item.class, Vehicle.class.getSuperclass());
        }
    }

    // =====================================================================
    // 2. CONSTRUCTOR & ENCAPSULATION – Art
    // =====================================================================

    @Nested
    @DisplayName("2. Art – Constructor & Encapsulation")
    class ArtTests {

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field bao gồm field kế thừa")
        void art_fullConstructor_setsAllFields() {
            Art art = new Art("art-001", "Starry Night", "Bức tranh nổi tiếng", "seller-1",
                    "Van Gogh", "Oil on canvas", 1889);

            // Field kế thừa từ Entity
            assertEquals("art-001",           art.getId());
            // Field kế thừa từ Item
            assertEquals("Starry Night",       art.getTitle());
            assertEquals("Bức tranh nổi tiếng", art.getDescription());
            assertEquals(ItemCategory.ART,     art.getCategory());
            assertEquals("seller-1",           art.getSellerId());
            // Field riêng của Art
            assertEquals("Van Gogh",           art.getArtist());
            assertEquals("Oil on canvas",      art.getMedium());
            assertEquals(1889,                 art.getYearCreated());
        }

        @Test
        @DisplayName("Art luôn có category = ART, không thể truyền category khác qua constructor")
        void art_categoryAlwaysART() {
            // Constructor Art chỉ nhận sellerId, không nhận category
            // → category được hardcode là ART trong super()
            Art art = new Art("id", "title", "desc", "seller-1", "artist", "medium", 2000);
            assertEquals(ItemCategory.ART, art.getCategory(),
                    "Category của Art phải luôn là ART (hardcode trong super())");
        }

        @Test
        @DisplayName("Constructor rỗng khởi tạo thành công, không ném exception")
        void art_defaultConstructor_noException() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Art::new);
        }

        @Test
        @DisplayName("Setter kế thừa từ Item hoạt động đúng trên đối tượng Art")
        void art_inheritedSetters_work() {
            Art art = new Art();
            art.setId("new-id");
            art.setTitle("New Title");
            art.setDescription("New Desc");
            art.setSellerId("new-seller");
            art.setStartingPrice(5_000_000.0);

            assertEquals("new-id",     art.getId());
            assertEquals("New Title",  art.getTitle());
            assertEquals("New Desc",   art.getDescription());
            assertEquals("new-seller", art.getSellerId());
            assertEquals(5_000_000.0,  art.getStartingPrice(), 0.001);
        }

        @Test
        @DisplayName("Setter riêng của Art hoạt động đúng")
        void art_ownSetters_work() {
            Art art = new Art();
            art.setArtist("Picasso");
            art.setMedium("Watercolor");
            art.setYearCreated(1937);

            assertEquals("Picasso",    art.getArtist());
            assertEquals("Watercolor", art.getMedium());
            assertEquals(1937,         art.getYearCreated());
        }

        @Test
        @DisplayName("Encapsulation: field artist là private, không truy cập trực tiếp")
        void art_artist_isPrivate() throws NoSuchFieldException {
            var field = Art.class.getDeclaredField("artist");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'artist' phải là private (Encapsulation)");
        }

        @Test
        @DisplayName("Encapsulation: field medium là private")
        void art_medium_isPrivate() throws NoSuchFieldException {
            var field = Art.class.getDeclaredField("medium");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
        }
    }

    // =====================================================================
    // 3. CONSTRUCTOR & ENCAPSULATION – Electronics
    // =====================================================================

    @Nested
    @DisplayName("3. Electronics – Constructor & Encapsulation")
    class ElectronicsTests {

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void electronics_fullConstructor_setsAllFields() {
            Electronics e = new Electronics(
                    "elec-001", "MacBook Pro", "Laptop cao cấp", "seller-2",
                    "Apple", "M3 Pro", 24
            );

            assertEquals("elec-001",            e.getId());
            assertEquals("MacBook Pro",          e.getTitle());
            assertEquals("Laptop cao cấp",       e.getDescription());
            assertEquals(ItemCategory.ELECTRONICS, e.getCategory());
            assertEquals("seller-2",             e.getSellerId());
            assertEquals("Apple",                e.getBrand());
            assertEquals("M3 Pro",               e.getModel());
            assertEquals(24,                     e.getWarrantyMonths());
        }

        @Test
        @DisplayName("Electronics luôn có category = ELECTRONICS")
        void electronics_categoryAlwaysELECTRONICS() {
            Electronics e = new Electronics("id", "title", "desc", "s1", "Sony", "X90", 12);
            assertEquals(ItemCategory.ELECTRONICS, e.getCategory());
        }

        @Test
        @DisplayName("Constructor rỗng không ném exception")
        void electronics_defaultConstructor_noException() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Electronics::new);
        }

        @Test
        @DisplayName("Setter riêng của Electronics hoạt động đúng")
        void electronics_ownSetters_work() {
            Electronics e = new Electronics();
            e.setBrand("Samsung");
            e.setModel("Galaxy S24");
            e.setWarrantyMonths(18);

            assertEquals("Samsung",     e.getBrand());
            assertEquals("Galaxy S24",  e.getModel());
            assertEquals(18,            e.getWarrantyMonths());
        }

        @Test
        @DisplayName("Encapsulation: field brand là private")
        void electronics_brand_isPrivate() throws NoSuchFieldException {
            var field = Electronics.class.getDeclaredField("brand");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'brand' phải là private (Encapsulation)");
        }

        @Test
        @DisplayName("warrantyMonths = 0 là hợp lệ (không giới hạn âm trong model)")
        void electronics_zeroWarranty_isValid() {
            Electronics e = new Electronics("id", "t", "d", "s", "Brand", "Model", 0);
            assertEquals(0, e.getWarrantyMonths());
        }
    }

    // =====================================================================
    // 4. CONSTRUCTOR & ENCAPSULATION – Vehicle
    // =====================================================================

    @Nested
    @DisplayName("4. Vehicle – Constructor & Encapsulation")
    class VehicleTests {

        @Test
        @DisplayName("Constructor đầy đủ khởi tạo đúng tất cả field")
        void vehicle_fullConstructor_setsAllFields() {
            Vehicle v = new Vehicle(
                    "veh-001", "Toyota Camry 2022", "Sedan gia đình", "seller-3",
                    "Toyota", "Camry", 2022, 15000
            );

            assertEquals("veh-001",         v.getId());
            assertEquals("Toyota Camry 2022", v.getTitle());
            assertEquals("Sedan gia đình",  v.getDescription());
            assertEquals(ItemCategory.VEHICLE, v.getCategory());
            assertEquals("seller-3",        v.getSellerId());
            assertEquals("Toyota",          v.getMake());
            assertEquals("Camry",           v.getVehicleModel());
            assertEquals(2022,              v.getYear());
            assertEquals(15000,             v.getMileage());
        }

        @Test
        @DisplayName("Vehicle luôn có category = VEHICLE")
        void vehicle_categoryAlwaysVEHICLE() {
            Vehicle v = new Vehicle("id", "title", "desc", "s1", "Honda", "Civic", 2020, 0);
            assertEquals(ItemCategory.VEHICLE, v.getCategory());
        }

        @Test
        @DisplayName("Constructor rỗng không ném exception")
        void vehicle_defaultConstructor_noException() {
            assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Vehicle::new);
        }

        @Test
        @DisplayName("Setter riêng của Vehicle hoạt động đúng")
        void vehicle_ownSetters_work() {
            Vehicle v = new Vehicle();
            v.setMake("BMW");
            v.setVehicleModel("X5");
            v.setYear(2023);
            v.setMileage(5000);

            assertEquals("BMW",  v.getMake());
            assertEquals("X5",   v.getVehicleModel());
            assertEquals(2023,   v.getYear());
            assertEquals(5000,   v.getMileage());
        }

        @Test
        @DisplayName("Encapsulation: field make là private")
        void vehicle_make_isPrivate() throws NoSuchFieldException {
            var field = Vehicle.class.getDeclaredField("make");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'make' phải là private (Encapsulation)");
        }

        @Test
        @DisplayName("Encapsulation: field mileage là private")
        void vehicle_mileage_isPrivate() throws NoSuchFieldException {
            var field = Vehicle.class.getDeclaredField("mileage");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
        }

        @Test
        @DisplayName("mileage = 0 hợp lệ (xe mới 100%)")
        void vehicle_zeroMileage_isValid() {
            Vehicle v = new Vehicle("id", "t", "d", "s", "Tesla", "Model 3", 2024, 0);
            assertEquals(0, v.getMileage());
        }
    }

    // =====================================================================
    // 5. FIELD KẾ THỪA TỪ ITEM – kiểm tra qua cả 3 subclass
    // =====================================================================

    @Nested
    @DisplayName("5. Field kế thừa từ Item – áp dụng chung cho cả 3 subclass")
    class InheritedFieldTests {

        @Test
        @DisplayName("startingPrice kế thừa từ Item hoạt động đúng trên Art")
        void startingPrice_inheritedByArt() {
            Art art = new Art("id", "title", "desc", "s1", "artist", "medium", 2000);
            art.setStartingPrice(50_000_000.0);
            assertEquals(50_000_000.0, art.getStartingPrice(), 0.001);
        }

        @Test
        @DisplayName("startingPrice kế thừa từ Item hoạt động đúng trên Electronics")
        void startingPrice_inheritedByElectronics() {
            Electronics e = new Electronics("id", "title", "desc", "s1", "brand", "model", 12);
            e.setStartingPrice(20_000_000.0);
            assertEquals(20_000_000.0, e.getStartingPrice(), 0.001);
        }

        @Test
        @DisplayName("startingPrice kế thừa từ Item hoạt động đúng trên Vehicle")
        void startingPrice_inheritedByVehicle() {
            Vehicle v = new Vehicle("id", "title", "desc", "s1", "make", "model", 2022, 0);
            v.setStartingPrice(500_000_000.0);
            assertEquals(500_000_000.0, v.getStartingPrice(), 0.001);
        }

        @Test
        @DisplayName("Encapsulation: field title trong Item là private")
        void item_title_isPrivate() throws NoSuchFieldException {
            var field = Item.class.getDeclaredField("title");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                    "field 'title' trong Item phải là private");
        }

        @Test
        @DisplayName("Encapsulation: field startingPrice trong Item là private")
        void item_startingPrice_isPrivate() throws NoSuchFieldException {
            var field = Item.class.getDeclaredField("startingPrice");
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
        }
    }

    // =====================================================================
    // 6. equals() & hashCode() KẾ THỪA TỪ Entity
    // =====================================================================

    @Nested
    @DisplayName("6. equals() và hashCode() kế thừa từ Entity")
    class EqualityTests {

        @Test
        @DisplayName("Hai Art cùng id thì equals() = true (kế thừa từ Entity)")
        void sameId_equalsTrue() {
            Art a1 = new Art("art-001", "Title 1", "d1", "s1", "artist", "medium", 2000);
            Art a2 = new Art("art-001", "Title 2", "d2", "s2", "artist2", "medium2", 2001);
            assertEquals(a1, a2,
                    "Entity.equals() so sánh theo id, không phải toàn bộ field");
        }

        @Test
        @DisplayName("Hai Electronics khác id thì equals() = false")
        void differentId_equalsFalse() {
            Electronics e1 = new Electronics("id-1", "t", "d", "s", "b", "m", 12);
            Electronics e2 = new Electronics("id-2", "t", "d", "s", "b", "m", 12);
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("Hai đối tượng cùng id thì hashCode() bằng nhau")
        void sameId_sameHashCode() {
            Vehicle v1 = new Vehicle("veh-001", "t1", "d", "s", "make", "model", 2022, 0);
            Vehicle v2 = new Vehicle("veh-001", "t2", "d", "s", "make", "model", 2023, 1000);
            assertEquals(v1.hashCode(), v2.hashCode());
        }

        @Test
        @DisplayName("Art và Electronics cùng id không equals nhau (khác class)")
        void differentClass_sameId_notEqual() {
            Art art = new Art("shared-id", "t", "d", "s", "a", "m", 2000);
            Electronics e = new Electronics("shared-id", "t", "d", "s", "b", "m", 12);
            // Entity.equals dùng getClass() != o.getClass() → false nếu khác class
            assertNotEquals(art, e,
                    "Art và Electronics khác class nên không equals dù cùng id");
        }
    }
}