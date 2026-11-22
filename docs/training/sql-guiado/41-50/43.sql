-- Práctica: InventoryRepository.balances(...).
-- Inspeccionamos dos productos del catálogo.
SELECT p.id, p.sku,
       COALESCE(i.on_hand, 0) AS on_hand,
       COALESCE(i.reserved, 0) AS reserved
FROM sql_backend_41_50.product AS p
JOIN sql_backend_41_50.inventory_balance AS i ON i.product_id = p.id
WHERE p.id IN (1, 4)
ORDER BY p.id;
