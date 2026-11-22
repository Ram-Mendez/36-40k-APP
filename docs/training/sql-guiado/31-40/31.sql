WITH pedidos(id, activo, canal, urgente) AS (VALUES (1,true,'WEB',false),(2,true,'TIENDA',true),(3,false,'WEB',false),(4,true,'TIENDA',false))
SELECT id FROM pedidos WHERE activo AND urgente OR canal = 'WEB' ORDER BY id;
