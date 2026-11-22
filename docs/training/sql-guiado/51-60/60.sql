-- Práctica: OrderRepository.search(...), con page=0, size=2 y sort=createdAt,asc.
-- Se quieren las líneas de los dos primeros pedidos por fecha de creación.
SELECT o.id AS order_id, l.product_id
FROM sql_backend_51_60.sales_order AS o
JOIN sql_backend_51_60.order_line AS l ON l.order_id = o.id
ORDER BY o.created_at ASC, o.id ASC, l.product_id ASC
LIMIT 2 OFFSET 0;
