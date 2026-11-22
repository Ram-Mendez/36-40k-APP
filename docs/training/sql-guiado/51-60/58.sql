-- Práctica: cantidad sugerida en ReplenishmentRepository.proposals(...).
WITH pending AS (
    SELECT l.product_id,
           SUM(l.quantity - l.received_quantity) AS incoming
    FROM sql_backend_51_60.purchase_order_line AS l
    JOIN sql_backend_51_60.purchase_order AS o ON o.id = l.purchase_order_id
    WHERE o.status IN ('SUBMITTED', 'PARTIALLY_RECEIVED')
    GROUP BY l.product_id
),
positions AS (
    SELECT r.product_id, b.on_hand - b.reserved AS available,
           COALESCE(p.incoming, 0) AS incoming, r.target_stock
    FROM sql_backend_51_60.replenishment_policy AS r
    JOIN sql_backend_51_60.inventory_balance AS b ON b.product_id = r.product_id
    LEFT JOIN pending AS p ON p.product_id = r.product_id
)
SELECT product_id, available, incoming, target_stock,
       GREATEST(0, target_stock - available) AS suggested_quantity
FROM positions
ORDER BY product_id;
