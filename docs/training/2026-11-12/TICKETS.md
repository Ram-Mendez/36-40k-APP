# Incidencias y trabajo asignado · 12/11/2026
**Producto:** Business Operations B2B · **Build:** `training/2026-11-12/incidents` · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d` · Java 21, Spring Boot 3.5.12, PostgreSQL 16.
**Evidencia:** todos los escenarios son reproducciones diseñadas, **no ejecutadas**. Los criterios son contratos simulados, no incidentes atribuidos a empresas reales. Actor de mutaciones: ADMIN u OPERATIONS.

## LAB-20261112-01 — Anulación después de una recepción parcial
**Tipo:** Incidencia · **Dominio:** compras/almacén · **Prioridad:** P2.
**Síntoma/impacto:** una compra parcialmente recepcionada admite una transición comercial incompatible con el albarán y las unidades pendientes.
**Precondiciones/actuación:** crear proveedor y producto activos; compra de 10 unidades a coste 8,00; enviar mediante `POST /api/v1/purchase-orders/{id}/submit`. Registrar 3 unidades por `POST /api/v1/purchase-orders/{id}/receipts` con un `requestId` UUID y `deliveryReference` exclusivos. Verificar `PARTIALLY_RECEIVED`. Solicitar `POST /api/v1/purchase-orders/{id}/cancel` y leer compra, albarán, stock y movimientos.
**Observado:** pendiente de captura.
**Esperado:** `409`, `code=INVALID_PURCHASE_STATE`; sigue `PARTIALLY_RECEIVED`, tres unidades recibidas y siete pendientes.
**Aceptación:** AC1 cancelación incompatible rechazada; AC2 sin cambios persistidos en movimientos/albarán/saldo; AC3 cancelación de una compra `DRAFT` sin recepciones sigue funcionando. **Evidencia solicitada:** HTTP y saldos antes/después.

## LAB-20261112-02 — Nueva devolución después de rechazo de otra solicitud
**Tipo:** Incidencia · **Dominio:** devoluciones · **Prioridad:** P2.
**Síntoma/impacto:** unidades de una venta completada quedan indisponibles para solicitar devolución tras rechazarse una solicitud anterior.
**Precondiciones/actuación:** pedido `COMPLETED` con cinco unidades del producto P. Crear devolución por cuatro unidades con `POST /api/v1/returns` (UUID nuevo, `orderId`, `reason`, `lines`), rechazarla en `POST /api/v1/returns/{id}/reject` con la `version` correcta, y solicitar cinco unidades de P con un UUID diferente. Consultar ambas devoluciones y el inventario.
**Observado:** pendiente de captura.
**Esperado:** nueva devolución `REQUESTED` aceptada; una segunda solicitud por cinco mientras la primera de cuatro siga abierta debe recibir `409`, `code=RETURN_QUANTITY_EXCEEDED`.
**Aceptación:** AC1 rechazo libera capacidad; AC2 una solicitud abierta sí limita nuevas cantidades; AC3 solicitudes/rechazos no introducen mercancía ni abono recibido. **Evidencia:** HTTP, estados y balances.

## LAB-20261112-03 — Cambio de proveedor preferente hacia un proveedor inactivo
**Tipo:** Incidencia · **Dominio:** reposición · **Prioridad:** P2.
**Síntoma/impacto:** una política de reposición existente se reasigna a una empresa no disponible para nuevas compras.
**Precondiciones/actuación:** crear proveedores activos A y B, producto P; crear `PUT /api/v1/replenishment/policies/{P}` con `minimumStock=5`, `targetStock=15`, `preferredSupplierId=A`, `active=true` (sin `version`). Desactivar B, actualizar la política con `preferredSupplierId=B` y la versión leída; consultar nuevamente política/propuestas.
**Observado:** pendiente de captura.
**Esperado:** `409`, `code=SUPPLIER_UNAVAILABLE`; A, los umbrales y la versión no cambian.
**Aceptación:** AC1 reasignación inválida rechazada; AC2 no hay escritura parcial; AC3 actualizar solo umbrales conservando un proveedor histórico posteriormente inactivo sigue siendo posible. **Evidencia:** estados/versiones antes/después.

## LAB-20261112-04 — Recuento de reservas en el informe de stock bajo
**Tipo:** Incidencia · **Dominio:** informes/inventario · **Prioridad:** P2.
**Síntoma/impacto:** el contador `confirmedOrders` de un producto no coincide con sus reservas reales.
**Precondiciones/actuación:** crear P y Q, con existencias 5 y 0 respectivamente. Confirmar pedido de 2 unidades de P sin Q; consultar `GET /api/v1/reports/low-stock?threshold=10&page=0&size=20` y cotejar reservas/saldos por producto.
**Observado:** pendiente de captura.
**Esperado:** P tiene `confirmedOrders=1`, Q `confirmedOrders=0`. Los campos `onHand`, `reserved`, `available` conservan sus valores propios.
**Aceptación:** AC1 contador por producto, no por informe completo; AC2 paginación y totales coherentes; AC3 otra reserva para Q afecta solo a Q. **Evidencia:** informe e IDs de reservas.

## LAB-20261112-05 — Dos cobros simultáneos superan el saldo disponible
**Tipo:** Incidencia · **Dominio:** facturación/concurrencia · **Prioridad:** P1.
**Síntoma/impacto:** operaciones de cobro concurrentes aceptables individualmente pueden superar el saldo pendiente al coexistir.
**Precondiciones/actuación:** factura `OPEN` por 100,00 EUR ligada a pedido completado; dos llamadas a `POST /api/v1/billing/invoices/{id}/payments` de 70,00 EUR con UUID y referencias distintos. Coordinar transacciones concurrentes sobre esa factura con barreras verificables, no solo `sleep`. Leer factura y asientos; repetir secuencialmente en factura nueva como control.
**Observado:** pendiente de ejecución concurrente.
**Esperado:** solo un cobro aceptado; otro `409`, `code=PAYMENT_EXCEEDS_BALANCE`; saldo final 30,00 y un único asiento no revertido de 70,00.
**Aceptación:** AC1 balance nunca negativo por esta carrera; AC2 asiento y balance persistidos coherentes; AC3 comportamiento secuencial y reintento idéntico se mantienen. **Evidencia:** dos respuestas correlacionadas y ledger.

---
## FEATURE-20261112-01 — Generación de borrador de compra desde propuestas
**Tipo:** Story · **Dominio:** compras/reposición · **Prioridad:** P2 · **Estado:** por implementar.
**Solicitud:** `POST /api/v1/replenishment/purchase-drafts` (ADMIN/OPERATIONS). Entrada: `supplierId`, `expectedOn` opcional, líneas únicas `productId` y `unitCost` positivo. Usar `suggestedQuantity` de propuestas actuales, vinculadas al proveedor elegido y marcadas `purchasable`. Respuesta `201 Created`, `Location` y una única compra `DRAFT`.
**Aceptación:** AC1 dos propuestas válidas crean borrador con cantidades/costes correctos; AC2 propuesta cero, producto no aplicable o proveedor diferente se rechazan sin compra parcial; AC3 no cambia stock ni recepción; el flujo actual de envío de compras sigue vigente; READONLY no crea compras. Implementación y pruebas corresponden al desarrollador.

## GIT-20261112-01 — Integración de dos entregas
**Tipo:** Task · **Prioridad:** P2 · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Refs:** `training/2026-11-12/team-a` y `training/2026-11-12/team-b`.
**Contrato A:** `GET /api/v1/returns?sort=status,asc|desc` ordena por estado y desambigua por ID, conservando filtros y sorts anteriores.
**Contrato B:** `GET /api/v1/replenishment/proposals` prioriza mayor `suggestedQuantity`, con desempate estable; filtros y totales no cambian.
**Entrega:** integrar mediante merge las dos ramas en una NUEVA rama personal nacida de la base, preservar A y B, sin modificar `main` ni ramas ajenas.
**Aceptación:** AC1 ambos contratos conviven; AC2 resultados paginados estables y consistentes; AC3 el usuario deja SHA final y registro de las pruebas realmente ejecutadas. No se incluye receta de merge ni resolución.
