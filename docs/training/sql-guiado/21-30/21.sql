SELECT centro, SUM(solicitadas) AS pendientes
FROM sql_lab20.expediciones
WHERE servidas < solicitadas
GROUP BY centro
ORDER BY centro;
