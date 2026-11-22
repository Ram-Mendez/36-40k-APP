SELECT p.nombre AS pasillo, COUNT(e.id) AS estantes_habilitados
FROM sql_lab20.zonas p
LEFT JOIN sql_lab20.zonas e ON e.padre_id = p.id
WHERE p.id IN (2, 3, 8) AND e.habilitada = true
GROUP BY p.id, p.nombre
ORDER BY p.id;
