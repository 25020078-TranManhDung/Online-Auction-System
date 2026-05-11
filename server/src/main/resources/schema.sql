USE defaultdb;

-- 1. Bảng Users (Khớp User.java và các sub-classes)
CREATE TABLE users (
                       id VARCHAR(50) PRIMARY KEY,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(100) UNIQUE,
                       role ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL,
                       admin_level INT DEFAULT 0,          -- Thuộc tính của Admin.java
                       reputation_score DOUBLE DEFAULT 5.0, -- Thuộc tính của Seller.java
                       status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 2. Bảng Items (Gộp Item.java và subclasses Electronics, Vehicle, Art)
CREATE TABLE items (
                       id VARCHAR(50) PRIMARY KEY,
                       title VARCHAR(255) NOT NULL,
                       description TEXT,
                       category ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') NOT NULL,
                       seller_id VARCHAR(50) NOT NULL,

    -- Các trường đặc thù (Null nếu không thuộc category đó)
                       brand VARCHAR(100), model VARCHAR(100), warranty_months INT, -- Electronics
                       make VARCHAR(100), vehicle_model VARCHAR(100), year INT, mileage INT, -- Vehicle
                       artist VARCHAR(100), medium VARCHAR(100), year_created INT, -- Art

                       FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Bảng Auctions (Khớp Auction.java và AuctionResponse.java)
CREATE TABLE auctions (
                          id VARCHAR(50) PRIMARY KEY,
                          item_id VARCHAR(50) NOT NULL,
                          seller_id VARCHAR(50) NOT NULL,
                          start_price DOUBLE NOT NULL,
                          current_price DOUBLE NOT NULL,
                          min_bid_increment DOUBLE NOT NULL,
                          start_time DATETIME NOT NULL,
                          end_time DATETIME NOT NULL,
                          status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
                          current_leader VARCHAR(100), -- bidderName dẫn đầu
                          bid_count INT DEFAULT 0,
                          FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                          FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- 4. Bảng Bid Transactions (Khớp BidTransaction.java)
CREATE TABLE bid_transactions (
                                  id VARCHAR(50) PRIMARY KEY,
                                  auction_id VARCHAR(50) NOT NULL,
                                  bidder_id VARCHAR(50) NOT NULL,      -- ID người đặt giá (FK)
                                  bidder_name VARCHAR(100),            -- Tên hiển thị (cache)
                                  amount DOUBLE NOT NULL,
                                  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  is_auto_bid BOOLEAN DEFAULT FALSE,
                                  FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                                  FOREIGN KEY (bidder_id) REFERENCES users(id)
);
-- TĂNG TỐC HỆ THỐNG (INDEX)

CREATE INDEX idx_auction_status ON auctions(status); -- Tìm nhanh các phiên đang chạy
CREATE INDEX idx_bid_history ON bid_transactions(auction_id, amount DESC); -- Lấy top bid nhanh nhất
CREATE INDEX idx_item_category ON items(category); -- Lọc theo loại sản phẩm
-- 5. Bảng Auto Bid Settings (khớp AutoBidSetting.java và AutoBidDaoImpl.java)
CREATE TABLE IF NOT EXISTS auto_bid_settings (
                                                 id              VARCHAR(50)  PRIMARY KEY,
                                                 bidder_id       VARCHAR(50)  NOT NULL,
                                                 auction_id      VARCHAR(50)  NOT NULL,
                                                 max_bid         DOUBLE       NOT NULL,
                                                 increment       DOUBLE       NOT NULL,
                                                 is_active       BOOLEAN      DEFAULT TRUE,
                                                 registered_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
                                                 FOREIGN KEY (bidder_id)  REFERENCES users(id)    ON DELETE CASCADE,
                                                 FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

-- INDEX tăng tốc truy vấn findActiveByAuction() trong AutoBidService
CREATE INDEX idx_autobid_auction_active ON auto_bid_settings(auction_id, is_active, registered_at);