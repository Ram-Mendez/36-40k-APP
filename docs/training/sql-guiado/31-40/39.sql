WITH lecturas(maquina, instante, valor) AS (VALUES ('A',1,10),('B',2,20),('A',3,15),('B',4,25))
SELECT maquina, instante, valor, LAG(valor) OVER (ORDER BY instante) AS anterior FROM lecturas ORDER BY maquina, instante;
