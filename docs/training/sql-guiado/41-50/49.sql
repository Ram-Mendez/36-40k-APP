-- Práctica: InventoryRepository.movements(productId, page, size).
-- Se solicita productId=1, page=1, size=2; las páginas empiezan en 0.
SELECT id, product_id, kind, quantity
FROM sql_backend_41_50.stock_movement
WHERE product_id = 1
ORDER BY id DESC
LIMIT 2 OFFSET 1;
