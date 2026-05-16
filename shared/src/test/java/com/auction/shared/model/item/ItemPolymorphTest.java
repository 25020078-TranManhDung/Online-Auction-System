package com.auction.shared.model.item;

import com.auction.shared.enums.ItemCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 *   "Polymorphism: override phương thức
 *
 * Các nhóm kiểm tra:
 *   1. Override printInfo() – đúng nội dung, đúng lớp gọi
 *   2. Tham chiếu kiểu Item trỏ đến subclass (runtime polymorphism)
 *   3. Duyệt danh sách Item hỗn hợp – gọi đúng phiên bản của từng lớp
 *   4. Kiểm tra override bằng reflection – đảm bảo cả 3 subclass đều @Override
 *   5. Downcasting an toàn sau khi upcasting
 */
@DisplayName("Item – Tính đa hình (Polymorphism)")
class ItemPolymorphTest {

    private Art art;
    private Electronics electronics;
    private Vehicle vehicle;

    // Dùng để bắt System.out.println() từ printInfo()
    private ByteArrayOutputStream outCapture;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        art = new Art(
                "art-001", "Starry Night", "Kiệt tác của Van Gogh", "seller-1",
                "Van Gogh", "Oil on canvas", 1889
        );
        electronics = new Electronics(
                "elec-001", "iPhone 15 Pro", "Flagship Apple", "seller-2",
                "Apple", "15 Pro Max", 12
        );
        vehicle = new Vehicle(
                "veh-001", "Toyota Camry", "Sedan cao cấp", "seller-3",
                "Toyota", "Camry", 2022, 15000
        );

        // Redirect stdout để kiểm tra output của printInfo()
        outCapture  = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outCapture));
    }

    void tearDown() {
        System.setOut(originalOut);
    }

    // =====================================================================
    // 1. OVERRIDE printInfo() – đúng nội dung
    // =====================================================================

    @Nested
    @DisplayName("1. printInfo() – Nội dung output của từng lớp")
    class PrintInfoContentTests {

        @Test
        @DisplayName("Art.printInfo() in đúng artist, medium, yearCreated")
        void art_printInfo_containsArtistMediumYear() {
            art.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("Van Gogh"),      "Phải chứa artist: Van Gogh");
            assertTrue(output.contains("Oil on canvas"),  "Phải chứa medium: Oil on canvas");
            assertTrue(output.contains("1889"),           "Phải chứa yearCreated: 1889");
        }

        @Test
        @DisplayName("Electronics.printInfo() in đúng brand, model, warrantyMonths")
        void electronics_printInfo_containsBrandModelWarranty() {
            electronics.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("Apple"),    "Phải chứa brand: Apple");
            assertTrue(output.contains("15 Pro Max"), "Phải chứa model: 15 Pro Max");
            assertTrue(output.contains("12"),        "Phải chứa warrantyMonths: 12");
        }

        @Test
        @DisplayName("Vehicle.printInfo() in đúng make, vehicleModel, year, mileage")
        void vehicle_printInfo_containsMakeModelYearMileage() {
            vehicle.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("Toyota"),  "Phải chứa make: Toyota");
            assertTrue(output.contains("Camry"),   "Phải chứa vehicleModel: Camry");
            assertTrue(output.contains("2022"),    "Phải chứa year: 2022");
            assertTrue(output.contains("15000"),   "Phải chứa mileage: 15000");
        }

        @Test
        @DisplayName("Ba lớp in nội dung KHÁC NHAU – mỗi lớp có output riêng")
        void threeClasses_printDifferentOutput() {
            art.printInfo();
            String artOut = outCapture.toString();
            outCapture.reset();

            electronics.printInfo();
            String elecOut = outCapture.toString();
            outCapture.reset();

            vehicle.printInfo();
            String vehOut = outCapture.toString();
            tearDown();

            assertNotEquals(artOut,  elecOut, "Art và Electronics phải in nội dung khác nhau");
            assertNotEquals(elecOut, vehOut,  "Electronics và Vehicle phải in nội dung khác nhau");
            assertNotEquals(artOut,  vehOut,  "Art và Vehicle phải in nội dung khác nhau");
        }
    }

    // =====================================================================
    // 2. RUNTIME POLYMORPHISM – tham chiếu kiểu Item trỏ đến subclass
    // =====================================================================

    @Nested
    @DisplayName("2. Tham chiếu Item trỏ đến subclass – runtime dispatch")
    class RuntimePolymorphismTests {

        @Test
        @DisplayName("Item ref = new Art() → printInfo() gọi phiên bản của Art")
        void itemRef_art_callsArtPrintInfo() {
            Item item = new Art("id", "t", "d", "s", "Monet", "Pastel", 1900);
            item.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("Monet"),  "Phải gọi Art.printInfo(), chứa artist: Monet");
            assertTrue(output.contains("Pastel"), "Phải gọi Art.printInfo(), chứa medium: Pastel");
            assertTrue(output.contains("1900"),   "Phải gọi Art.printInfo(), chứa year: 1900");
        }

        @Test
        @DisplayName("Item ref = new Electronics() → printInfo() gọi phiên bản của Electronics")
        void itemRef_electronics_callsElectronicsPrintInfo() {
            Item item = new Electronics("id", "t", "d", "s", "Sony", "WH-1000XM5", 24);
            item.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("Sony"),        "Phải gọi Electronics.printInfo()");
            assertTrue(output.contains("WH-1000XM5"), "Phải chứa model");
        }

        @Test
        @DisplayName("Item ref = new Vehicle() → printInfo() gọi phiên bản của Vehicle")
        void itemRef_vehicle_callsVehiclePrintInfo() {
            Item item = new Vehicle("id", "t", "d", "s", "BMW", "X5", 2023, 5000);
            item.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("BMW"),  "Phải gọi Vehicle.printInfo()");
            assertTrue(output.contains("X5"),   "Phải chứa vehicleModel");
            assertTrue(output.contains("5000"), "Phải chứa mileage");
        }

        @Test
        @DisplayName("Item ref sau khi gán lại sang lớp khác → gọi đúng phiên bản mới")
        void itemRef_reassigned_callsNewClass() {
            Item item = new Art("id1", "t", "d", "s", "artist", "medium", 2000);
            item.printInfo();
            assertTrue(outCapture.toString().contains("artist"));
            outCapture.reset();

            // Gán lại sang Electronics
            item = new Electronics("id2", "t", "d", "s", "LG", "OLED C3", 36);
            item.printInfo();
            String output = outCapture.toString();
            tearDown();

            assertTrue(output.contains("LG"),
                    "Sau khi gán lại, phải gọi Electronics.printInfo()");
        }
    }

    // =====================================================================
    // 3. DANH SÁCH Item HỖN HỢP – duyệt và gọi printInfo()
    // =====================================================================

    @Nested
    @DisplayName("3. Danh sách List<Item> hỗn hợp – dispatcher đúng từng lớp")
    class MixedListPolymorphismTests {

        @Test
        @DisplayName("Duyệt List<Item> hỗn hợp, mỗi phần tử gọi đúng printInfo() của lớp mình")
        void mixedList_eachCallsOwnPrintInfo() {
            List<Item> items = new ArrayList<>();
            items.add(new Art("a1",  "Mona Lisa",  "d", "s", "da Vinci", "Oil", 1503));
            items.add(new Electronics("e1", "iPad Pro", "d", "s", "Apple", "M4", 12));
            items.add(new Vehicle("v1", "Tesla Model S", "d", "s", "Tesla", "Model S", 2023, 0));

            for (Item item : items) {
                item.printInfo();
            }
            String allOutput = outCapture.toString();
            tearDown();

            // Art output
            assertTrue(allOutput.contains("da Vinci"), "Art phải in artist");
            assertTrue(allOutput.contains("Oil"),      "Art phải in medium");
            assertTrue(allOutput.contains("1503"),     "Art phải in year");
            // Electronics output
            assertTrue(allOutput.contains("Apple"),    "Electronics phải in brand");
            assertTrue(allOutput.contains("M4"),       "Electronics phải in model");
            // Vehicle output
            assertTrue(allOutput.contains("Tesla"),    "Vehicle phải in make");
            assertTrue(allOutput.contains("Model S"),  "Vehicle phải in vehicleModel");
            assertTrue(allOutput.contains("2023"),     "Vehicle phải in year");
        }

        @Test
        @DisplayName("Đếm đúng số lần từng lớp được gọi qua danh sách hỗn hợp")
        void mixedList_correctCallCount() {
            List<Item> items = List.of(
                    new Art("a1", "t", "d", "s", "artist1", "m", 2000),
                    new Art("a2", "t", "d", "s", "artist2", "m", 2001),
                    new Electronics("e1", "t", "d", "s", "brand1", "model", 12),
                    new Vehicle("v1", "t", "d", "s", "make1", "vmodel", 2022, 0)
            );

            for (Item item : items) item.printInfo();
            String output = outCapture.toString();
            tearDown();

            // 2 Art → "artist1" và "artist2" đều phải xuất hiện
            assertTrue(output.contains("artist1"), "Art đầu tiên phải được gọi");
            assertTrue(output.contains("artist2"), "Art thứ hai phải được gọi");
            // 1 Electronics → "brand1"
            assertTrue(output.contains("brand1"),  "Electronics phải được gọi");
            // 1 Vehicle → "make1"
            assertTrue(output.contains("make1"),   "Vehicle phải được gọi");
        }

        @Test
        @DisplayName("Lọc theo category từ List<Item> hỗn hợp – getCategory() đúng kiểu")
        void mixedList_filterByCategory() {
            List<Item> items = List.of(
                    new Art("a1", "t", "d", "s", "a", "m", 2000),
                    new Electronics("e1", "t", "d", "s", "b", "m", 12),
                    new Electronics("e2", "t", "d", "s", "b2", "m2", 24),
                    new Vehicle("v1", "t", "d", "s", "mk", "mv", 2022, 0)
            );

            long artCount   = items.stream()
                    .filter(i -> i.getCategory() == ItemCategory.ART).count();
            long elecCount  = items.stream()
                    .filter(i -> i.getCategory() == ItemCategory.ELECTRONICS).count();
            long vehCount   = items.stream()
                    .filter(i -> i.getCategory() == ItemCategory.VEHICLE).count();

            tearDown();
            assertEquals(1, artCount,  "Có 1 Art trong danh sách");
            assertEquals(2, elecCount, "Có 2 Electronics trong danh sách");
            assertEquals(1, vehCount,  "Có 1 Vehicle trong danh sách");
        }
    }

    // =====================================================================
    // 4. REFLECTION – xác nhận @Override bằng method lookup
    // =====================================================================

    @Nested
    @DisplayName("4. Reflection – xác nhận các subclass đều override printInfo()")
    class ReflectionOverrideTests {

        @Test
        @DisplayName("Art.class khai báo method printInfo() riêng (đã override)")
        void art_declaresPrintInfo() {
            assertDoesNotThrow(
                    () -> Art.class.getDeclaredMethod("printInfo"),
                    "Art phải tự khai báo printInfo() (override) chứ không dùng của Item"
            );
        }

        @Test
        @DisplayName("Electronics.class khai báo method printInfo() riêng (đã override)")
        void electronics_declaresPrintInfo() {
            assertDoesNotThrow(
                    () -> Electronics.class.getDeclaredMethod("printInfo"),
                    "Electronics phải tự khai báo printInfo() (override)"
            );
        }

        @Test
        @DisplayName("Vehicle.class khai báo method printInfo() riêng (đã override)")
        void vehicle_declaresPrintInfo() {
            assertDoesNotThrow(
                    () -> Vehicle.class.getDeclaredMethod("printInfo"),
                    "Vehicle phải tự khai báo printInfo() (override)"
            );
        }

        @Test
        @DisplayName("printInfo() trong Item là abstract (buộc subclass phải override)")
        void item_printInfo_isAbstract() throws NoSuchMethodException {
            var method = Item.class.getDeclaredMethod("printInfo");
            assertTrue(
                    java.lang.reflect.Modifier.isAbstract(method.getModifiers()),
                    "Item.printInfo() phải là abstract để bắt buộc các subclass override"
            );
        }
    }

    // =====================================================================
    // 5. DOWNCAST AN TOÀN sau khi upcast lên Item
    // =====================================================================

    @Nested
    @DisplayName("5. Downcast an toàn – instanceof + cast")
    class DowncastTests {

        @Test
        @DisplayName("Item ref trỏ Art → instanceof Art = true, downcast thành công")
        void downcast_artFromItemRef() {
            Item item = new Art("id", "t", "d", "s", "Rembrandt", "Ink", 1650);

            assertTrue(item instanceof Art,
                    "instanceof Art phải true với tham chiếu Item trỏ Art");
            assertFalse(item instanceof Electronics,
                    "instanceof Electronics phải false");
            assertFalse(item instanceof Vehicle,
                    "instanceof Vehicle phải false");

            Art art = (Art) item;
            assertEquals("Rembrandt", art.getArtist(),
                    "Sau downcast phải truy cập field riêng của Art");
        }

        @Test
        @DisplayName("Item ref trỏ Electronics → instanceof Electronics = true, downcast thành công")
        void downcast_electronicsFromItemRef() {
            Item item = new Electronics("id", "t", "d", "s", "Panasonic", "LUMIX", 6);

            assertTrue(item instanceof Electronics);
            assertFalse(item instanceof Art);
            assertFalse(item instanceof Vehicle);

            Electronics e = (Electronics) item;
            assertEquals("Panasonic", e.getBrand());
            assertEquals("LUMIX",     e.getModel());
        }

        @Test
        @DisplayName("Item ref trỏ Vehicle → instanceof Vehicle = true, downcast thành công")
        void downcast_vehicleFromItemRef() {
            Item item = new Vehicle("id", "t", "d", "s", "Mercedes", "GLC", 2024, 100);

            assertTrue(item instanceof Vehicle);
            assertFalse(item instanceof Art);
            assertFalse(item instanceof Electronics);

            Vehicle v = (Vehicle) item;
            assertEquals("Mercedes", v.getMake());
            assertEquals(100,        v.getMileage());
        }

        @Test
        @DisplayName("Downcast sai kiểu ném ClassCastException")
        void downcast_wrongType_throwsClassCastException() {
            Item item = new Art("id", "t", "d", "s", "artist", "medium", 2000);

            assertThrows(ClassCastException.class, () -> {
                Electronics e = (Electronics) item; // cố ý sai
            }, "Downcast Art → Electronics phải ném ClassCastException");
        }
    }
}