# SQL y Java · Ejercicios 51–60

Empieza por [51 · La búsqueda confunde un guion con un guion bajo](51.md). En IntelliJ abre la consola de `lab_20261117@localhost`, puerto `35438`. Copia el SQL del ejercicio entero y ejecútalo con **Ctrl+Enter**.

Cada guía enlaza un método real del proyecto y muestra un fragmento Java de práctica con un fallo. En los fragmentos, `mapper` representa el conversor de filas del repositorio.

Las consultas usan el esquema de práctica `sql_backend_51_60`, ya cargado en esa conexión. Si recreas la base y falta el esquema, ejecuta [00-preparar.sql](00-preparar.sql) completo en la misma consola.

| Caso | Problema | Código Java |
| --- | --- | --- |
| [51](51.md) | Búsqueda con guion bajo | `ProductService.list` |
| [52](52.md) | Total incorrecto en una página | `OrderRepository.search` |
| [53](53.md) | Una devolución rechazada ocupa unidades | `ReturnRepository.allocatedQuantity` |
| [54](54.md) | Crédito de una devolución pendiente | `BillingRepository.BALANCES` |
| [55](55.md) | Recepciones de dos compras | `ProcurementRepository.previousReceipt` |
| [56](56.md) | Eventos de auditoría mezclados | `AuditController.list` |
| [57](57.md) | Reserva con stock insuficiente | `InventoryService.reserve` |
| [58](58.md) | Propuesta que ignora compras pendientes | `ReplenishmentRepository.proposals` |
| [59](59.md) | Factura que vence hoy | `BillingRepository.receivables` |
| [60](60.md) | Paginación de pedidos y líneas | `OrderRepository.search` |

Ejecuta los SQL en la consola de práctica. Los enlaces Java te llevan al código de la aplicación.
