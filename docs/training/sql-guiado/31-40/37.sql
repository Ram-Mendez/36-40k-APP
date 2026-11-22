WITH norte(aviso) AS (VALUES ('FALTA CAJA'),('REVISAR')), sur(aviso) AS (VALUES ('FALTA CAJA'))
SELECT aviso FROM norte UNION SELECT aviso FROM sur ORDER BY aviso;
