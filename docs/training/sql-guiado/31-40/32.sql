WITH productos(codigo) AS (VALUES ('A'),('B'),('C')), bloqueados(codigo) AS (VALUES ('B'),(NULL))
SELECT codigo FROM productos WHERE codigo NOT IN (SELECT codigo FROM bloqueados) ORDER BY codigo;
