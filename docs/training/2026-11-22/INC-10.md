# INC-10 · Una confirmación falla mientras el stock está bloqueado

**Incidencia.** Hay cinco unidades de un producto. Un pedido necesita cuatro. Otra transacción tiene bloqueada la fila del stock, sin cambiar las cantidades.

**Datos preparados:** producto `26`; primer pedido `12` con cuatro unidades; segundo pedido `13` con dos unidades.

## Reproducir

1. En IntelliJ, abre una consola SQL de la conexión `lab_20261117@localhost:35690`. En esa consola ejecuta juntas estas líneas y deja la transacción abierta:

```sql
BEGIN;
SELECT product_id FROM inventory_balance WHERE product_id = 26 FOR UPDATE;
```

2. En Swagger ejecuta `POST /api/v1/orders/{id}/confirm` con `id = 12`, sin cerrar la consola SQL.
3. Vuelve a **la misma consola SQL** y ejecuta `COMMIT;`. Observa la respuesta de la petición original en Swagger.
4. Consulta `GET /api/v1/orders/{id}` con `id = 12` y `GET /api/v1/inventory` con `productId = 26`.

**Fallo observado:** la confirmación falla antes de que se libere la fila.

**Resultado esperado:** la petición original espera y, después del `COMMIT`, responde 200. El producto conserva cinco unidades físicas, cuatro reservadas y una disponible. Confirmar el pedido `13` debe responder `409 INSUFFICIENT_STOCK`.

**Para investigar:** pon un breakpoint en [OrderController.confirm](../../../src/main/java/com/acme/operations/order/OrderController.java) y sigue la confirmación hasta el acceso al inventario. Tras corregir, repite el bloqueo y la confirmación del pedido `12` si sigue en `DRAFT`; no confirmes el pedido `13` antes de repetir la prueba.

Si detienes la práctica con la transacción aún abierta, ejecuta `ROLLBACK;` en esa misma consola para liberar la fila.
