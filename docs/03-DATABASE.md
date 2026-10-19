# PostgreSQL y consistencia

Flyway crea el esquema desde `src/main/resources/db/migration`. Hibernate únicamente valida (`ddl-auto=validate`). Los datos de demostración están separados en `db/dev`, activados por los perfiles dev y test. PostgreSQL conserva timestamps con zona (`timestamptz`); las interfaces intercambian instantes ISO 8601.

V1 crea maestros y auditoría, V2 seguridad y V3 pedidos/inventario. V4 está reservada al seed de desarrollo. La siguiente migración de esquema debe ser V5 o superior, también en producción. Una base de desarrollo no debe promoverse a producción quitando el perfil: usa una base independiente sin datos ni usuarios de demostración.

| Tabla | Claves y significado |
| --- | --- |
| `app_user` | PK id; username único, BCrypt, rol restringido, baja lógica. |
| `auth_session` | PK UUID; FK usuario; familia, hash SHA-256 único del refresh, vencimiento, uso y revocación. |
| `customer` | PK id; tax_id único; contacto, activo y versión optimista. |
| `product` | PK id; sku único; precio decimal positivo, activo y versión. |
| `sales_order` | FK cliente; estado restringido, total, versión, referencia/coste transporte. |
| `order_line` | FK pedido/producto; producto único por pedido; cantidad positiva y precio acordado. |
| `inventory_balance` | PK/FK producto; físicas, reservadas y versión. CHECK `on_hand >= reserved AND reserved >= 0`. |
| `stock_reservation` | PK compuesta pedido/producto; cantidad positiva. |
| `stock_movement` | FK producto y pedido opcional; tipo, cantidad firmada, motivo y fecha. |
| `audit_event` | Actor, acción, recurso, detalle, request_id y fecha. |

Las bajas de maestros no eliminan filas ni rompen el historial. Las referencias externas se guardan como datos, no como claves locales. El esquema de datos de ejemplo no sustituye las restricciones de producción.

## Transacciones obligatorias

**Confirmar:** bloquear pedido, comprobar DRAFT y cliente activo, leer líneas por id de producto, comprobar producto activo, bloquear cada saldo, verificar disponible y crear reserva/movimiento, cambiar a CONFIRMED, auditar. Si cualquier línea falla, se revierte todo.

**Cancelar:** bloquear pedido; admitir DRAFT o CONFIRMED. Si había reservas, borrarlas y disminuir `reserved` con movimientos RELEASE. Cambiar estado y auditar en la misma transacción.

**Completar:** tras obtener cotización, bloquear y verificar estado/versión, eliminar reservas, disminuir `reserved` y `on_hand`, crear movimientos SHIP y registrar referencia, coste y auditoría. La comparación de versión impide aplicar una cotización a un pedido que cambió entretanto.

**Ajustar:** bloquear saldo, calcular nuevo físico con control de overflow y comprobar que no cae por debajo de lo reservado. Saldo, movimiento ADJUSTMENT y auditoría son atómicos.

Cada flujo bloquea productos en orden creciente. Dos pedidos concurrentes que reclaman las mismas unidades se serializan en la fila de inventario; el segundo vuelve a leer el saldo comprometido. Las restricciones CHECK son una última barrera, no el algoritmo de negocio.

## SQL operativo

Sustituir `42` por el id investigado. Las consultas son de lectura.

```sql
-- Comparar saldo materializado con reservas de pedidos.
SELECT p.id, p.sku, i.on_hand, i.reserved,
       COALESCE(SUM(r.quantity), 0) AS reservation_total,
       i.on_hand-i.reserved AS available
FROM product p JOIN inventory_balance i ON i.product_id=p.id
LEFT JOIN stock_reservation r ON r.product_id=p.id
WHERE p.id=42
GROUP BY p.id,p.sku,i.on_hand,i.reserved;

-- Historial de un pedido, incluidos request IDs que permiten buscar logs.
SELECT created_at,actor,action,request_id,details
FROM audit_event WHERE entity_type='ORDER' AND entity_id='42'
ORDER BY created_at,id;

-- Detectar divergencias entre estado y reservas.
SELECT o.id,o.status,r.product_id,r.quantity
FROM sales_order o JOIN stock_reservation r ON r.order_id=o.id
WHERE o.status <> 'CONFIRMED';

-- Facturación operativa: solo pedidos completados, no borradores/reservas.
SELECT c.id,c.name,COUNT(*) AS completed_orders,SUM(o.total) AS net_sales
FROM customer c JOIN sales_order o ON o.customer_id=c.id
WHERE o.status='COMPLETED'
GROUP BY c.id,c.name ORDER BY net_sales DESC,c.id LIMIT 20 OFFSET 0;
```

`ReportRepository.lowStock` utiliza LEFT JOIN para incluir productos sin saldo, COALESCE para tratarlo como cero y una subconsulta que cuenta pedidos con reserva. `sales` agrega pedidos completados por cliente; `from` incluye el instante y `to` lo excluye, sobre `updated_at` de finalización. La paginación ejecuta consulta de total y consulta limitada.

## Índices y rendimiento

Hay índices por cliente/fecha y estado/fecha de pedido, producto en líneas y reservas, producto/fecha de movimientos, recurso/fecha en auditoría, usuario/familia/vencimiento en sesiones y activo/nombre en maestros. Las claves únicas también crean índices. Los patrones `%texto%` de búsqueda no aprovechan normalmente un B-tree: medir con `EXPLAIN (ANALYZE, BUFFERS)` antes de proponer una extensión o índice adicional.

No interpretar un plan con veinte filas de demostración como prueba de rendimiento en producción. Comparar cardinalidades, selectividad, plan y tiempo con un conjunto representativo en una base aislada. Las migraciones nuevas deben ser compatibles con datos existentes y acompañarse de pruebas PostgreSQL.
