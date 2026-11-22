SELECT centro, SUM(DISTINCT bultos) AS bultos
FROM sql_lab20.turnos
GROUP BY centro
ORDER BY centro;
