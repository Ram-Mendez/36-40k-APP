-- Clientes que todavía no han completado ningún pedido.
SELECT c.id, c.nombre
FROM sql_lab10.clientes AS c
WHERE NOT EXISTS (
    SELECT 1
    FROM sql_lab10.pedidos AS p
    WHERE p.estado = 'COMPLETED'
)
ORDER BY c.id;
