# Incidentes de debugging — primera ronda

Estos cinco incidentes se conservan en `training/5-debugging-bugs-v2`. Los incidentes nuevos, INC-006 a INC-010, están en `DEBUGGING-CHALLENGES-V2.md`.

Usa exclusivamente el entorno de entrenamiento del nuevo worktree. Las rutas siguientes son relativas a su backend. Autentícate con un usuario ADMIN u OPERATIONS y envía su access token mediante `Authorization: Bearer <token>`. Las lecturas también pueden realizarse con un usuario READONLY, salvo las rutas administrativas.

En cada ejecución sustituye `R` por un sufijo alfanumérico único, por ejemplo `V21105A`. Los identificadores entre `<...>` son los números devueltos por la API: no son texto literal que debas enviar. Crea datos nuevos para cada escenario y envía cada petición de modificación exactamente una vez, salvo donde el ticket indique lo contrario.

## INC-001 — Una cancelación reduce las existencias físicas

**Dificultad:** media.

**Síntoma:** después de cancelar un pedido confirmado que no se ha enviado, el almacén tiene menos existencias físicas que antes de confirmarlo.

**Endpoint afectado:** `POST /api/v1/orders/{id}/cancel`.

**Reproducción:**

1. Crea un cliente con `POST /api/v1/customers` y guarda su ID como `<C1>`:
   ```json
   {"name":"INC001 Customer","taxId":"B1-R","email":"inc001@example.test","address":"1 Test Street"}
   ```
2. Crea un producto con `POST /api/v1/products` y guarda su ID como `<P1>`:
   ```json
   {"sku":"B1-R","name":"INC001 Product","unitPrice":10.00}
   ```
3. Envía `POST /api/v1/inventory/<P1>/adjustments`:
   ```json
   {"quantity":20,"reason":"INC001 initial stock"}
   ```
4. Envía `POST /api/v1/orders` con `{"customerId":<C1>}` y guarda el ID como `<O1>`.
5. Envía `PUT /api/v1/orders/<O1>/lines/<P1>` con `{"quantity":6}`.
6. Envía `POST /api/v1/orders/<O1>/confirm`, sin cuerpo. Comprueba que el pedido está `CONFIRMED` y que el inventario muestra `onHand=20`, `reserved=6`, `available=14`.
7. Envía `POST /api/v1/orders/<O1>/cancel`, sin cuerpo.
8. Consulta `GET /api/v1/orders/<O1>`, `GET /api/v1/inventory?productId=<P1>` y `GET /api/v1/inventory/movements?productId=<P1>&page=0&size=100`.

**Resultado esperado:** pedido `CANCELLED`; `onHand=20`, `reserved=0`, `available=20`. Cancelar no representa una salida física de mercancía.

**Resultado incorrecto:** el pedido queda `CANCELLED`, pero el producto muestra `onHand=14`, `reserved=0` y `available=14`.

## INC-002 — Un pedido se repite al pasar a la siguiente página

**Dificultad:** media.

**Síntoma:** un listado estable muestra un pedido en dos páginas consecutivas.

**Endpoint afectado:** `GET /api/v1/orders`.

**Reproducción:**

1. Crea un cliente nuevo con `POST /api/v1/customers` y guarda su ID como `<C2>`:
   ```json
   {"name":"INC002 Customer","taxId":"B2-R","email":"inc002@example.test","address":"2 Test Street"}
   ```
2. Envía tres peticiones `POST /api/v1/orders`, cada una con `{"customerId":<C2>}`. Guarda los IDs en orden de creación como `<O21>`, `<O22>` y `<O23>`.
3. Sin crear, editar ni eliminar pedidos durante la consulta, envía:
   ```http
   GET /api/v1/orders?customerId=<C2>&page=0&size=2&sort=createdAt,asc
   GET /api/v1/orders?customerId=<C2>&page=1&size=2&sort=createdAt,asc
   ```
4. Compara los IDs de `content`, el número de elementos y los totales de ambas respuestas.

**Resultado esperado:** primera página con `<O21>` y `<O22>`; segunda página solo con `<O23>`; `totalElements=3`.

**Resultado incorrecto:** `<O22>` vuelve a aparecer en la segunda página. El total de elementos continúa siendo 3.

## INC-003 — Un SKU duplicado produce un error interno

**Dificultad:** media.

**Síntoma:** una duplicación de datos conocida se presenta al consumidor como un fallo interno del servidor.

**Endpoint afectado:** `POST /api/v1/products`.

**Reproducción:**

1. Elige un SKU nuevo y envía:
   ```http
   POST /api/v1/products
   ```
   ```json
   {"sku":"B3-R","name":"INC003 Product","unitPrice":10.00}
   ```
2. Comprueba que responde `201` y guarda el ID.
3. Repite el POST con exactamente el mismo SKU:
   ```json
   {"sku":"B3-R","name":"INC003 Duplicate","unitPrice":12.00}
   ```
4. Guarda el status HTTP, `code` y `requestId`. Consulta `GET /api/v1/products?search=B3-R` para comprobar cuántos productos existen.

**Resultado esperado:** `409 Conflict`, `code=DATA_CONFLICT` y un único producto persistido.

**Resultado incorrecto:** `500 Internal Server Error`, manteniendo `code=DATA_CONFLICT`. La duplicación no llega a persistirse.

## INC-004 — Una confirmación rechazada deja stock reservado para un borrador

**Dificultad:** alta.

**Síntoma:** la confirmación es rechazada, pero algunas unidades dejan de estar disponibles para otros pedidos. El pedido afectado permanece en borrador.

**Endpoint afectado:** `POST /api/v1/orders/{id}/confirm`.

**Reproducción:**

1. Crea un cliente con `POST /api/v1/customers` y guarda su ID como `<C4>`:
   ```json
   {"name":"INC004 Customer","taxId":"B4-R","email":"inc004@example.test","address":"4 Test Street"}
   ```
2. Crea dos productos **en este orden**, mediante dos POST a `/api/v1/products`. Guarda sus IDs como `<A>` y `<B>`:
   ```json
   {"sku":"B4-A-R","name":"INC004 Product A","unitPrice":10.00}
   ```
   ```json
   {"sku":"B4-B-R","name":"INC004 Product B","unitPrice":10.00}
   ```
3. Envía un POST a `/api/v1/inventory/<A>/adjustments` y otro a `/api/v1/inventory/<B>/adjustments`, ambos con:
   ```json
   {"quantity":10,"reason":"INC004 initial stock"}
   ```
4. Envía `POST /api/v1/orders` con `{"customerId":<C4>}` y guarda el ID como `<O4>`.
5. Envía `PUT /api/v1/orders/<O4>/lines/<A>` con `{"quantity":3}`, después `PUT /api/v1/orders/<O4>/lines/<B>` con `{"quantity":2}`. Comprueba que aparecen ambos productos en las líneas.
6. Desactiva B con `DELETE /api/v1/products/<B>`; debe responder `204`.
7. Envía `POST /api/v1/orders/<O4>/confirm` **una sola vez**, sin cuerpo. Guarda el rechazo `409` con `code=PRODUCT_UNAVAILABLE`.
8. Consulta `GET /api/v1/orders/<O4>`, `GET /api/v1/inventory?productId=<A>` y `GET /api/v1/inventory?productId=<B>`.
9. Consulta `GET /api/v1/inventory/movements?productId=<A>&page=0&size=100` y busca movimientos asociados a `<O4>`.

**Resultado esperado:** pedido `DRAFT`; ambos productos conservan `onHand=10`, `reserved=0`, `available=10`. El intento rechazado no deja reservas ni movimientos de reserva.

**Resultado incorrecto:** pedido `DRAFT`, pero A queda con `onHand=10`, `reserved=3`, `available=7` y un movimiento `RESERVE` de 3 unidades asociado al pedido. B conserva `onHand=10`, `reserved=0`, `available=10`.

Para repetir este ticket crea datos nuevos. Una segunda confirmación sobre el mismo pedido no representa el mismo estado inicial.

## INC-005 — Un pedido se completa durante una caída del servicio de envío

**Dificultad:** alta.

**Síntoma:** durante una caída del servicio de cotizaciones se registran pedidos completados con coste de envío cero y se descuentan existencias.

**Endpoint afectado:** `POST /api/v1/orders/{id}/complete`.

**Reproducción:**

1. Comprueba que el servicio de envío del entorno de entrenamiento está arrancado y que su `GET /actuator/health/readiness` responde `UP`. Crea un cliente con `POST /api/v1/customers` y guarda el ID como `<C5>`:
   ```json
   {"name":"INC005 Customer","taxId":"B5-R","email":"inc005@example.test","address":"5 Test Street"}
   ```
2. Crea un producto con `POST /api/v1/products` y guarda el ID como `<P5>`:
   ```json
   {"sku":"B5-R","name":"INC005 Product","unitPrice":10.00}
   ```
3. Envía `POST /api/v1/inventory/<P5>/adjustments`:
   ```json
   {"quantity":20,"reason":"INC005 initial stock"}
   ```
4. Envía `POST /api/v1/orders` con `{"customerId":<C5>}` y guarda el ID como `<O5>`. Añade la línea con `PUT /api/v1/orders/<O5>/lines/<P5>` y `{"quantity":2}`.
5. Envía `POST /api/v1/orders/<O5>/confirm`, sin cuerpo. Comprueba `CONFIRMED`, `shippingReference=null`, `shippingCost=null` y el inventario `onHand=20`, `reserved=2`, `available=18`.
6. Detén **solo el servicio de envío de este entorno**. Comprueba que su readiness ya no responde. Mantén el núcleo y PostgreSQL arrancados.
7. Envía `POST /api/v1/orders/<O5>/complete` **una sola vez**, sin cuerpo. Guarda status HTTP, respuesta y `requestId`.
8. Consulta `GET /api/v1/orders/<O5>`, `GET /api/v1/inventory?productId=<P5>` y `GET /api/v1/inventory/movements?productId=<P5>&page=0&size=100`.
9. Vuelve a arrancar el servicio de envío y comprueba su readiness. Para repetir el ticket crea datos nuevos desde el paso 1.

**Resultado esperado:** `503 Service Unavailable` con `code=SHIPPING_UNAVAILABLE`. El pedido permanece `CONFIRMED`, sin referencia ni coste de envío; el producto mantiene `onHand=20`, `reserved=2`, `available=18`. La petición puede reintentarse cuando vuelva el servicio.

**Resultado incorrecto:** `200 OK`, pedido `COMPLETED` con referencia de envío y `shippingCost=0.00`. El producto queda con `onHand=18`, `reserved=0`, `available=18`, aunque el servicio de envío estaba parado.