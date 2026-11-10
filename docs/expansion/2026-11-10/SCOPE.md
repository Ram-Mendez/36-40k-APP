# Ampliación del dominio de operaciones

Fecha: 10/11/2026. Base inspeccionada: `48a5b99440e4e6f3fbe125c822209d3499822b25`.

## Objetivo y entrega

La repetición de ejercicios se aborda con más procesos de negocio y un registro de escenarios. Esta entrega amplía una sola vez la aplicación sana; los lotes posteriores parten de ella. No contiene un segundo lote de errores deliberados para el 10/11, otro conflicto Git ni otra notificación por correo.

Los contratos HTTP del núcleo y shipping pasan de **36 a 71** (aproximadamente 1,97 veces); la tabla siguiente enumera diez capacidades anteriores y diez añadidas. El recuento incluye consultas y acciones HTTP, no el transportista de ejemplo en `infra/carrier`. Sirve como medida de superficie, junto a las reglas y pruebas; no representa una medida de complejidad, productividad o dominio del alumno.

| Capacidad conservada | Nueva capacidad con reglas propias |
|---|---|
| Clientes, edición versionada y baja lógica | Proveedores, plazos de suministro y baja lógica |
| Catálogo, precios, búsqueda y versión | Compras con costes pactados y compromiso de suministro |
| Ajustes de inventario y disponible | Recepciones parciales y comprobantes idempotentes |
| Borradores de venta y precio por línea | Solicitud de devolución y límite acumulado sobre lo vendido |
| Confirmación y reserva de existencias | Aprobación/rechazo versionados de devoluciones |
| Cancelación y liberación de reservas | Inspección recuperable/dañado, reposición y abono |
| Expedición y cotización REST | Emisión de factura operativa con datos históricos |
| Informes de ventas y stock | Cobros, reembolsos y reversión auditada de asientos |
| Sesiones, rotación y revocación | Saldos, créditos y antigüedad de deuda |
| Usuarios, permisos y auditoría | Políticas de reposición y propuestas que incluyen compras pendientes |

Nuevos flujos disponibles mediante API y Swagger. Sus criterios de aceptación están en [TICKETS.md](TICKETS.md), los contratos en [API.md](API.md) y la evidencia ejecutada en [VALIDATION.md](VALIDATION.md). El SHA remoto definitivo queda registrado en [`training/control`](https://github.com/Ram-Mendez/36-40k-APP/blob/training/control/docs/training/EXPANSION-BASELINE.json).

## Arquitectura

Se conservan Java 21, Spring Boot 3.5.12, Maven, PostgreSQL 16 y los dos servicios existentes. `shipping-service` conserva su contrato REST. Los módulos nuevos siguen `Controller → Service → Repository`, utilizan JDBC y la misma transacción para sus efectos de negocio.

No se necesita extraer otro microservicio en esta entrega: recepción, inspección, stock y saldo se benefician de una transacción local. La extracción obligaría a resolver consistencia entre sistemas antes de aportar un nuevo proceso operativo. Los paquetes `procurement`, `returns`, `billing` y `replenishment` delimitan las responsabilidades para poder evolucionarlas posteriormente.

| Operación | Consistencia |
|---|---|
| Recepción de compra | Bloquear compra; validar todas las líneas; bloquear saldos por `productId`; guardar comprobante, cantidades, movimientos y auditoría en una transacción |
| Devolución | Bloquear pedido de venta, después devolución y saldos ordenados; todas las solicitudes no rechazadas consumen cantidad retornable |
| Cobro/reembolso/reversión | Bloquear pedido de venta y después factura; el bloqueo del pedido se comparte con devoluciones |
| Consultas de saldo/reposición | Agregar cada relación antes del JOIN; lectura consistente con `REPEATABLE_READ` cuando hay consultas múltiples |
| Reintento | Comparar clave y contenido normalizado; mismo contenido no repite efectos; contenido distinto devuelve 409 |

Los abonos incluyen unidades aceptadas recuperables o dañadas al precio vendido. El transporte no se abona. Las facturas son un registro operativo en EUR: no incorporan un motor tributario, facturación fiscal ni llamadas a una pasarela bancaria. Registrar un cobro o reembolso crea un asiento local; no mueve dinero externo.

## Migraciones y compatibilidad

Se añaden V5 (compras), V6 (devoluciones), V7 (facturación) y V8 (reposición). V1–V4 conservan su contenido. V5 amplía los tipos de movimiento con `RECEIPT` y `RETURN`; mantiene los existentes y las restricciones de cantidades. Las tablas nuevas no sustituyen datos actuales y no requieren borrar volúmenes.

La base de la mañana contenía una búsqueda literal de `_` que añadía un `%` no solicitado y una integración de ordenación que conservaba `sku` pero omitía `active`. Se restauran ambos contratos y se conservan todas las pruebas anteriores; se añade una regresión conjunta de SKU/estado/paginación.

## Preparación del próximo lote

La ampliación no se repite cada noche. `training/control` registra su SHA y validación, un ledger de escenarios anteriores y una plantilla de tickets. Las próximas incidencias deben repartir cinco casos entre al menos tres áreas, con un máximo de dos por área, y contrastar mecanismo/invariante con los últimos diez lotes. Cambiar nombres, datos o endpoint no convierte un escenario en nuevo.

En los cinco primeros lotes posteriores, al menos dos incidencias deben utilizar capacidades nuevas ya validadas. Se mantiene la dificultad de una medio-baja, tres medias y una medio-alta acotada. La ampliación no aumenta el número diario de incidencias.

## Fundamento técnico y de documentación

Fuentes oficiales consultadas el 10/11/2026; son referencias técnicas, no nuevos productos contabilizados en el corpus:

- [PostgreSQL 16: SELECT y cláusulas de bloqueo](https://www.postgresql.org/docs/16/sql-select.html): selección explícita de filas y alcance de `FOR UPDATE`.
- [Spring Framework: transacciones declarativas](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): transacción en el servicio, propagación y aislamiento.
- [Spring Framework: reglas de rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html): los rechazos de negocio usan excepciones no comprobadas y no se ocultan antes del rollback.
- [Atlassian: informes de bugs](https://www.atlassian.com/software/jira/templates/bug-report) y [criterios de aceptación](https://www.atlassian.com/work-management/project-management/acceptance-criteria): datos reproducibles y resultados verificables.
- [GitHub: formularios de incidencias](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms): plantillas versionadas en el repositorio.
