# Contratos de la ampliación

Todas las rutas tienen prefijo `/api/v1`. Login y Bearer JWT existentes. Lectura: ADMIN, OPERATIONS o READONLY. Escritura: ADMIN u OPERATIONS; reversión de asientos: exclusivamente ADMIN. JSON desconocido o inválido se rechaza y los errores usan `ProblemDetail` con `code` y `requestId`.

Listados paginados: índice desde cero, tamaño 1–100, orden estable y metadatos `PageResponse`. Los identificadores de los ejemplos son variables obtenidas de respuestas reales, no datos que existan necesariamente en tu base.

## Proveedores y compras — 15 operaciones

| Método y ruta | Entrada o resultado |
|---|---|
| `POST /suppliers` | `{name,taxId,email,leadTimeDays}`; 201 y Location |
| `GET /suppliers` | `search,active,page,size,sort,direction` |
| `GET /suppliers/{id}` | Datos y versión |
| `PUT /suppliers/{id}` | Campos de alta y `version` actual; 409 ante edición obsoleta |
| `DELETE /suppliers/{id}` | Baja lógica, 204 |
| `POST /purchase-orders` | `{supplierId,expectedOn?}`; fecha omitida = hoy UTC + plazo del proveedor |
| `GET /purchase-orders` | `supplierId,status,dueBefore,page,size,sort,direction`; `dueBefore` es exclusivo |
| `GET /purchase-orders/{id}` | Cabecera y líneas con recibido/pendiente |
| `PUT /purchase-orders/{id}/lines/{productId}` | `{quantity,unitCost}`; solo DRAFT |
| `DELETE /purchase-orders/{id}/lines/{productId}` | Retirar línea; solo DRAFT |
| `POST /purchase-orders/{id}/submit` | Congelar compromiso, líneas y costes; exige líneas/productos/proveedor válidos |
| `POST /purchase-orders/{id}/cancel` | Solo DRAFT o SUBMITTED sin recepciones |
| `POST /purchase-orders/{id}/receipts` | `{requestId,deliveryReference,lines:[{productId,quantity}]}` |
| `GET /purchase-orders/{id}/receipts` | Comprobantes paginados |
| `GET /purchase-orders/{id}/receipts/{receiptId}` | Comprobante de esa compra; otra compra no da acceso al recurso |

Estados: DRAFT → SUBMITTED → PARTIALLY_RECEIVED → RECEIVED. La recepción devuelve 200 tanto al registrar como al repetir exactamente un UUID. La misma clave con otro contenido da 409. Aumenta físico, mantiene reservado y conserva el coste pactado. Una baja posterior del proveedor no impide recibir una compra ya emitida.

Ordenación de proveedores: `id,name,taxId,leadTimeDays,active`. Compras: `id,createdAt,expectedOn,total,status`. Direcciones `asc`/`desc`. Cantidad por línea 1–1.000.000; hasta 100 productos por compra. Fecha explícita de llegada: hoy UTC a dos años. Costes positivos con hasta dos decimales; total máximo `999999999999.99`.

## Devoluciones — 6 operaciones

| Método y ruta | Entrada o resultado |
|---|---|
| `POST /returns` | `{orderId,requestId,reason,lines:[{productId,quantity}]}`; pedido COMPLETED |
| `GET /returns` | `orderId,status,page,size,sort`; resúmenes |
| `GET /returns/{id}` | Cabecera, versión y líneas |
| `POST /returns/{id}/approve` | `{version,reason}`; REQUESTED → APPROVED |
| `POST /returns/{id}/reject` | `{version,reason}`; REQUESTED → REJECTED |
| `POST /returns/{id}/receive` | `{requestId,version,lines:[{productId,restockQuantity,damagedQuantity}]}` |

Las solicitudes REQUESTED, APPROVED y RECEIVED cuentan contra las unidades vendidas; REJECTED libera ese cupo. El crédito propuesto se obtiene del precio original, y solo pasa a saldo contable en RECEIVED. En la recepción deben figurar exactamente todas las líneas y sumar lo solicitado: recuperable aumenta stock, dañado no. Las dos claves UUID tienen ámbitos independientes: creación por pedido y recepción por devolución. Un replay de recepción conserva la versión original del payload.

Ordenación admitida: `createdAt,asc`, `createdAt,desc`, `creditAmount,asc` o `creditAmount,desc`. No se admite recibir parcialmente una RMA; para una devolución parcial se solicita una cantidad menor que la vendida.

## Facturación y saldos — 10 operaciones

| Método y ruta | Entrada o resultado |
|---|---|
| `POST /billing/invoices` | `{orderId,dueDate}`; 201 y Location, una factura por pedido COMPLETED |
| `GET /billing/invoices` | `customerId,status,page,size`; orden vencimiento/id |
| `GET /billing/invoices/{id}` | Snapshot de cliente/importes y saldo actual |
| `POST /billing/invoices/{id}/payments` | `{requestId,amount,reference}`; cobro parcial o total |
| `POST /billing/invoices/{id}/refunds` | Mismo contrato; solo crédito reembolsable |
| `GET /billing/invoices/{id}/entries` | Asientos paginados, incluidos los revertidos |
| `GET /billing/invoices/{id}/entries/{entryId}` | Asiento perteneciente a esa factura |
| `POST /billing/invoices/{id}/entries/{entryId}/reverse` | `{reason}`; ADMIN, conserva historial |
| `GET /billing/invoices/{id}/credits` | Devoluciones recibidas que abonan el pedido |
| `GET /billing/receivables` | `customerId?,asOf?`; deuda y tramos de antigüedad |

`balance = productTotal + shippingTotal − credited − netPaid`. `netPaid` suma cobros y resta reembolsos no revertidos. Estados derivados: OPEN, PARTIALLY_PAID, SETTLED, CREDIT_DUE. El estado no es un campo editable. Créditos y pagos se agregan por separado antes de combinarse, evitando multiplicar importes.

Vencimiento de emisión: hoy UTC a 90 días. Emisión repetida con el mismo pedido/vencimiento devuelve la factura original; otros términos dan 409. UUID de asiento compartido entre cobros y reembolsos dentro de la factura: mismo contenido devuelve el asiento sin repetir efectos; otro contenido da 409. El replay de un asiento revertido no lo reactiva. No se permite revertir un cobro si los reembolsos dejarían `netPaid` negativo.

`asOf` clasifica la antigüedad **del saldo actual** y la respuesta declara `balanceBasis=CURRENT_BALANCE`; no reconstruye el historial a esa fecha. Tramos: NOT_DUE, 1_30, 31_60, 61_90 y OVER_90. Los créditos pendientes de reembolso se presentan aparte.

## Reposición — 4 operaciones

| Método y ruta | Entrada o resultado |
|---|---|
| `PUT /replenishment/policies/{productId}` | `{minimumStock,targetStock,preferredSupplierId?,active,version?}`; crear omitiendo versión, actualizar con la actual |
| `GET /replenishment/policies/{productId}` | Política actual |
| `GET /replenishment/policies` | Políticas paginadas |
| `GET /replenishment/proposals` | Necesidades paginadas con disponible, compras pendientes y cantidad sugerida |

Una propuesta se genera cuando `onHand − reserved + incoming < minimumStock`. La sugerencia es `targetStock − available − incoming`. `incoming` contiene solo unidades aún no recibidas de compras SUBMITTED/PARTIALLY_RECEIVED. Incluye productos activos sin fila de saldo y excluye productos/políticas inactivos. Un proveedor preferido inactivo impide presentar la sugerencia como ejecutable. Consultar propuestas no crea una compra.

Filtros de políticas: `productId,preferredSupplierId,active,search,page,size`. Propuestas: los mismos salvo `active`; orden estable por producto. PUT responde 200 en creación y edición. Mínimo/objetivo entre 0 y 1.000.000.000, objetivo ≥ mínimo. `purchasable` indica que hay proveedor activo; no elimina los límites de cantidad de una línea de compra. Sin proveedor se devuelve `NO_PREFERRED_SUPPLIER`; con proveedor desactivado, `SUPPLIER_INACTIVE`. El suministro ya comprometido de cualquier proveedor sigue contando en `incoming`.

## Ejecución

Con Docker disponible, la suite estándar sigue siendo `./mvnw verify` (en Windows, `mvnw.cmd verify`). Hay soporte opcional para un PostgreSQL **dedicado de pruebas** mediante `-Dtest.database.url`, `-Dtest.database.username` y `-Dtest.database.password`; al omitirlos se mantiene Testcontainers. El perfil test ejecuta migraciones y escribe fixtures: no apuntarlo a una base de trabajo.

Para recorrer el proceso completo por HTTP, consulta `scripts/smoke-expansion.py`. Recibe URL y credenciales explícitas, crea sus propios recursos y verifica compras → venta → devolución → factura → reembolso → reposición. Las rutas y resultados reales de la ejecución de esta entrega están en `VALIDATION.md`.
