# PROTOCOL.md — WebSocket JSON Protocol

---

## 1. Cấu trúc gói tin

Mọi message đều dùng một trong ba format sau.

### Client → Server (Request)

```json
{
  "action"   : "TÊN_ACTION",       // bắt buộc, luôn UPPER_SNAKE_CASE
  "token"    : "abc123xyz",        // bắt buộc với mọi action sau login
  "requestId": "req-uuid-001",     // client tự gen UUID, dùng để match response
  "data"     : { /* payload tuỳ action */ }
}
```

### Server → Client (Response)

```json
{
  "requestId": "req-uuid-001",     // echo lại requestId của client
  "success"  : true,               // hoặc false
  "data"     : { /* payload response */ },
  "error"    : null                // null nếu success, có message nếu lỗi
}
```

### Server → Client (Push — không có requestId)

```json
{
  "type" : "PUSH",
  "event": "BID_PLACED",           // tên sự kiện
  "data" : { /* payload sự kiện */ }
}
```

> **Phân biệt Response và Push:** Client dùng trường `requestId` hoặc `type` để phân loại — nếu có `"type":"PUSH"` thì dispatch cho Observer listeners, nếu có `requestId` thì match với callback đang chờ.

---

## 2. Lỗi — ErrorResponse

```json
{
  "requestId": "req-uuid-001",
  "success"  : false,
  "data"     : null,
  "error"    : {
    "code"   : "INVALID_BID",                         // mã lỗi cố định để client switch-case
    "message": "Số tiền bid phải cao hơn giá hiện tại"
  }
}
```

### Danh sách error codes chuẩn

| Code                 | Ý nghĩa                              |
|----------------------|--------------------------------------|
| `AUTH_FAILED`        | Sai username / password              |
| `TOKEN_EXPIRED`      | Token hết hạn                        |
| `TOKEN_INVALID`      | Token không hợp lệ                   |
| `AUCTION_NOT_FOUND`  | Auction không tồn tại                |
| `AUCTION_CLOSED`     | Auction đã đóng                      |
| `INVALID_BID`        | Bid không hợp lệ                     |
| `INSUFFICIENT_BID`   | Bid thấp hơn mức tối thiểu           |
| `USER_NOT_FOUND`     | Không tìm thấy user                  |
| `PERMISSION_DENIED`  | Không có quyền                       |
| `INTERNAL_ERROR`     | Lỗi server không xác định            |

---

## 3. Auth Actions

### `LOGIN` _(không cần token)_

**Request**
```json
{
  "action"   : "LOGIN",
  "token"    : null,
  "requestId": "req-001",
  "data": {
    "username": "alice",
    "password": "Secret123!"
  }
}
```

**Response**
```json
{
  "requestId": "req-001",
  "success"  : true,
  "data": {
    "token"    : "eyJhbGci...",
    "userId"   : "uuid-alice",
    "username" : "alice",
    "role"     : "BIDDER",
    "expiresAt": "2026-04-09T10:00:00"
  },
  "error": null
}
```

---

### `REGISTER` _(không cần token)_

**Request**
```json
{
  "action"   : "REGISTER",
  "token"    : null,
  "requestId": "req-002",
  "data": {
    "username": "bob",
    "password": "Secure456!",
    "email"   : "bob@example.com",
    "role"    : "BIDDER"
  }
}
```

**Response**
```json
{
  "requestId": "req-002",
  "success"  : true,
  "data": {
    "userId"  : "uuid-bob",
    "username": "bob",
    "message" : "Đăng ký thành công"
  },
  "error": null
}
```

---

### `LOGOUT`

**Request**
```json
{
  "action"   : "LOGOUT",
  "token"    : "eyJhbGci...",
  "requestId": "req-003",
  "data"     : {}
}
```

**Response**
```json
{
  "requestId": "req-003",
  "success"  : true,
  "data": {
    "message": "Đã đăng xuất"
  },
  "error": null
}
```

---

## 4. Auction Actions

### `GET_AUCTIONS` — Lấy danh sách

**Request**
```json
{
  "action"   : "GET_AUCTIONS",
  "token"    : "eyJhbGci...",
  "requestId": "req-010",
  "data": {
    "status": "ACTIVE",  // optional
    "page"  : 0,
    "size"  : 20
  }
}
```

**Response**
```json
{
  "requestId": "req-010",
  "success"  : true,
  "data": {
    "auctions": [
      {
        "id"          : "uuid-a1",
        "title"       : "iPhone 15 Pro",
        "currentPrice": 15000000,
        "endTime"     : "2026-04-10T18:00:00",
        "status"      : "ACTIVE",
        "bidCount"    : 12
      }
    ],
    "total": 45
  },
  "error": null
}
```

---

### `GET_AUCTION_DETAIL`

**Request**
```json
{
  "action"   : "GET_AUCTION_DETAIL",
  "token"    : "eyJhbGci...",
  "requestId": "req-011",
  "data": {
    "auctionId": "uuid-a1"
  }
}
```

**Response**
```json
{
  "requestId": "req-011",
  "success"  : true,
  "data": {
    "auction"      : { /* AuctionResponse đầy đủ */ },
    "item"         : { /* Item info + extra fields */ },
    "recentBids"   : [ /* 10 bid gần nhất */ ],
    "timeRemaining": 3600,       // giây
    "currentLeader": "alice"
  },
  "error": null
}
```

---

### `CREATE_AUCTION` _(chỉ SELLER)_

**Request**
```json
{
  "action"   : "CREATE_AUCTION",
  "token"    : "eyJhbGci...",
  "requestId": "req-012",
  "data": {
    "itemId"         : "uuid-item-1",
    "startPrice"     : 5000000,
    "minBidIncrement": 100000,
    "durationMinutes": 1440      // 24 giờ
  }
}
```

---

### `START_AUCTION` _(chỉ SELLER)_

**Request**
```json
{
  "action"   : "START_AUCTION",
  "token"    : "eyJhbGci...",
  "requestId": "req-013",
  "data": {
    "auctionId": "uuid-a1"
  }
}
```

---

### `CLOSE_AUCTION` _(chỉ SELLER)_

**Request**
```json
{
  "action"   : "CLOSE_AUCTION",
  "token"    : "eyJhbGci...",
  "requestId": "req-014",
  "data": {
    "auctionId": "uuid-a1"
  }
}
```

---

## 5. Bid Actions

### `PLACE_BID`

**Request**
```json
{
  "action"   : "PLACE_BID",
  "token"    : "eyJhbGci...",
  "requestId": "req-020",
  "data": {
    "auctionId": "uuid-a1",
    "amount"   : 3200000
  }
}
```

**Response (thành công)**
```json
{
  "requestId": "req-020",
  "success"  : true,
  "data": {
    "newCurrentPrice": 3200000,
    "rank"           : 1,
    "isAutoBid"      : false,
    "message"        : "Bạn đang dẫn đầu!"
  },
  "error": null
}
```

> **Lưu ý:** Response thất bại phải trả về error code rõ ràng để client hiển thị đúng thông báo. Ví dụ: `INVALID_BID` khi bid thấp hơn min, `AUCTION_CLOSED` khi auction đã đóng.

---

### `SET_AUTO_BID`

**Request**
```json
{
  "action"   : "SET_AUTO_BID",
  "token"    : "eyJhbGci...",
  "requestId": "req-021",
  "data": {
    "auctionId": "uuid-a1",
    "maxBid"   : 5000000,
    "increment": 100000
  }
}
```

**Response**
```json
{
  "requestId": "req-021",
  "success"  : true,
  "data": {
    "maxBid"        : 5000000,
    "currentPrice"  : 3200000,
    "alreadyWinning": false,
    "message"       : "Auto-bid đã đăng ký"
  },
  "error": null
}
```

---

### `CANCEL_AUTO_BID`

**Request**
```json
{
  "action"   : "CANCEL_AUTO_BID",
  "token"    : "eyJhbGci...",
  "requestId": "req-023",
  "data": {
    "auctionId": "uuid-a1"
  }
}
```

---

### `GET_BID_HISTORY`

**Request**
```json
{
  "action"   : "GET_BID_HISTORY",
  "token"    : "eyJhbGci...",
  "requestId": "req-022",
  "data": {
    "auctionId": "uuid-a1"
  }
}
```

**Response data**
```json
{
  "bids": [
    { "bidder": "alice", "amount": 3200000, "time": "...", "isAutoBid": false }
  ]
}
```

---

## 6. Server Push Events

> Push là tin nhắn server chủ động gửi xuống client **không có** client request trước. Client phải có background thread liên tục đọc và phân loại: nếu có `"type":"PUSH"` thì dispatch cho Observer, nếu có `"requestId"` thì match với callback đang chờ.

---

### `BID_PLACED` — Có bid mới trong auction

```json
{
  "type" : "PUSH",
  "event": "BID_PLACED",
  "data" : {
    "auctionId"      : "uuid-a1",
    "bidderId"       : "uuid-alice",
    "bidderName"     : "alice",
    "amount"         : 3200000,
    "newCurrentPrice": 3200000,
    "isAutoBid"      : false,
    "timestamp"      : "2026-04-08T14:35:22"
  }
}
```

---

### `AUCTION_CLOSED` — Auction kết thúc

```json
{
  "type" : "PUSH",
  "event": "AUCTION_CLOSED",
  "data" : {
    "auctionId" : "uuid-a1",
    "winnerId"  : "uuid-alice",
    "winnerName": "alice",
    "finalPrice": 5100000,
    "closedAt"  : "2026-04-09T18:00:00"
  }
}
```

---

### `AUTO_BID_PLACED` — Auto-bid của chính mình vừa chạy

```json
{
  "type" : "PUSH",
  "event": "AUTO_BID_PLACED",
  "data" : {
    "auctionId"      : "uuid-a1",
    "amount"         : 3300000,
    "newCurrentPrice": 3300000,
    "remainingMax"   : 1700000   // maxBid - amount còn lại
  }
}
```

---

### `AUTO_BID_FAILED` — Auto-bid bị vượt, không đủ maxBid

```json
{
  "type" : "PUSH",
  "event": "AUTO_BID_FAILED",
  "data" : {
    "auctionId"   : "uuid-a1",
    "message"     : "Đã đạt giá tối đa, auto-bid đã tắt",
    "currentPrice": 5100000
  }
}
```

---



