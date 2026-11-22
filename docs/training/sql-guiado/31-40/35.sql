WITH cargos(importe) AS (VALUES (1.005::numeric),(1.005::numeric))
SELECT SUM(ROUND(importe, 2)) AS total FROM cargos;
