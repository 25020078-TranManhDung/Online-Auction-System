CREATE DATABASE auction_system;
USE auction_system;

-- Bảng users:
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    role ENUM ('ADMIN', 'SELLER', 'BIDDER') NOT NULL
);

-- Bảng Items:
CREATE TABLE items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DECIMAL(15,2) NOT NULL,
    category ENUM('ELECTRONICS', 'ART', 'VEHICLE') NOT NULL,
    seller_id INT NOT NULL,
    FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- Bảng auction:
CREATE TABLE auctions (
    id VARCHAR(50) PRIMARY KEY,
    item_id INT NOT NULL,
    current_price DECIMAL(15,2) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
    FOREIGN KEY (item_id) REFERENCES items(id)
);

-- Bảng transactions
CREATE TABLE bid_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id VARCHAR(50) NOT NULL,
    bidder_id INT NOT NULL,
    bid_amount DECIMAL(15,2) NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auctions(id),
    FOREIGN KEY (bidder_id) REFERENCES users(id)
);

CREATE INDEX idx_auction_id ON bid_transactions(auction_id);
CREATE INDEX idx_auction_status ON auctions(status);