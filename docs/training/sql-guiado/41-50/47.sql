-- Práctica: compras pendientes en ReplenishmentRepository.
-- Se muestran solo los productos que tienen compras abiertas.
SELECT l.product_id,
       SUM(l.quantity) AS incoming
FROM sql_backend_41_50.purchase_order_line AS l
JOIN sql_backend_41_50.purchase_order AS o ON o.id = l.purchase_order_id
WHERE o.status IN ('SUBMITTED', 'PARTIALLY_RECEIVED')
GROUP BY l.product_id
ORDER BY l.product_id;
