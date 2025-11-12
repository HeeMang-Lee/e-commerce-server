-- Create popular products view for tests
CREATE OR REPLACE VIEW popular_products_view AS
SELECT
    oi.product_id,
    SUM(oi.quantity) as sales_count
FROM order_items oi
INNER JOIN orders o ON oi.order_id = o.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY oi.product_id;
