# SQL y Java · Ejercicios 41–50

Empieza por [41 · Un pedido aparece sin líneas](41.md). Abre en IntelliJ la consola de `lab_20261117@localhost`, puerto `35438`. Copia el archivo SQL del ejercicio entero y ejecútalo con **Ctrl+Enter**.

Cada caso parte de un método y una consulta de esta aplicación. La guía incluye un fragmento Java de práctica con un fallo y un enlace al método real. En los fragmentos, `mapper` representa el conversor de filas del repositorio.

Las consultas usan las tablas de `sql_backend_41_50`. Sus datos ya están cargados en la conexión de práctica. Si alguna vez recreas la base y falta ese esquema, ejecuta [00-preparar.sql](00-preparar.sql) completo en esa conexión.

| Caso | Problema | Código Java |
| --- | --- | --- |
| [41](41.md) | Un pedido aparece sin líneas | `OrderRepository.lines` |
| [42](42.md) | Un pedido vacío tiene total `NULL` | `OrderRepository.recalculate` |
| [43](43.md) | Falta un producto en el inventario | `InventoryRepository.balances` |
| [44](44.md) | Un informe suma dos veces un pedido | `ReportRepository.sales` |
| [45](45.md) | Una factura pierde un pago por los ID | `BillingRepository.entry` |
| [46](46.md) | El saldo cuenta un pago anulado | `BillingRepository.BALANCES` |
| [47](47.md) | La reposición cuenta unidades recibidas | `ReplenishmentRepository.PROPOSAL_CTE` |
| [48](48.md) | Una compra completa sigue como parcial | `ProcurementRepository.updateReceiptStatus` |
| [49](49.md) | Una página repite un movimiento | `InventoryRepository.movements` |
| [50](50.md) | El informe cuenta reservas de otros productos | `ReportRepository.lowStock` |

Los datos de esta tanda se consultan en la consola de práctica. Los enlaces Java muestran el código de la aplicación y sus métodos.
