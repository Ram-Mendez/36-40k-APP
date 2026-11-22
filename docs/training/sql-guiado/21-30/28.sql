SELECT centro, ROUND(AVG(100.0 * servidas / solicitadas), 2) AS porcentaje
FROM sql_lab20.expediciones
GROUP BY centro
ORDER BY centro;
