SELECT DISTINCT l.sku
FROM sql_lab20.lotes l
WHERE NOT EXISTS (
    SELECT 1
    FROM sql_lab20.movimientos m
    WHERE m.canal = 'WEB' AND m.unidades < 0
)
ORDER BY l.sku;
