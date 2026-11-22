WITH entradas(id, instante) AS (VALUES (1, timestamp '2025-01-01 10:00'),(2, timestamp '2025-01-31 15:00'),(3, timestamp '2025-02-01 00:00'))
SELECT id FROM entradas WHERE instante BETWEEN '2025-01-01' AND '2025-01-31' ORDER BY id;
