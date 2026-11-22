-- Práctica: OrderRepository.lines(orderId).
-- El pedido solicitado es el 101.
SELECT l.order_id, l.product_id, p.sku, l.quantity, l.unit_price
FROM sql_backend_41_50.order_line AS l
JOIN sql_backend_41_50.product AS p ON p.id = l.product_id
WHERE l.product_id = 101
ORDER BY l.product_id;
