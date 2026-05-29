# 🏷️ Hệ thống Đấu giá Trực tuyến — Online Auction System

> Bài tập lớn — Lập trình nâng cao  
> Trường Đại học Công nghệ (UET — VNU)

---

## Mục lục

- [Mô tả hệ thống](#mô-tả-hệ-thống)
- [Công nghệ & Yêu cầu cài đặt](#công-nghệ--yêu-cầu-cài-đặt)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Hướng dẫn build & chạy chương trình](#hướng-dẫn-build--chạy-chương-trình)
- [Chạy nhiều Client (Multi-client)](#chạy-nhiều-client-multi-client)
- [Tài khoản test](#tài-khoản-test)
- [Danh sách chức năng đã hoàn thành](#danh-sách-chức-năng-đã-hoàn-thành)
- [Báo cáo & Video demo](#báo-cáo--video-demo)

---

## Mô tả hệ thống

Hệ thống đấu giá trực tuyến cho phép nhiều người dùng đồng thời tham gia cạnh tranh giá để mua sản phẩm trong một khoảng thời gian xác định trước. Người bán đăng sản phẩm lên hệ thống; người mua (bidder) đặt giá cạnh tranh; hệ thống tự động xác định người thắng khi phiên kết thúc.

Hệ thống hỗ trợ ba vai trò: **Bidder** (người đấu giá), **Seller** (người bán), **Admin** (quản trị viên).

Giao tiếp Client–Server qua **TCP Socket** với dữ liệu định dạng **JSON**, theo protocol được định nghĩa trong [`PROTOCOL.md`](./PROTOCOL.md).

---

## Công nghệ & Yêu cầu cài đặt

### Công cụ bắt buộc

| Công cụ | Phiên bản tối thiểu | Kiểm tra |
|---|---|---|
| Java JDK | 19+ | `java -version` |
| Apache Maven | 3.8+ | `mvn -version` |

> **Lưu ý về Database:** Hệ thống sử dụng **MySQL cloud (Aiven)** — **không cần cài database local**. Kết nối đã được cấu hình sẵn trong `server/src/main/resources/application.properties`.

### Thư viện chính (Maven tự tải)

| Thư viện | Vai trò |
|---|---|
| JavaFX 19 + FXML | Giao diện đồ họa phía client |
| MySQL Connector + HikariCP | Kết nối và quản lý connection pool |
| jBCrypt | Mã hóa mật khẩu |
| Gson | Xử lý JSON (serialize/deserialize) |
| JJWT 0.12.3 | Xác thực token |
| JUnit 5 + Mockito | Unit test |
| SLF4J | Logging |

---

## Cấu trúc thư mục

```
Online-Auction-System/
├── shared/                          ← Module dùng chung (model, DTO, enum, exception, util)
│   └── src/main/java/com/auction/shared/
│       ├── model/                   ← Entity, User (Bidder/Seller/Admin), Item (Art/Electronics/Vehicle), Auction, BidTransaction
│       ├── dto/                     ← Request & Response DTO
│       ├── enums/                   ← AuctionStatus, ItemCategory, UserRole
│       ├── exception/               ← Các custom exception
│       ├── network/protocol/        ← Message, ServerResponse, Actions, PushMessage
│       └── util/                    ← JsonUtil
│
├── server/                          ← Backend (Socket server, Service, DAO, Design Pattern)
│   └── src/main/java/com/auction/server/
│       ├── ServerMain.java          ← Entry point server
│       ├── config/                  ← AppConfig
│       ├── controller/              ← AuctionController, BidController, ItemController, UserController, WalletController
│       ├── service/                 ← AuctionService, BidService, AutoBidService, UserService, WalletService, AuctionTimerService
│       ├── dao/                     ← Interface DAO + impl/ (AuctionDAO, BidTransactionDAO, UserDAO, WalletDAO, ...)
│       ├── network/                 ← SocketServer, ClientHandler, MessageRouter
│       ├── observer/                ← AuctionEventBus, AuctionObserver, BidNotifier
│       ├── pattern/
│       │   ├── singleton/           ← DatabaseManager, AuctionManager
│       │   ├── factory/             ← ItemFactory
│       │   └── strategy/            ← BidStrategy, NormalBidStrategy, AutoBidStrategy
│       └── util/                    ← PasswordUtil, TokenUtil
│
├── client/                          ← Frontend (JavaFX GUI)
│   └── src/main/java/com/auction/client/
│       ├── Launcher.java            ← Entry point client
│       ├── MainApp.java             ← JavaFX Application
│       ├── controller/              ← LoginController, RegisterController, AuctionListController,
│       │                               AuctionDetailController, BiddingController,
│       │                               SellerDashboardController, AdminDashboardController,
│       │                               BidderWalletController, SellerWalletController, AdminWalletController,
│       │                               ChangePasswordController
│       ├── model/                   ← AuctionViewModel, UserSession
│       ├── network/                 ← SocketClient, MessageHandler
│       ├── observer/                ← AuctionUpdateListener, BidUpdateListener
│       └── util/                    ← AlertUtil, ChartUtil, ImageUtil, ViewLoader, ThemeManager, ...
│
├── pom.xml                          ← Parent POM (build theo thứ tự: shared → server → client)
├── PROTOCOL.md                      ← Đặc tả giao thức Socket JSON
└── README.md
```

---

## Hướng dẫn build & chạy chương trình

### 1. Clone repository

```bash
git clone https://github.com/<tên-nhóm>/Online-Auction-System.git
cd Online-Auction-System
```

### 2. Build toàn bộ project

Chạy lệnh sau tại **thư mục gốc** (nơi chứa `pom.xml` cha):

```bash
mvn install -DskipTests
```

> Lệnh này build theo thứ tự `shared` → `server` → `client`. Kết thúc bằng `BUILD SUCCESS` là thành công.

### 3. Chạy Server (Terminal 1)

```bash
java -jar server/target/server-1.0-SNAPSHOT.jar
```

Chờ đến khi thấy log thông báo server khởi động trên cổng **8080** thì chuyển sang bước tiếp theo.

### 4. Chạy Client (Terminal 2 — mở terminal mới)

```bash
mvn javafx:run -pl client
```

Cửa sổ giao diện đăng nhập sẽ hiện lên.

---

## Chạy nhiều Client (Multi-client)

Mỗi terminal là một client độc lập. Để chạy thêm client, **mở terminal mới** và chạy lại lệnh:

```bash
mvn javafx:run -pl client
```

**Thứ tự bắt buộc:**

```
Terminal 1:  java -jar server/target/server-1.0-SNAPSHOT.jar   ← chạy trước
Terminal 2:  mvn javafx:run -pl client                         ← chạy sau
Terminal 3:  mvn javafx:run -pl client                         ← (tuỳ chọn) client thứ 2
```

> **Linux / macOS:** Các lệnh trên hoàn toàn tương thích. Đảm bảo `java` và `mvn` đã có trong `PATH`.  
> **Windows:** Sử dụng Command Prompt hoặc PowerShell với cú pháp lệnh giống hệt trên.

---

## Tài khoản test

| Vai trò | Username | Password |
|---|---|---|
| Admin | `admin` | `123456` |
| Seller | `seller01` | `123456` |
| Bidder | `bidder01` | `123456` |

---

## Danh sách chức năng đã hoàn thành

### ✅ Chức năng bắt buộc

| # | Chức năng | Trạng thái |
|---|---|---|
| 1 | Đăng ký tài khoản (Bidder / Seller) | ✅ |
| 2 | Đăng nhập / Đăng xuất | ✅ |
| 3 | Phân quyền theo vai trò: Bidder, Seller, Admin | ✅ |
| 4 | Thêm / sửa / xóa sản phẩm đấu giá (Seller) | ✅ |
| 5 | Tạo / sửa / hủy phiên đấu giá (Seller) | ✅ |
| 6 | Xem danh sách phiên đấu giá | ✅ |
| 7 | Xem chi tiết sản phẩm & phiên đấu giá | ✅ |
| 8 | Đặt giá (bid) theo thời gian thực | ✅ |
| 9 | Kiểm tra tính hợp lệ của giá đấu | ✅ |
| 10 | Cập nhật người dẫn đầu phiên đấu giá (realtime) | ✅ |
| 11 | Tự động đóng phiên khi hết thời gian | ✅ |
| 12 | Xác định người thắng cuộc | ✅ |
| 13 | Chuyển trạng thái phiên: `OPEN → RUNNING → FINISHED → PAID / CANCELED` | ✅ |
| 14 | Xử lý lỗi: đặt giá thấp hơn giá hiện tại | ✅ |
| 15 | Xử lý lỗi: đấu giá khi phiên đã đóng | ✅ |
| 16 | Xử lý lỗi kết nối, lỗi dữ liệu | ✅ |
| 17 | Giao diện JavaFX đầy đủ: Login, Register, Danh sách, Chi tiết, Bidding, Dashboard | ✅ |
| 18 | Quản lý ví (nạp tiền, rút tiền) cho Bidder / Seller / Admin | ✅ |
| 19 | Đổi mật khẩu | ✅ |
| 20 | Quản lý người dùng (Admin) | ✅ |

### ✅ Thiết kế hướng đối tượng (OOP)

| # | Nội dung | Trạng thái |
|---|---|---|
| 1 | Cây kế thừa: `Entity → Item → Art / Electronics / Vehicle`, `Entity → User → Bidder / Seller / Admin` | ✅ |
| 2 | Encapsulation (private + getter/setter), Inheritance, Polymorphism, Abstraction | ✅ |
| 3 | **Singleton**: `DatabaseManager`, `AuctionManager` | ✅ |
| 4 | **Factory Method**: `ItemFactory` (tạo các loại Item theo category) | ✅ |
| 5 | **Observer**: `AuctionEventBus`, `BidNotifier` — push realtime đến tất cả client | ✅ |
| 6 | **Strategy**: `BidStrategy`, `NormalBidStrategy`, `AutoBidStrategy` | ✅ |

### ✅ Kiến trúc & Chất lượng mã

| # | Nội dung | Trạng thái |
|---|---|---|
| 1 | Kiến trúc Client–Server rõ ràng, phân tầng | ✅ |
| 2 | MVC phía client: JavaFX + FXML + Controller | ✅ |
| 3 | MVC phía server: Controller → Service → DAO → Database | ✅ |
| 4 | Build tool: Maven (multi-module: shared / server / client) | ✅ |
| 5 | Unit Test: JUnit 5 + Mockito (shared, server — service & pattern) | ✅ |
| 6 | CI/CD: GitHub Actions (`ci.yml`) tự động build & test khi push lên `main` | ✅ |

### ✅ Chức năng nâng cao

| # | Chức năng | Trạng thái |
|---|---|---|
| 1 | **Auto-Bidding**: đặt `maxBid` + `increment`, hệ thống tự trả giá khi có bid mới từ đối thủ | ✅ |
| 2 | **Concurrent Bidding**: xử lý nhiều bidder đặt giá đồng thời, tránh lost update / race condition | ✅ |
| 3 | **Realtime Update**: push bid mới đến tất cả client đang xem phiên qua Socket/Observer, không polling | ✅ |
| 4 | **Bid History Visualization**: biểu đồ đường (line chart) giá đấu cập nhật theo thời gian thực | ✅ |


---

## Báo cáo & Video demo

| Tài liệu | Link |
|---|---|
| 📄 Báo cáo PDF | https://drive.google.com/file/d/15-q-SSMFFYFAJX9xEqeM6XZf7m_YekuR/view?usp=sharing |
| 🎥 Video demo |https://drive.google.com/file/d/1Qm9buGOyD_WLCTuJAQeTXyii9o0n7ioC/view?usp=sharing |

