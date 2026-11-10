## CAT-20261110-B — Ordenación por estado de catálogo

**Prioridad:** P2

**Solicitud:** El listado de productos debe aceptar `sort=active` con dirección ascendente o descendente para agrupar activos e inactivos.

**Aceptación:** `GET /api/v1/products?sort=active&direction=asc&page=0&size=20` devuelve primero los registros inactivos y mantiene un desempate determinista; `desc` ordena primero los activos. Se conservan los filtros `search`, `active` y los demás órdenes conocidos.

**Estado:** Cambio propuesto en la rama correspondiente; no hay verificación ejecutada.
