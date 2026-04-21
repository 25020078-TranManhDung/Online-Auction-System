# Hệ thống đấu giá trực tuyến

> Bài tập lớn — Lập trình nâng cao  
> Trường Đại học Công nghệ  
> Nhóm 4 người

---

## Mục lục

- [Mô tả dự án](#mô-tả-dự-án)
- [Kiến trúc hệ thống](#kiến-trúc-hệ-thống)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Phân công công việc](#phân-công-công-việc)

---

## Mô tả dự án

Hệ thống đấu giá trực tuyến cho phép nhiều người dùng cùng tham gia cạnh tranh giá để mua sản phẩm trong khoảng thời gian xác định. Hệ thống hỗ trợ 3 vai trò: **Bidder** (người đấu giá), **Seller** (người bán), **Admin** (quản trị viên).

---

## Kiến trúc hệ thống

```
auction-system/
├── shared/       ← Dùng chung: Model, DTO, Enum, Exception, Util
├── server/       ← Backend: Socket, Service, DAO, Pattern
└── client/       ← Frontend: JavaFX, FXML, Controller
```

Giao tiếp Client–Server qua **TCP Socket** với dữ liệu định dạng **JSON**, theo protocol được định nghĩa trong `PROTOCOL.md`.

---

## Công nghệ sử dụng


Java 19, Maven

JavaFX 19 + FXML (phía client)

MySQL + HikariCP (quản lý kết nối cơ sở dữ liệu hiệu suất cao)

jBCrypt (mã hóa mật khẩu), Gson & Protocol Buffers (xử lý JSON và tuần tự hóa dữ liệu)

JUnit 5 (unit test), SLF4J (logging)

---

## Phân công công việc

### Tổng quan theo module

| Module | Thành viên A | Thành viên B | Thành viên C | Thành viên D |
|--------|:---:|:---:|:---:|:---:|
| **Shared** | Entity, Item, User, Enum, DTO | Auction, BidTransaction, Exception | JsonUtil, AutoBidSetting | — |
| **Server** | Config, DAO, Singleton, Factory | Service, Observer, Strategy | Network, Controller | — |
| **Client** | — | — | SocketClient, MessageHandler | Toàn bộ JavaFX UI |

---
