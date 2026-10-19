-- Loaded exclusively by dev/test profiles. Never enable these profiles in production.
-- The next schema migration must use V5 or later, including in the production location.
INSERT INTO app_user(username,password_hash,role) VALUES
 ('admin','$2a$12$bv.ttChn3L.KtB9VyffC8eHxyEonWOvkDKrRxQKcBYjDSWjuA6NQS','ADMIN'),
 ('operations','$2a$12$bv.ttChn3L.KtB9VyffC8eHxyEonWOvkDKrRxQKcBYjDSWjuA6NQS','OPERATIONS'),
 ('readonly','$2a$12$bv.ttChn3L.KtB9VyffC8eHxyEonWOvkDKrRxQKcBYjDSWjuA6NQS','READONLY');
INSERT INTO customer(name,tax_id,email,address) VALUES
 ('Ferretería Central SL','B28000001','compras@central.example','Calle Mayor 12, 28013 Madrid'),
 ('Suministros Levante SL','B46000002','pedidos@levante.example','Avenida del Puerto 45, 46021 Valencia'),
 ('Talleres Norte SA','A48000003','almacen@norte.example','Calle Industria 8, 48001 Bilbao');
INSERT INTO product(sku,name,unit_price) VALUES
 ('GLOVE-100','Caja de guantes de trabajo',24.90),
 ('DRILL-18V','Taladro profesional 18V',129.50),
 ('SCREW-M8','Caja de tornillos M8',12.75),
 ('HELMET-W','Casco de seguridad blanco',18.00),
 ('BOOT-42','Botas de seguridad talla 42',59.95),
 ('TAPE-50','Cinta industrial 50mm',6.80),
 ('LADDER-3','Escalera de aluminio 3m',149.00),
 ('GLASSES-C','Gafas protectoras',8.50);
INSERT INTO inventory_balance(product_id,on_hand,reserved)
 SELECT id, CASE sku WHEN 'DRILL-18V' THEN 12 WHEN 'LADDER-3' THEN 3 ELSE 100 END,0 FROM product;
INSERT INTO stock_movement(product_id,kind,quantity,reason)
 SELECT product_id,'ADJUSTMENT',on_hand,'Development opening balance' FROM inventory_balance;
INSERT INTO sales_order(customer_id,status,total) SELECT id,'DRAFT',49.80 FROM customer WHERE tax_id='B28000001';
INSERT INTO order_line(order_id,product_id,quantity,unit_price)
 SELECT o.id,p.id,2,p.unit_price FROM sales_order o CROSS JOIN product p WHERE p.sku='GLOVE-100';
INSERT INTO audit_event(actor,action,entity_type,entity_id,details)
 VALUES ('system','DEVELOPMENT_SEED','SYSTEM','dev','Development catalog, balances, users and draft order initialized');
