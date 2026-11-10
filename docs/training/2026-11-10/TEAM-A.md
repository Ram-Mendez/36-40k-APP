## CAT-20261110-A — Ordenación por SKU

**Prioridad:** P2

**Solicitud:** El listado de catálogo debe aceptar `sort=sku` en las mismas condiciones que el resto de ordenaciones permitidas.

**Aceptación:** `GET /api/v1/products?sort=sku&direction=asc&page=0&size=20` devuelve el catálogo ordenado por SKU ascendente; con `direction=desc`, descendente. Conserva filtros, paginación y orden determinista ante empates; no habilita nombres arbitrarios de columna.

**Estado:** Cambio propuesto en la rama correspondiente; no hay verificación ejecutada.
