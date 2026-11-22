SELECT e.id, e.solicitadas * t.euros AS cargo
FROM sql_lab20.expediciones e
JOIN sql_lab20.tarifas t ON t.sku = e.sku
WHERE e.dia >= '2025-01-01' AND e.dia < '2025-02-01'
  AND t.desde = (
      SELECT MAX(h.desde)
      FROM sql_lab20.tarifas h
      WHERE h.desde <= e.dia
  )
ORDER BY e.id;
