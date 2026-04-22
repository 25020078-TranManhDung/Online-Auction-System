USE auction_system;

-- TẮT KIỂM TRA KHÓA NGOẠI ĐỂ XÓA DỮ LIỆU CŨ (Nếu có chạy lại file này)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE bid_transactions;
TRUNCATE TABLE auctions;
TRUNCATE TABLE items;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;


-- 1. BƠM ĐỦ 1 ADMIN, 2 SELLERS, 3 BIDDERS
-- Sử dụng ID dạng String để khớp với Entity.java (UUID/String)

INSERT INTO users (id, username, password, email, role, admin_level, reputation_score) VALUES
('u-admin',  'admin',   'admin123', 'admin@auction.com',   'ADMIN', 1, NULL),
('u-sel-01', 'seller1', '123456',   'seller1@auction.com', 'SELLER', 0, 4.8),
('u-sel-02', 'seller2', '123456',   'seller2@auction.com', 'SELLER', 0, 5.0),
('u-bid-01', 'bidder1', '123456',   'bidder1@auction.com', 'BIDDER', 0, NULL),
('u-bid-02', 'bidder2', '123456',   'bidder2@auction.com', 'BIDDER', 0, NULL),
('u-bid-03', 'bidder3', '123456',   'bidder3@auction.com', 'BIDDER', 0, NULL);

-- =========================================================================
-- 2. BƠM ĐỦ 5 ITEMS (Gắn cho seller1 và seller2)
-- Sửa `name` -> `title`, bơm thêm thông tin riêng biệt (brand, model, artist...)
-- =========================================================================
INSERT INTO items (id, title, description, category, seller_id, brand, model, artist) VALUES
('i-001', 'MacBook Pro M3', 'Máy nguyên seal mới 100%', 'ELECTRONICS', 'u-sel-01', 'Apple', 'M3 Pro 14-inch', NULL),
('i-002', 'Tranh Mona Lisa', 'Bản sao chép tay xịn', 'ART', 'u-sel-01', NULL, NULL, 'Leonardo da Vinci Copy'),
('i-003', 'Yamaha R1', 'Xe motor phân khối lớn', 'VEHICLE', 'u-sel-02', 'Yamaha', 'YZF-R1 2024', NULL),
('i-004', 'iPhone 15 Pro Max', 'Hàng likenew 99%', 'ELECTRONICS', 'u-sel-02', 'Apple', '15 Pro Max 256GB', NULL),
('i-005', 'Bình gốm thời Minh', 'Đồ cổ sưu tầm', 'ART', 'u-sel-01', NULL, NULL, 'Khuyết danh');


-- 3. BƠM AUCTIONS (Giữ nguyên 4 kịch bản test đặc biệt của bạn)
-- Bổ sung seller_id, start_price, min_bid_increment, current_leader, bid_count

INSERT INTO auctions (id, item_id, seller_id, start_price, current_price, min_bid_increment, start_time, end_time, status, current_leader, bid_count) VALUES

-- Kịch bản 1: Mở bán trong tương lai (Chưa ai được bid)
('AUC-01', 'i-001', 'u-sel-01', 35000000.00, 35000000.00, 500000.00, DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 'OPEN', NULL, 0),

-- Kịch bản 2: Đang chạy bình thường (Test Concurrent Bid, có history để vẽ Chart) -> Đã có 3 bids
('AUC-02', 'i-002', 'u-sel-01', 5000000.00, 6000000.00, 100000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RUNNING', 'bidder3', 3),

-- Kịch bản 3: SẮP HẾT HẠN (Để test AuctionTimerService và Anti-sniping) -> Kết thúc sau 5 phút nữa!
('AUC-03', 'i-004', 'u-sel-02', 25000000.00, 25000000.00, 200000.00, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 5 MINUTE), 'RUNNING', NULL, 0),

-- Kịch bản 4: Đã kết thúc (Test UI hiển thị lịch sử / biểu đồ) -> Đã có 2 bids
('AUC-04', 'i-005', 'u-sel-01', 15000000.00, 18000000.00, 500000.00, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'FINISHED', 'bidder2', 2);


-- 4. BƠM DỮ LIỆU LỊCH SỬ ĐẤU GIÁ (Dùng bidder_name thay vì id theo chuẩn DTO)

INSERT INTO bid_transactions (id, auction_id, bidder_name, amount, timestamp, is_auto_bid) VALUES

-- Lịch sử cho AUC-02 (Đang chạy, vẽ Line Chart cực đẹp)
('bid-001', 'AUC-02', 'bidder1', 5200000.00, DATE_SUB(NOW(), INTERVAL 2 HOUR), FALSE), -- bidder1 trả giá
('bid-002', 'AUC-02', 'bidder2', 5500000.00, DATE_SUB(NOW(), INTERVAL 1 HOUR), TRUE),  -- bidder2 nhảy vào (auto bid)
('bid-003', 'AUC-02', 'bidder3', 6000000.00, NOW(), FALSE),                            -- bidder3 chốt giá cao nhất hiện tại

-- Lịch sử cho AUC-04 (Đã kết thúc, bidder2 win)
('bid-004', 'AUC-04', 'bidder1', 16000000.00, DATE_SUB(NOW(), INTERVAL 2 DAY), FALSE),
('bid-005', 'AUC-04', 'bidder2', 18000000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), FALSE);