USE defaultdb;  -- ← sửa lại tên DB cho khớp schema.sql (đang dùng 'auction_system' sai)

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE bid_transactions;
TRUNCATE TABLE auctions;
TRUNCATE TABLE items;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. USERS (giữ nguyên, không có gì sai)
INSERT INTO users (id, username, password, email, role, admin_level, reputation_score) VALUES
                                                                                           ('u-admin',  'admin',   '$2a$12$V8YmL9W5SlpC51tkc2EuG.YABiHmuhHyOaABfYErr0DLuNxUapsTC', 'admin@auction.com',   'ADMIN',  1,   NULL),
                                                                                           ('u-sel-01', 'seller1', '$2a$12$V8YmL9W5SlpC51tkc2EuG.YABiHmuhHyOaABfYErr0DLuNxUapsTC', 'seller1@auction.com', 'SELLER', 0,   4.8),
                                                                                           ('u-sel-02', 'seller2', '$2a$12$V8YmL9W5SlpC51tkc2EuG.YABiHmuhHyOaABfYErr0DLuNxUapsTC', 'seller2@auction.com', 'SELLER', 0,   5.0),
                                                                                           ('u-bid-01', 'bidder1', '$2a$12$V8YmL9W5SlpC51tkc2EuG.YABiHmuhHyOaABfYErr0DLuNxUapsTC', 'bidder1@auction.com', 'BIDDER', 0,   NULL),
                                                                                           ('u-bid-02', 'bidder2', '$2a$12$V8YmL9W5SlpC51tkc2EuG.YABiHmuhHyOaABfYErr0DLuNxUapsTC', 'bidder2@auction.com', 'BIDDER', 0,   NULL),
                                                                                           ('u-bid-03', 'bidder3', '$2a$12$V8YmL9W5SlpC51tkc2EuG.YABiHmuhHyOaABfYErr0DLuNxUapsTC', 'bidder3@auction.com', 'BIDDER', 0,   NULL);

-- 2. ITEMS — Sửa: Vehicle dùng đúng cột make/vehicle_model/year/mileage
INSERT INTO items (id, title, description, category, seller_id,
                   brand,   model,              warranty_months,  -- Electronics
                   make,    vehicle_model,      year,   mileage,  -- Vehicle
                   artist,  medium, year_created)                 -- Art
VALUES
-- ELECTRONICS
('i-001', 'MacBook Pro M3',    'Máy nguyên seal mới 100%',  'ELECTRONICS', 'u-sel-01',
 'Apple', 'M3 Pro 14-inch', 12,   NULL, NULL, NULL, NULL,   NULL, NULL, NULL),

('i-004', 'iPhone 15 Pro Max', 'Hàng likenew 99%',          'ELECTRONICS', 'u-sel-02',
 'Apple', '15 Pro Max 256GB', 12, NULL, NULL, NULL, NULL,   NULL, NULL, NULL),

-- VEHICLE — dùng đúng cột make/vehicle_model/year/mileage
('i-003', 'Yamaha R1',         'Xe motor phân khối lớn',    'VEHICLE',     'u-sel-02',
 NULL,  NULL,  NULL,   'Yamaha', 'YZF-R1', 2024, 0,        NULL, NULL, NULL),

-- ART
('i-002', 'Tranh Mona Lisa',   'Bản sao chép tay xịn',      'ART',         'u-sel-01',
 NULL,  NULL,  NULL,   NULL,  NULL,  NULL,  NULL,           'Leonardo da Vinci Copy', 'Sơn dầu', 2020),

('i-005', 'Bình gốm thời Minh','Đồ cổ sưu tầm',             'ART',         'u-sel-01',
 NULL,  NULL,  NULL,   NULL,  NULL,  NULL,  NULL,           'Khuyết danh', 'Gốm sứ', 1400);

-- 3. AUCTIONS (giữ nguyên kịch bản)
INSERT INTO auctions (id, item_id, seller_id, start_price, current_price, min_bid_increment,
                      start_time, end_time, status, current_leader, winner_id, bid_count) VALUES
                                                                                              ('AUC-01','i-001','u-sel-01', 35000000,35000000, 500000, DATE_ADD(NOW(),INTERVAL 1 DAY), DATE_ADD(NOW(),INTERVAL 7 DAY),'OPEN',    NULL,      NULL,       0),
                                                                                              ('AUC-02','i-002','u-sel-01',  5000000, 6000000, 100000, DATE_SUB(NOW(),INTERVAL 1 DAY), DATE_ADD(NOW(),INTERVAL 3 DAY),'RUNNING', 'bidder3', NULL,       3),
                                                                                              ('AUC-03','i-004','u-sel-02', 25000000,25000000, 200000, DATE_SUB(NOW(),INTERVAL 2 DAY), DATE_ADD(NOW(),INTERVAL 5 MINUTE),'RUNNING',NULL,    NULL,       0),
                                                                                              ('AUC-04','i-005','u-sel-01', 15000000,18000000, 500000, DATE_SUB(NOW(),INTERVAL 5 DAY), DATE_SUB(NOW(),INTERVAL 1 DAY),'FINISHED','bidder2', 'u-bid-02', 2);

-- 4. BID_TRANSACTIONS — Sửa: thêm cột bidder_id (là ID thật), bidder_name (là username)
INSERT INTO bid_transactions (id, auction_id, bidder_id,  bidder_name, amount, timestamp, is_auto_bid) VALUES
-- AUC-02
('bid-001','AUC-02', 'u-bid-01', 'bidder1',  5200000, DATE_SUB(NOW(),INTERVAL 2 HOUR), FALSE),
('bid-002','AUC-02', 'u-bid-02', 'bidder2',  5500000, DATE_SUB(NOW(),INTERVAL 1 HOUR), TRUE),
('bid-003','AUC-02', 'u-bid-03', 'bidder3',  6000000, NOW(),                           FALSE),
-- AUC-04
('bid-004','AUC-04', 'u-bid-01', 'bidder1', 16000000, DATE_SUB(NOW(),INTERVAL 2 DAY),  FALSE),
('bid-005','AUC-04', 'u-bid-02', 'bidder2', 18000000, DATE_SUB(NOW(),INTERVAL 1 DAY),  FALSE);