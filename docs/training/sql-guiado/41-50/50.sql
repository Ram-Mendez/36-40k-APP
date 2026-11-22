-- Práctica: ReportRepository.lowStock(threshold, page, size).
-- Se usa un umbral de 6 unidades disponibles.
SELECT p.id, p.sku,
       COALESCE(i.on_hand, 0) - COALESCE(i.reserved, 0) AS available,
       (SELECT COUNT(DISTINCT r.order_id)
        FROM sql_backend_41_50.stock_reservation AS r) AS orders_reserved
FROM sql_backend_41_50.product AS p
LEFT JOIN sql_backend_41_50.inventory_balance AS i ON i.product_id = p.id
WHERE p.active = true
  AND COALESCE(i.on_hand, 0) - COALESCE(i.reserved, 0) <= 6
ORDER BY available, p.id;
