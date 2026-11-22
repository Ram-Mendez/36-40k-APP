WITH lecturas(id, temperatura) AS (VALUES (1,95),(2,75),(3,50))
SELECT id, CASE WHEN temperatura >= 70 THEN 'ALTA' WHEN temperatura >= 90 THEN 'CR?TICA' ELSE 'NORMAL' END AS nivel FROM lecturas ORDER BY id;
