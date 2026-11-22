SELECT e.nombre AS estante, p.nombre AS pasillo
FROM sql_lab20.zonas e
JOIN sql_lab20.zonas p ON p.id = e.id
WHERE e.id IN (4, 5, 6) AND e.habilitada
ORDER BY e.id;
