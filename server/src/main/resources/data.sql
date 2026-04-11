-- 1. Bơm đủ 1 Admin, 2 Sellers, 3 Bidders (Đúng cam kết task list)
INSERT INTO users (username, password, email, role) VALUES
('admin', 'admin123', 'admin@auction.com', 'ADMIN'),
('seller1', '123456', 'seller1@auction.com', 'SELLER'),
('seller2', '123456', 'seller2@auction.com', 'SELLER'),
('bidder1', '123456', 'bidder1@auction.com', 'BIDDER'),
('bidder2', '123456', 'bidder2@auction.com', 'BIDDER'),
('bidder3', '123456', 'bidder3@auction.com', 'BIDDER');

-- 2. Bơm đủ 5 Items (Gắn cho seller 2 và 3)
INSERT INTO items (name, description, starting_price, category, seller_id) VALUES
('MacBook Pro M3', 'Máy nguyên seal mới 100%', 35000000.00, 'ELECTRONICS', 2),
('Tranh Mona Lisa', 'Bản sao chép tay xịn', 5000000.00, 'ART', 2),
('Yamaha R1', 'Xe motor phân khối lớn', 300000000.00, 'VEHICLE', 3),
('iPhone 15 Pro Max', 'Hàng likenew 99%', 25000000.00, 'ELECTRONICS', 3),
('Bình gốm thời Minh', 'Đồ cổ sưu tầm', 15000000.00, 'ART', 2);

-- 3. Bơm Auctions (Tạo các kịch bản test đặc biệt)
INSERT INTO auctions (id, item_id, current_price, start_time, end_time, status) VALUES
-- Kịch bản 1: Mở bán trong tương lai (Chưa ai được bid)
('AUC-01', 1, 35000000.00, DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 'OPEN'),

-- Kịch bản 2: Đang chạy bình thường (Dành cho test Concurrent Bid & Auto Bid)
('AUC-02', 2, 6000000.00, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RUNNING'),

-- Kịch bản 3: SẮP HẾT HẠN (Để test AuctionTimerService và Anti-sniping) -> Kết thúc sau 5 phút nữa!
('AUC-03', 4, 25000000.00, NOW(), DATE_ADD(NOW(), INTERVAL 5 MINUTE), 'RUNNING'),

-- Kịch bản 4: Đã kết thúc (Để UI test hiển thị lịch sử / biểu đồ)
('AUC-04', 5, 18000000.00, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'FINISHED');

-- 4. Bơm dữ liệu lịch sử đấu giá (Tạo kịch bản tranh giành để vẽ Biểu đồ Line Chart cho AUC-02)
INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, timestamp) VALUES
('AUC-02', 4, 5200000.00, DATE_SUB(NOW(), INTERVAL 2 HOUR)), -- bidder1 trả giá
('AUC-02', 5, 5500000.00, DATE_SUB(NOW(), INTERVAL 1 HOUR)), -- bidder2 nhảy vào tranh
('AUC-02', 6, 6000000.00, NOW()),                            -- bidder3 chốt giá cao nhất

('AUC-04', 4, 16000000.00, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('AUC-04', 5, 18000000.00, DATE_SUB(NOW(), INTERVAL 1 DAY));