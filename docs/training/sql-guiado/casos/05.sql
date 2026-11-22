-- 05. The warehouse wants products with 4 or fewer available units.
-- Run the full query. Note the IDs before changing it.
SELECT p.id,
       p.nombre,
       COALESCE(s.fisicas, 0) - COALESCE(s.reservadas, 0) AS disponibles
FROM sql_lab10.productos AS p
LEFT JOIN sql_lab10.stock AS s ON s.producto_id = p.id
WHERE COALESCE(s.fisicas, 0) <= 4
ORDER BY p.id;
