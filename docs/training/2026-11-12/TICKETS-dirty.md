# Incidencias y trabajo asignado · 12/11/2026
## LAB-20261112-01 — Anulación después de una recepción parcial
## LAB-20261112-02 — Nueva devolución después de rechazo de otra solicitud
## LAB-20261112-03 — Cambio de proveedor preferente hacia un proveedor inactivo
## LAB-20261112-04 — Recuento de reservas en el informe de stock bajo
## LAB-20261112-05 — Dos cobros simultáneos superan el saldo disponible
## FEATURE-20261112-01 — Generación de borrador de compra desde propuestas
**¿Cómo pasa compras de «conviene pedir estas unidades» a tener un pedido preparado?** Imagina dos propuestas vigentes para el mismo proveedor: 6 cajas de guantes y 4 pares de botas. El responsable elige el proveedor y los dos productos e indica el coste por unidad. La aplicación toma las cantidades sugeridas de las propuestas actuales. Si los costes fueran 8 € y 12 €, el resultado sería una compra en borrador con dos líneas y un total de 96 €.

La operación termina al preparar ese único borrador. El envío al proveedor y la recepción en almacén son pasos posteriores: en este punto el stock sigue igual. Si las botas corresponden a otro proveedor o ya no procede pedirlas, se rechaza la petición completa, incluso si la línea de guantes era válida. Comprueba también que el borrador creado puede enviarse por el flujo habitual y que un usuario de solo lectura no puede crearlo.

**Tipo:** Story · **Dominio:** compras/reposición · **Prioridad:** P2 · **Estado:** por implementar.
**Solicitud:** `POST /api/v1/replenishment/purchase-drafts` (ADMIN/OPERATIONS). Entrada: `supplierId`, `expectedOn` opcional, líneas únicas `productId` y `unitCost` positivo. Usar `suggestedQuantity` de propuestas actuales, vinculadas al proveedor elegido y marcadas `purchasable`. Respuesta `201 Created`, `Location` y una única compra `DRAFT`.
**Aceptación:** AC1 dos propuestas válidas crean borrador con cantidades/costes correctos; AC2 propuesta cero, producto no aplicable o proveedor diferente se rechazan sin compra parcial; AC3 no cambia stock ni recepción; el flujo actual de envío de compras sigue vigente; READONLY no crea compras. Implementación y pruebas corresponden al desarrollador.

## GIT-20261112-01 — Integración de dos entregas

**¿Qué tienes que entregar tú, si el código lo escribieron A y B?** Una rama personal del 12/11 que reúna los dos cambios y pruebas de que ambos funcionan juntos. A cambia el orden en que se muestran las devoluciones. B cambia el orden en que compras ve las propuestas de reposición. Para este ejercicio crea la rama desde la base indicada abajo e incorpora cada rama del 12/11 mediante merge.

Prueba cada listado con datos que permitan ver el cambio. Para A, usa devoluciones de distintos estados y al menos dos con el mismo estado. Para B, usa propuestas con distintas cantidades sugeridas y dos que empaten. Recorre páginas pequeñas: deben salir primero los registros que corresponden al orden solicitado, y los empatados deben conservar un orden estable al pasar de página, sin saltos ni duplicados. Anota el SHA final de tu rama y qué resultados viste en los dos endpoints.

**Tipo:** Task · **Prioridad:** P2 · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Refs:** `training/2026-11-12/team-a` y `training/2026-11-12/team-b`.
**Contrato A:** `GET /api/v1/returns?sort=status,asc|desc` ordena por estado y desambigua por ID, conservando filtros y sorts anteriores.
**Contrato B:** `GET /api/v1/replenishment/proposals` prioriza mayor `suggestedQuantity`, con desempate estable; filtros y totales no cambian.
**Entrega:** integrar mediante merge las dos ramas en una NUEVA rama personal nacida de la base, preservar A y B, sin modificar `main` ni ramas ajenas.
**Aceptación:** AC1 ambos contratos conviven; AC2 resultados paginados estables y consistentes; AC3 el usuario deja SHA final y registro de las pruebas realmente ejecutadas. No se incluye receta de merge ni resolución.
