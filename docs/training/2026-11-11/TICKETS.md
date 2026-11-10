# Backlog operativo — 2026-11-11

**Aplicación:** Business Operations B2B · Java 21 / Spring Boot 3.5.12 / PostgreSQL 16. **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Rama:** `training/2026-11-11/incidents`. Entorno de desarrollo aislado, nunca datos de producción. **Evidencia:** casos de reproducción **diseñados y pendientes de ejecución HTTP/SQL**; los comportamientos indicados como síntoma son hipótesis reproducibles del lote, no observaciones de QA.

## LAB-20261111-01 · Compras con fecha límite incluidas indebidamente

| Campo | Valor |
|---|---|
| Tipo / componente | Bug · compras / consulta |
| Prioridad / impacto | P2 · una búsqueda de pedidos pendientes por fecha incluye compromisos fuera de la ventana pedida |
| Actor | ADMIN, OPERATIONS o READONLY, con JWT válido |
| Build | `training/2026-11-11/incidents`, SHA publicado en `MANIFEST.md` |
| Evidencia | Reproducción diseñada; pendiente de ejecución |

**Condiciones.** Crear un proveedor activo de prueba con identificador fiscal único; crear dos compras del mismo proveedor con `expectedOn=D` y `expectedOn=D+1`, donde `D` es la fecha actual UTC; mantener ambas en `DRAFT`, sin realizar recepciones. No cambiar las fechas durante la consulta.

**Reproducción.** (1) Registrar y guardar los IDs de las dos compras mediante `POST /api/v1/purchase-orders` con `{ "supplierId": <S>, "expectedOn": "YYYY-MM-DD" }`. (2) Consultar `GET /api/v1/purchase-orders?supplierId=<S>&dueBefore=<D+1>&page=0&size=20&sort=expectedOn&direction=asc`. (3) Contrastar `content`, `totalElements`, `expectedOn` y los IDs de ambas compras.

**Resultado observado:** pendiente de captura; el escenario está preparado para detectar una inclusión fuera de ventana.

**Resultado esperado:** `dueBefore` es **exclusivo**. Debe incluir la compra del día `D` y excluir la de `D+1`.

**Aceptación.** AC1: el listado y el total coinciden con el límite exclusivo; AC2: con límite `D` no aparecen compras creadas en `D` o después; AC3: una consulta sin límite conserva ambas compras y la ordenación estable.

**Evidencia pendiente:** IDs reales, respuestas HTTP, SQL de comprobación y fecha UTC utilizada.

## LAB-20261111-02 · La propuesta de reposición desaparece después de una entrega parcial

| Campo | Valor |
|---|---|
| Tipo / componente | Bug · reposición / compras / inventario |
| Prioridad / impacto | P2 · planificación de compras infravalorada ante mercancía todavía pendiente |
| Actor | ADMIN u OPERATIONS para altas y recepción; READONLY puede consultar |
| Build | `training/2026-11-11/incidents`, SHA en `MANIFEST.md` |
| Evidencia | Reproducción diseñada; pendiente de ejecución |

**Condiciones.** Crear un producto activo sin existencias ni reservas y un proveedor activo; configurar `PUT /api/v1/replenishment/policies/<P>` con `{ "minimumStock":15, "targetStock":20, "preferredSupplierId":<S>, "active":true }`. Registrar compra del producto por **12** unidades y coste unitario positivo, enviarla con `POST /api/v1/purchase-orders/<PO>/submit`. Recibir **4** unidades con un `requestId` UUID nuevo.

**Reproducción.** (1) Leer `GET /api/v1/purchase-orders/<PO>` tras la recepción: registrar cantidad recibida y pendiente. (2) Leer `GET /api/v1/inventory?productId=<P>`. (3) Leer `GET /api/v1/replenishment/proposals?productId=<P>` y comparar el campo `incoming` y la presencia de una propuesta.

**Resultado observado:** pendiente de captura; escenario preparado para detectar una propuesta omitida.

**Resultado esperado:** quedan **8** unidades de entrada, hay **4** físicas disponibles y el proyectado es **12**. La política de mínimo **15** exige una propuesta por **8** unidades hasta objetivo **20**.

**Aceptación.** AC1: `incoming=8`, `available=4`, `projected=12` y `suggestedQuantity=8`; AC2: una recepción posterior de las 8 unidades elimina la necesidad cuando el proyectado ya no está bajo el mínimo; AC3: consultar propuestas no crea una compra ni modifica inventario.

**Evidencia pendiente:** respuesta de compra, existencias y propuesta antes/después de recepciones.

## LAB-20261111-03 · Mercancía deteriorada reaparece como disponible tras la inspección

| Campo | Valor |
|---|---|
| Tipo / componente | Bug · devoluciones / inspección / existencias |
| Prioridad / impacto | P1 · sobreventa potencial por stock vendible superior al recuperable |
| Actor | ADMIN u OPERATIONS; lecturas con READONLY |
| Build | `training/2026-11-11/incidents`, SHA en `MANIFEST.md` |
| Evidencia | Reproducción diseñada; pendiente de ejecución |

**Condiciones.** Producto nuevo, stock inicial **10**, pedido de **3** unidades realmente completado con servicio de envío accesible; registrar cantidad física antes de la devolución (**7**, sin otros movimientos). Crear `POST /api/v1/returns` por las **3** unidades, aprobar usando la `version` devuelta. No mezclar datos de otras pruebas.

**Reproducción.** (1) Enviar `POST /api/v1/returns/<R>/receive` con UUID nuevo, versión vigente y línea `{ "productId":<P>, "restockQuantity":1, "damagedQuantity":2 }`. (2) Consultar `GET /api/v1/returns/<R>`, el inventario filtrado por producto y `GET /api/v1/inventory/movements?productId=<P>&page=0&size=100`. (3) Contrastar lo aceptado para venta, las unidades deterioradas y el movimiento persistido.

**Resultado observado:** pendiente de captura; escenario preparado para detectar diferencia entre inspección e inventario.

**Resultado esperado:** `RECEIVED` y disposiciones **1 recuperable + 2 dañadas**, pero el físico vendible solo aumenta de **7 a 8**; movimiento `RETURN` de **1** unidad. El crédito por la devolución permanece regido por el pedido original.

**Aceptación.** AC1: inventario y movimiento registran solo la unidad recuperable; AC2: una devolución únicamente dañada no aumenta la existencia vendible; AC3: la devolución mantiene íntegros el estado y crédito previsto sin alterar otros productos.

**Evidencia pendiente:** respuesta de inspección, balances antes/después y trazabilidad de movimientos.

## LAB-20261111-04 · El saldo de factura conserva un cobro ya anulado

| Campo | Valor |
|---|---|
| Tipo / componente | Bug · facturación / libro de asientos / cuentas a cobrar |
| Prioridad / impacto | P1 · saldos de clientes y deuda cobrable incorrectos después de una reversión administrativa |
| Actor | ADMIN para reversión; ADMIN u OPERATIONS para cobro; READONLY para consultas |
| Build | `training/2026-11-11/incidents`, SHA en `MANIFEST.md` |
| Evidencia | Reproducción diseñada; pendiente de ejecución |

**Condiciones.** Pedido nuevo `COMPLETED` con factura emitida y total positivo mayor que **5,00 EUR**, sin devoluciones ni otros cobros. Registrar su `invoiceId`.

**Reproducción.** (1) Ejecutar `POST /api/v1/billing/invoices/<I>/payments` con un `requestId` UUID único, `amount=5.00` y referencia no vacía; guardar `entryId`. (2) Como ADMIN, `POST /api/v1/billing/invoices/<I>/entries/<E>/reverse` con `{ "reason":"Correccion contable de prueba" }`. (3) Comparar `GET /api/v1/billing/invoices/<I>`, el asiento por ID y `GET /api/v1/billing/receivables?customerId=<C>`.

**Resultado observado:** pendiente de captura; escenario preparado para detectar incoherencia entre asiento y saldo derivado.

**Resultado esperado:** asiento histórico reversible con `reversedAt` no nulo; `netPaid=0.00`, saldo de la factura igual a su total y deuda actual consistente con ese saldo.

**Aceptación.** AC1: la anulación conserva el asiento pero elimina sus efectos financieros; AC2: una segunda anulación con la misma razón es inocua y no altera el saldo; AC3: cobros activos posteriores siguen reduciendo el saldo de forma normal.

**Evidencia pendiente:** registro del cobro, reversión, factura y cuentas a cobrar en el mismo entorno.

## LAB-20261111-05 · Reutilización de referencia de recepción con contenido distinto

| Campo | Valor |
|---|---|
| Tipo / componente | Bug · compras / recepción parcial / contrato de reintentos |
| Prioridad / impacto | P1 · una operación de recepción responde como aceptada pese a cambiar los datos asociados a su referencia de solicitud |
| Actor | ADMIN u OPERATIONS, con token válido |
| Build | `training/2026-11-11/incidents`, SHA en `MANIFEST.md` |
| Evidencia | Reproducción diseñada; pendiente de ejecución |

**Condiciones.** Compra `SUBMITTED` nueva por **10** unidades de un solo producto y stock inicial conocido; crear un UUID `U`. Servicio principal con PostgreSQL real de pruebas. No usar recepción de otro pedido.

**Reproducción.** (1) `POST /api/v1/purchase-orders/<PO>/receipts` con `{ "requestId":"<U>", "deliveryReference":"ALBARAN-A", "lines":[{"productId":<P>,"quantity":3}] }`. Guardar ID y respuesta. (2) Repetir `POST` con el **mismo U**, pero `deliveryReference="ALBARAN-B"` y `quantity=4`. (3) Leer la compra, sus comprobantes y stock para contrastar la respuesta y el efecto acumulado.

**Resultado observado:** pendiente de captura; se evaluarán respuesta HTTP, código de negocio y efectos persistidos.

**Resultado esperado:** primera operación aceptada; la segunda responde `409` con `code=IDEMPOTENCY_CONFLICT`. El comprobante original, el stock y las **7** unidades pendientes no cambian tras el rechazo.

**Aceptación.** AC1: clave ya utilizada con contenido diferente da conflicto inequívoco, no aceptación; AC2: reenvío exacto de la primera solicitud devuelve el comprobante original sin modificar stock; AC3: una nueva clave con una segunda recepción válida de hasta 7 unidades sí puede avanzar la compra.

**Evidencia pendiente:** respuesta inicial y del reintento, estado de compra, lista de comprobantes y balance físico.

---

## FEATURE-20261111-01 · Compromisos abiertos de suministro por proveedor

**Tipo:** Story · **Área:** compras, compromisos y tesorería · **Prioridad:** P2 · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Actor:** ADMIN, OPERATIONS o READONLY, sin nuevos permisos de escritura.

**Necesidad.** El responsable de compras necesita consultar cuánto queda pendiente de recibir de un proveedor, sin sumar compras en borrador, canceladas o ya entregadas.

**Alcance.** Incorporar una **consulta de solo lectura** `GET /api/v1/suppliers/{supplierId}/open-commitments`. Respuesta estable con `supplierId`, `purchaseOrderCount` (número de órdenes con saldo pendiente), `outstandingUnits` (suma de unidades pendientes), `outstandingValue` (importe de unidades pendientes al coste pactado), `earliestExpectedOn` (fecha mínima entre compras abiertas o `null`). Considerar compras `SUBMITTED` y `PARTIALLY_RECEIVED`; excluir `DRAFT`, `CANCELLED`, `RECEIVED`. El proveedor puede estar desactivado si los compromisos son previos. No generar nuevas compras ni cambiar facturas. Evitar multiplicación de cabeceras por líneas al contar.

**Aceptación.** AC1: dos compras con dos líneas y una recepción parcial producen cantidades, valor y recuento correctos (en decimal, sin redondeo arbitrario); AC2: proveedor válido sin compras abiertas devuelve cero y fecha `null`; proveedor inexistente da `404` y READONLY no recibe `403`; AC3: tras recepción final, el compromiso desaparece sin afectar endpoints de compras o reposición. La implementación y pruebas del contrato corresponden al desarrollador que recibe la tarea.

## GIT-20261111-01 · Integrar cambios de compañeros sin perder contratos

**Tipo:** Task · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Ramas recibidas:** `training/2026-11-11/team-a` y `training/2026-11-11/team-b`. Los commits exactos figuran en `MANIFEST.md`.

**Entrega A.** La búsqueda `GET /api/v1/suppliers?search=...` ahora encuentra además coincidencias en la dirección de email, con búsqueda literal y compatibilidad de `active`, nombre e identificador fiscal.

**Entrega B.** `GET /api/v1/billing/invoices/{id}/entries?page=0&size=...` presenta primero los asientos más recientes, sin duplicados entre páginas; conserva los asientos revertidos para auditoría.

**Resultado requerido.** Integrar ambos commits en **una nueva rama personal**, partiendo de la base declarada; utilizar **cherry-pick** como maniobra de incorporación de la entrega B. No fusionar en `main` ni reescribir ramas de entrenamiento. La feature de compromisos queda pendiente e independiente.

**Aceptación.** AC1: una búsqueda por email —incluido el carácter literal `_`— obtiene el proveedor correcto sin alterar filtros previos; AC2: tres asientos del mismo documento aparecen por ID descendente entre páginas `size=2` y `size=1`, incluidos los revertidos; AC3: se conservan rutas/permisos anteriores y se documentan commit final y pruebas efectivamente ejecutadas.

**Evidencia:** commits de compañeros publicados; validación de ejecución e integración personal pendientes.
