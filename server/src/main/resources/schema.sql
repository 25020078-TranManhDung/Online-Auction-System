USE defaultdb;

-- ============================================================
-- 1. Bảng users
--    Đồng bộ với DB thực tế: thêm full_name, held_amount,
--    violation_count, locked_until (có trong DB nhưng thiếu
--    trong schema cũ)
-- ============================================================
CREATE TABLE users (
                       id               VARCHAR(50)  PRIMARY KEY,
                       username         VARCHAR(50)  NOT NULL UNIQUE,
                       password         VARCHAR(255) NOT NULL,
                       email            VARCHAR(100) UNIQUE,
                       full_name        VARCHAR(100),                       -- FIX: RegisterController gửi nhưng schema cũ thiếu
                       avatar           LONGTEXT,                           -- [MỚI]: Cột lưu chuỗi Base64 của ảnh đại diện
                       role             ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL,
                       admin_level      INT          DEFAULT 0,             -- Thuộc tính Admin.java
                       reputation_score DOUBLE       DEFAULT 5.0,           -- Thuộc tính Seller.java
                       status           VARCHAR(20)  DEFAULT 'ACTIVE',
                       wallet_balance   DOUBLE       DEFAULT 0.0,
                       held_amount      DOUBLE       DEFAULT 0.0,           -- FIX: tiền đang bị giữ khi đặt giá
                       violation_count  INT          DEFAULT 0,             -- FIX: đếm vi phạm
                       locked_until     DATETIME     DEFAULT NULL           -- FIX: khoá tài khoản đến thời điểm cụ thể
);
-- ============================================================
-- 2. Bảng items
--    Sửa description TEXT → MEDIUMTEXT khớp DB thực tế
--    (TEXT = 65KB, MEDIUMTEXT = 16MB — tránh cắt dữ liệu dài)
-- ============================================================
CREATE TABLE items (
                       id               VARCHAR(50)  PRIMARY KEY,
                       title            VARCHAR(255) NOT NULL,
                       description      MEDIUMTEXT,                         -- FIX: TEXT → MEDIUMTEXT khớp DB
                       category         ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') NOT NULL,
                       seller_id        VARCHAR(50)  NOT NULL,

    -- Electronics
                       brand            VARCHAR(100),
                       model            VARCHAR(100),
                       warranty_months  INT,

    -- Vehicle
                       make             VARCHAR(100),
                       vehicle_model    VARCHAR(100),
                       year             INT,
                       mileage          INT,

    -- Art
                       artist           VARCHAR(100),
                       medium           VARCHAR(100),
                       year_created     INT,

                       FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ============================================================
-- 3. Bảng auctions
--    FIX NGHIÊM TRỌNG: thêm current_leader_id — dùng trong
--    AuctionDaoImpl, BidService, AutoBidService (fresh install
--    sẽ crash nếu thiếu cột này)
-- ============================================================
CREATE TABLE auctions (
                          id                  VARCHAR(50)  PRIMARY KEY,
                          item_id             VARCHAR(50)  NOT NULL,
                          seller_id           VARCHAR(50)  NOT NULL,
                          start_price         DOUBLE       NOT NULL,
                          current_price       DOUBLE       NOT NULL,
                          min_bid_increment   DOUBLE       NOT NULL,
                          start_time          DATETIME     NOT NULL,
                          end_time            DATETIME     NOT NULL,
                          status              ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
                          current_leader      VARCHAR(100),                    -- Tên bidder dẫn đầu (hiển thị)
                          current_leader_id   VARCHAR(255) DEFAULT NULL,       -- FIX: ID bidder dẫn đầu (để refund, đã có trong DB)
                          winner_id           VARCHAR(50)  DEFAULT NULL,       -- ID người thắng (lưu vĩnh viễn)
                          bid_count           INT          DEFAULT 0,

                          FOREIGN KEY (item_id)   REFERENCES items(id)    ON DELETE CASCADE,
                          FOREIGN KEY (seller_id) REFERENCES users(id),
                          FOREIGN KEY (winner_id) REFERENCES users(id)    ON DELETE SET NULL
);

-- ============================================================
-- 4. Bảng bid_transactions
-- ============================================================
CREATE TABLE bid_transactions (
                                  id                VARCHAR(50)  PRIMARY KEY,
                                  auction_id        VARCHAR(50)  NOT NULL,
                                  bidder_id         VARCHAR(50)  NOT NULL,
                                  bidder_name       VARCHAR(100),                      -- Tên hiển thị (cache)
                                  current_leader_id VARCHAR(50)  DEFAULT NULL,
                                  amount            DOUBLE       NOT NULL,
                                  timestamp         DATETIME     DEFAULT CURRENT_TIMESTAMP,
                                  is_auto_bid       BOOLEAN      DEFAULT FALSE,

                                  FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                                  FOREIGN KEY (bidder_id)  REFERENCES users(id)
);

-- ============================================================
-- 5. Bảng wallet_transactions
--    FIX: type ENUM → VARCHAR(50) khớp DB thực tế
--    (DB đang dùng varchar(50), ENUM trong schema cũ không được
--    áp dụng; WalletDaoImpl dùng .name() nên vẫn chạy đúng)
-- ============================================================
CREATE TABLE wallet_transactions (
                                     id            VARCHAR(50)  PRIMARY KEY,
                                     user_id       VARCHAR(50)  NOT NULL,
                                     type          VARCHAR(50)  NOT NULL,                 -- FIX: ENUM → VARCHAR(50) khớp DB
    -- Giá trị hợp lệ: TOP_UP, BID_DEDUCT,
    -- BID_REFUND, AUCTION_WIN, SELLER_RECEIVE,
    -- COMMISSION, WITHDRAW
                                     amount        DOUBLE       NOT NULL,
                                     balance_after DOUBLE       NOT NULL,
                                     description   VARCHAR(255),
                                     auction_id    VARCHAR(50)  DEFAULT NULL,
                                     created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,

                                     FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
                                     FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE SET NULL
);

-- ============================================================
-- 6. Bảng auto_bid_settings
-- ============================================================
CREATE TABLE IF NOT EXISTS auto_bid_settings (
                                                 id            VARCHAR(50)  PRIMARY KEY,
                                                 bidder_id     VARCHAR(50)  NOT NULL,
                                                 auction_id    VARCHAR(50)  NOT NULL,
                                                 max_bid       DOUBLE       NOT NULL,
                                                 increment     DOUBLE       NOT NULL,
                                                 is_active     BOOLEAN      DEFAULT TRUE,
                                                 registered_at DATETIME     DEFAULT CURRENT_TIMESTAMP,

                                                 FOREIGN KEY (bidder_id)  REFERENCES users(id)    ON DELETE CASCADE,
                                                 FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

-- ============================================================
-- INDEX — tăng tốc truy vấn
-- ============================================================
CREATE INDEX idx_auction_status         ON auctions(status);
CREATE INDEX idx_bid_history            ON bid_transactions(auction_id, amount DESC);
CREATE INDEX idx_item_category          ON items(category);
CREATE INDEX idx_autobid_auction_active ON auto_bid_settings(auction_id, is_active, registered_at);
CREATE INDEX idx_wallet_user            ON wallet_transactions(user_id, created_at DESC);
CREATE INDEX idx_wallet_auction         ON wallet_transactions(auction_id);