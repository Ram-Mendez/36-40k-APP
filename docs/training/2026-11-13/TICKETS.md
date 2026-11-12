# Backlog de incidencias · 13/11/2026

**Sistema:** Business Operations B2B · **Stack:** Java 21 / Spring Boot 3.5.12 / PostgreSQL 16 · **Base validada de origen:** `ad23c49900c51202f8c3c965e5b153d9e925990d` · **Build del lote:** `training/2026-11-13/incidents`, commit de código `5b354475c8ce3b777c6272404f812a71395fca80`.

**Estado de evidencia común:** reproducciones diseñadas, **pendientes de ejecutar**; no se atribuyen a QA real. Realizar operaciones con datos sintéticos y entorno aislado. Mutaciones con ADMIN u OPERATIONS; READONLY exclusivamente cuando se indique.

## LAB-20261113-01 · Plazo previsto de compra al omitir la fecha

**Tipo/componente:** incidencia · compras/proveedores · **Prioridad:** P2 · **Actor:** ADMIN u OPERATIONS.

**Precondiciones:** proveedor A activo con `leadTimeDays=4`; proveedor B activo con `leadTimeDays=0`; fecha del servidor calculada en UTC. Identificadores fiscales independientes.

**Reproducción mínima:**
1. Crear compra para A mediante `POST /api/v1/purchase-orders` con `supplierId=A`, sin `expectedOn`.
2. Consultar compra y proveedor mediante sus GET y comparar el plazo programado.
3. Repetir con B y con A enviando `expectedOn` explícita dentro de la ventana válida.

**Observado:** pendiente de ejecución; escenario previsto para comparar la fecha inicial con el plazo del proveedor.

**Esperado:** la fecha implícita de A es día UTC actual + 4 días; la de B es hoy; una fecha explícita se conserva. La creación debe responder `201`.

**Criterios:** AC1 fecha implícita coincide con el plazo del proveedor; AC2 caso sin días de tránsito y fecha explícita no se modifican; AC3 ninguna compra se crea con proveedor inactivo. **Evidencia:** tres respuestas de creación, GET de proveedores y fecha UTC usada.

## LAB-20261113-02 · Rango de reposición inválido al modificar política desactivada

**Tipo/componente:** incidencia · reposición/políticas · **Prioridad:** P2 · **Actor:** ADMIN u OPERATIONS.

**Precondiciones:** producto activo; política existente con `minimumStock=5`, `targetStock=12`, `active=false`; consultar y guardar su `version`.

**Reproducción mínima:**
1. Enviar `PUT /api/v1/replenishment/policies/{productId}` con la versión actual, `minimumStock=15`, `targetStock=10`, `active=false` y el mismo proveedor preferente.
2. Consultar la política y comprobar versión y límites.
3. Repetir con un rango válido `minimumStock=10,targetStock=15` conservando su inactividad y la versión vigente.

**Observado:** pendiente de ejecutar y de comprobar el HTTP devuelto por la validación de la aplicación y por las restricciones de PostgreSQL.

**Esperado:** primer cambio responde `400`, `code=INVALID_REPLENISHMENT_RANGE`; no cambian mínimos, objetivo ni versión. El cambio válido se acepta.

**Criterios:** AC1 no se almacena rango invertido, esté activa o no la política; AC2 error HTTP/negocio coherente, sin filtración de error SQL; AC3 cambios válidos y consultas de propuestas mantienen su contrato. **Evidencia:** respuestas HTTP y SELECT de los límites/versiones.

## LAB-20261113-03 · Reembolso cuando aún existe deuda positiva

**Tipo/componente:** incidencia · facturación/cobros · **Prioridad:** P1 · **Actor:** ADMIN u OPERATIONS; consultas de factura permitidas a READONLY.

**Precondiciones:** pedido completado con factura emitida por un total positivo, p. ej. 100,00 EUR; una devolución recibida genera crédito de 20,00 EUR; no hay pagos, reembolsos ni anulaciones en la factura.

**Reproducción mínima:**
1. Consultar `GET /api/v1/billing/invoices/{id}` y `GET /api/v1/billing/invoices/{id}/credits`; comprobar saldo positivo 80,00.
2. Solicitar `POST /api/v1/billing/invoices/{id}/refunds` con `amount=10.00`, UUID nuevo y `reference` no vacía.
3. Leer factura, `entries` y cuentas a cobrar; comparar saldo y asientos con el estado anterior.

**Observado:** pendiente de captura.

**Esperado:** `409`, `code=REFUND_EXCEEDS_CREDIT`; no debe generarse salida de caja si aún hay deuda positiva. Un escenario independiente con crédito neto real sí permite reembolso hasta el importe acreditado.

**Criterios:** AC1 rechazo sin asiento ni mutación del saldo; AC2 frontera de crédito neto cero y positivo correctamente diferenciada; AC3 pagos ordinarios siguen reduciendo la deuda. **Evidencia:** factura, créditos, asientos y respuestas HTTP antes/después.

## LAB-20261113-04 · Ventas netas duplican el transporte

**Tipo/componente:** incidencia · informes/ventas · **Prioridad:** P2 · **Actor:** READONLY, ADMIN u OPERATIONS.

**Precondiciones:** cliente de prueba con dos pedidos completados; uno con subtotal de productos 30,00 y transporte 7,00; otro con subtotal 20,00 y transporte 0,00. No incluir pedidos pendientes en el período.

**Reproducción mínima:**
1. Consultar los dos pedidos mediante GET y guardar total de productos y `shippingCost`.
2. Consultar `GET /api/v1/reports/sales?page=0&size=20` cubriendo ambos pedidos.
3. Cotejar la fila del cliente, el número de pedidos, `netSales` y `shippingCost`; repetir con un pedido sin transporte.

**Observado:** pendiente de ejecución.

**Esperado:** `completedOrders=2`, `netSales=50.00` y `shippingCost=7.00`, sin sumar el transporte dentro de las ventas netas.

**Criterios:** AC1 ambos importes conciliados de manera independiente; AC2 pedidos sin transporte y filtro temporal conservan resultado; AC3 conteo de clientes, orden y páginas se mantienen. **Evidencia:** GET de pedidos y reporte para el mismo cliente/período.

## LAB-20261113-05 · Aprobación de devolución con versión de recurso no vigente

**Tipo/componente:** incidencia · devoluciones/control de concurrencia · **Prioridad:** P1 · **Actor:** ADMIN u OPERATIONS.

**Precondiciones:** pedido completado, solicitud de devolución `REQUESTED` no procesada; leer `GET /api/v1/returns/{id}` y registrar su `version=V` vigente. No ejecutar una recepción ni una segunda decisión entre pasos.

**Reproducción mínima:**
1. Solicitar `POST /api/v1/returns/{id}/approve` con `version=V+1` y una razón válida.
2. Volver a consultar estado y versión; contrastar con `POST /api/v1/returns/{otroId}/reject` con versión incorrecta.
3. En otra devolución nueva, aprobar usando exactamente su versión leída para verificar el flujo legítimo.

**Observado:** pendiente de ejecución.

**Esperado:** el primer intento responde `409`, `code=CONCURRENT_MODIFICATION`; el recurso sigue `REQUESTED`. Una decisión con versión vigente sí se aplica; otras solicitudes con versión no coincidente también se rechazan.

**Criterios:** AC1 precondición de versión exigida en ambas decisiones; AC2 rechazo no cambia estado, versión ni auditoría; AC3 aprobación legítima y flujo posterior de recepción conservan funcionamiento. **Evidencia:** GET antes/después, HTTP y auditoría de decisiones.

---

## FEATURE-20261113-01 · Informe de inspecciones de devolución por producto

**Tipo:** historia de negocio · **Dominio:** calidad/devoluciones · **Prioridad:** P2 · **Estado:** pendiente de implementación del desarrollador.

**Solicitud:** exponer `GET /api/v1/returns/inspection-summary?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20` para ADMIN, OPERATIONS y READONLY. Fechas UTC `from` inclusiva y `to` exclusiva, `from < to`; el límite superior no puede exceder un año respecto al inferior.

**Contrato:** una fila por producto con `productId`, `restockedUnits`, `damagedUnits` y `inspectedUnits`; sumar disposiciones solo de devoluciones `RECEIVED` cuya fecha efectiva de recepción esté dentro del intervalo. Ordenar por `inspectedUnits DESC, productId ASC`, paginación 0.. y tamaño 1..100, sin duplicar productos. No revelar otras columnas del cliente ni alterar el stock.

**Aceptación:** AC1 dos inspecciones de un producto consolidan sus recuperables/dañados; AC2 una devolución rechazada, una aprobada no recibida y una recepción en `to` no cuentan; AC3 intervalo inválido `400` y tamaño fuera de rango `400`; AC4 el endpoint existente de búsqueda de devoluciones y READONLY no mutan estado. El usuario implementa código y tests; las ramas de compañeros no resuelven esta historia.

## GIT-20261113-01 · Integrar contratos simultáneos de búsqueda de clientes

**Tipo:** tarea Git · **Prioridad:** P2 · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`.

**Ramas:** `training/2026-11-13/team-a` y `training/2026-11-13/team-b`. Ambas introducen cambios reales sobre el mismo contrato HTTP.

**Contrato A:** `GET /api/v1/customers?sort=taxId&direction=asc|desc` ofrece orden por identificador fiscal con desempate por `id`, manteniendo filtros `active` y `search`.

**Contrato B:** `GET /api/v1/customers?sort=active&direction=asc|desc` ofrece orden por estado activo con desempate por `id`, manteniendo ambos filtros.

**Entrega:** realizar una integración propia de ambas ramas mediante merge a partir de la base; si existe conflicto, preservar ambos contratos, no elegir uno descartando el otro. No modificar `main` ni las ramas del laboratorio.

**Aceptación:** AC1 A y B coexisten en el mismo build; AC2 dos páginas consecutivas con valores repetidos son estables y sin duplicados; AC3 orden previo `name` e `id`, filtros y permisos continúan; AC4 registrar SHA final y evidencia realmente ejecutada. No se entrega secuencia de comandos resuelta.
