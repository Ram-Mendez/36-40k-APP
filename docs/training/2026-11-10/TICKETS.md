# Incidencias · 2026-11-10

**Sistema:** Business Operations B2B · Java 21 / Spring Boot 3.5.12 / PostgreSQL 16. **Entorno:** únicamente rama de laboratorio `training/2026-11-10/incidents`; credenciales y base de datos de desarrollo aisladas. **Estado de evidencias:** escenarios definidos; sin ejecución HTTP/SQL certificada.

## LAB-20261110-01 · Listado de clientes con estado incoherente
**Prioridad:** P2 · **Área:** CRM / consulta.

**Observación comunicada:** el filtro por estado devuelve registros que no corresponden al valor solicitado. Los equipos operativos no pueden separar con fiabilidad clientes vigentes y dados de baja.

**Reproducción solicitada:** crear dos clientes con nombres y taxId exclusivos de la prueba mediante `POST /api/v1/customers`; desactivar uno con `DELETE /api/v1/customers/{id}`. Consultar `GET /api/v1/customers?search=LAB101&active=true&page=0&size=20` y repetir con `active=false`, utilizando `LAB101` como prefijo común de los nombres.

**Aceptación:** `active=true` solo incluye clientes activos, `active=false` solo inactivos; `totalElements` coincide con el filtro y se mantienen la ordenación y paginación existentes.

**Evidencia:** pendiente de obtener respuestas HTTP y los IDs creados durante la reproducción.

## LAB-20261110-02 · Historial de movimientos mostrado fuera de secuencia
**Prioridad:** P2 · **Área:** almacén / trazabilidad.

**Observación comunicada:** los movimientos antiguos preceden a los recientes, dificultando investigar el último ajuste de un producto.

**Reproducción solicitada:** crear un producto nuevo y registrar tres ajustes consecutivos con motivos inequívocos `LAB102-A`, `LAB102-B` y `LAB102-C`, mediante `POST /api/v1/inventory/{productId}/adjustments`. Consultar `GET /api/v1/inventory/movements?productId={productId}&page=0&size=2`, seguido de `page=1`.

**Aceptación:** la primera página contiene los dos últimos movimientos por ID descendente, y la segunda el más antiguo; ningún movimiento se duplica entre páginas, `totalElements=3` para ese producto recién creado.

**Evidencia:** pendiente de capturar el listado y los IDs de movimientos reales.

## LAB-20261110-03 · Facturación operativa anticipada
**Prioridad:** P1 · **Área:** informes comerciales.

**Observación comunicada:** el resumen de ventas atribuye importes a clientes cuyos pedidos todavía no han sido completados. Se sobredimensionan las cifras operativas.

**Reproducción solicitada:** para un cliente nuevo, crear un pedido con producto activo, existencias suficientes y una línea de 2 unidades; realizar `POST /api/v1/orders/{id}/confirm`, sin completar el pedido. Consultar `GET /api/v1/reports/sales?page=0&size=20`, filtrando en el resultado por el cliente de prueba.

**Aceptación:** un pedido `CONFIRMED` no aporta ventas ni incrementa el número de pedidos completados. Solo un pedido `COMPLETED` se agrega a `netSales` y al contador correspondiente. Mantener intactos los filtros temporales y los totales paginados.

**Evidencia:** pendiente de obtener las respuestas de confirmación e informe de un entorno aislado.

## LAB-20261110-04 · Modificación repetida de una línea de pedido altera unidades
**Prioridad:** P1 · **Área:** pedidos / coherencia.

**Observación comunicada:** reenviar la misma solicitud para fijar unidades modifica de nuevo la cantidad final y el importe; un reintento de cliente deja un pedido diferente.

**Reproducción solicitada:** crear un pedido `DRAFT` de un cliente válido, elegir un producto activo y enviar dos veces `PUT /api/v1/orders/{orderId}/lines/{productId}` con cuerpo `{"quantity":3}`. Consultar `GET /api/v1/orders/{orderId}` tras cada petición.

**Aceptación:** tras ambas llamadas la línea conserva `quantity=3`, el precio originalmente pactado y el mismo importe total; no surgen reservas ni líneas duplicadas. Otros productos del pedido no cambian.

**Evidencia:** pendiente de comparar las dos representaciones HTTP de la línea y el total.

## LAB-20261110-05 · Sesión continúa operativa después de cerrar sesión
**Prioridad:** P0 · **Área:** autenticación / revocación.

**Observación comunicada:** tras una respuesta satisfactoria al cierre de sesión, las credenciales anteriores siguen autorizando peticiones protegidas. Puede persistir acceso no deseado a datos internos.

**Reproducción solicitada:** iniciar sesión en una cuenta de entrenamiento usando `POST /api/v1/auth/login` y guardar el `accessToken`. Confirmar que `GET /api/v1/products` autoriza esa identidad; ejecutar `POST /api/v1/auth/logout` con el mismo Bearer; repetir el GET con el mismo token sin iniciar sesión de nuevo.

**Aceptación:** el cierre devuelve `204`; el token anterior no puede acceder a rutas autenticadas y responde `401`. Las sesiones nuevas se mantienen operativas; no se relajan los permisos por rol.

**Evidencia:** pendiente de registrar status HTTP y `requestId` de la operación protegida posterior al cierre.

---

## Entrega adicional · CAT-20261110-A
**Solicitud de negocio:** ordenar el catálogo por SKU mediante `GET /api/v1/products?sort=sku&direction=asc|desc`. La clasificación debe ser estable y compatible con filtros existentes. Contrato completo de aceptación: `docs/training/2026-11-10/TEAM-A.md` en `training/2026-11-10/team-a`.

## Integración Git · GIT-20261110-01
**Solicitud:** integrar los cambios recibidos de `training/2026-11-10/team-a` (orden por SKU) y `training/2026-11-10/team-b` (orden por estado activo) en **una nueva rama local de integración**, conservando ambos contratos, filtros previos y comportamiento por defecto. Las dos ramas modifican un tramo común; resolver cualquier conflicto sin descartar ninguno de los requisitos. No integrar en `main` ni alterar los commits remotos del laboratorio.

**Aceptación:** la versión integrada acepta ambos valores `sort=sku` y `sort=active` con direcciones válidas, y sigue rechazando columnas arbitrarias con el error HTTP habitual. Documentar SHA final y pruebas ejecutadas, sin publicar en `main`.

La gestión de ramas, preparación del entorno y estado real de validación figuran en `MANIFEST.md` y `VALIDATION.md`.
