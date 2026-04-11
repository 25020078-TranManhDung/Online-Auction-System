-- 1. Bơm dữ liệu bảng users
INSERT INTO users (username, password, email, role) VALUES
('admin', 'admin123', 'admin@auction.com', 'ADMIN'),
('nguoiban', '123456', 'seller@auction.com', 'SELLER'),
('nguoimua', '123456', 'bidder@auction.com', 'BIDDER');

-- 2. Bơm dữ liệu bảng items (Gắn với người bán có id = 2)
INSERT INTO items (name, description, starting_price, category, seller_id) VALUES
('MacBook Pro M3', 'Máy nguyên seal mới 100%', 35000000.00, 'ELECTRONICS', 2),
('Tranh Mona Lisa', 'Bản sao chép tay xịn', 5000000.00, 'ART', 2),
('Yamaha R1', 'Xe motor phân khối lớn', 300000000.00, 'VEHICLE', 2);

-- 3. Bơm dữ liệu bảng auctions (Tạo 2 phiên đấu giá)
INSERT INTO auctions (id, item_id, current_price, start_time, end_time, status) VALUES
('AUC-MACBOOK-01', 1, 35000000.00, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'OPEN'),
('AUC-TRANH-01', 2, 5000000.00, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'RUNNING');

-- 4. Bơm dữ liệu bảng bid_transactions (Người mua id=3 đấu giá bức tranh)
INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, timestamp) VALUES
('AUC-TRANH-01', 3, 5500000.00, NOW());