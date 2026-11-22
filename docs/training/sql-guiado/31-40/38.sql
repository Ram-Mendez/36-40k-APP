WITH puntos(persona, valor) AS (VALUES ('Ana',100),('Leo',100),('Mia',90))
SELECT persona, RANK() OVER (ORDER BY valor DESC) AS puesto FROM puntos ORDER BY valor DESC, persona;
