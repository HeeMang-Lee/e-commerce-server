-- E-Commerce Database Schema

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    point_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_email (email)
);

CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    base_price DECIMAL(10,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    category VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_created (user_id, created_at),
    INDEX idx_order_number (order_number),
    INDEX idx_created_at (created_at)  -- PopularProduct 쿼리 최적화를 위한 인덱스
);

CREATE TABLE order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    snapshot_product_name VARCHAR(200) NOT NULL,
    snapshot_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    item_total_amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_order (order_id),
    INDEX idx_product (product_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at),
    INDEX idx_order_product_quantity (order_id, product_id, quantity)  -- PopularProduct 쿼리를 위한 커버링 인덱스
);

CREATE TABLE order_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL UNIQUE,
    user_coupon_id BIGINT,
    original_amount DECIMAL(15,2) NOT NULL,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    used_point INT NOT NULL DEFAULT 0,
    final_amount DECIMAL(15,2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_data TEXT,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_order (order_id),
    INDEX idx_status (payment_status),
    INDEX idx_paid_at (paid_at)
);

CREATE TABLE coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value INT NOT NULL,
    max_discount_amount DECIMAL(10,2),
    max_issue_count INT NOT NULL,
    current_issue_count INT NOT NULL DEFAULT 0,
    issue_start_date TIMESTAMP NOT NULL,
    issue_end_date TIMESTAMP NOT NULL,
    valid_period_days INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_issue_period (issue_start_date, issue_end_date),
    INDEX idx_status (status),
    INDEX idx_issue_count (current_issue_count)
);

CREATE TABLE user_coupons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    INDEX idx_user_status (user_id, status),
    INDEX idx_coupon (coupon_id),
    INDEX idx_expires (expires_at)
);

CREATE TABLE point_histories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    related_order_id BIGINT,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_created (user_id, created_at),
    INDEX idx_order (related_order_id),
    INDEX idx_type (transaction_type)
);

CREATE TABLE outbox_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,

    INDEX idx_status (status),
    INDEX idx_created (created_at)
);

-- Sample Data
INSERT INTO users (id, name, email, point_balance) VALUES
(1, 'Test User 1', 'test1@test.com', 50000),
(2, 'Test User 2', 'test2@test.com', 30000);

INSERT INTO products (id, name, description, base_price, stock_quantity, category, status) VALUES
(1, 'Wireless Keyboard', 'Mechanical Wireless Keyboard', 89000, 100, 'Electronics', 'ACTIVE'),
(2, 'Wireless Mouse', 'Ergonomic Wireless Mouse', 45000, 200, 'Electronics', 'ACTIVE'),
(3, '27inch Monitor', 'FHD 27inch Monitor', 300000, 50, 'Electronics', 'ACTIVE'),
(4, 'Webcam HD', 'HD Webcam for streaming', 35000, 150, 'Accessories', 'ACTIVE'),
(5, 'USB-C Hub', 'Multi-port USB-C Hub', 55000, 80, 'Accessories', 'ACTIVE');

-- View: Top products by sales in last 3 days
CREATE OR REPLACE VIEW popular_products_view AS
SELECT
    oi.product_id,
    SUM(oi.quantity) as sales_count,
    SUM(oi.item_total_amount) as sales_amount,
    MAX(o.created_at) as last_updated
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
  AND oi.status IN ('PENDING', 'CONFIRMED')
GROUP BY oi.product_id
ORDER BY sales_count DESC;
