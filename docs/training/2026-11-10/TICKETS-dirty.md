## LAB-20261110-01 · Listado de clientes con estado incoherente
el filtro active recibido se comparaba con el active del cliente en bbdd 
y al ser el mismo, no el filtro no lo traia, traia el opuesto con coincidencia en el nombre
## LAB-20261110-02 · Historial de movimientos mostrado fuera de secuencia
Cambiando el ASC por DESC en /movements para filtrar primero por los mas recientemente creados, en este caso ordenandos por ID
## LAB-20261110-03 · Facturación operativa anticipada
StringBuilder filter = new StringBuilder(" WHERE o.status IN ('COMPLETED','CONFIRMED')");
En esta linea al hacer el filtro incluyendo 'CONFIRMED', hace que una order en ese estado se cuente como completedOrders
## LAB-20261110-04 · Modificación repetida de una línea de pedido altera unidades
no he entendido bien el bug
## LAB-20261110-05 · Sesión continúa operativa después de cerrar sesión
                    jdbc.update(
                             "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp)"
                                 + " WHERE family_id=? AND used_at IS NOT NULL",
                             family);
used_at es un campo que indica si la renovacion del token ha ocurrido.
en una sesion activa donde no has renovado el token y haces logout, esta condicion no se cumple y no se revoca el token.

## Entrega adicional · CAT-20261110-A
**Solicitud de negocio:** ordenar el catálogo por SKU mediante `GET /api/v1/products?sort=sku&direction=asc|desc`. La clasificación debe ser estable y compatible con filtros existentes. Contrato completo de aceptación: `docs/training/2026-11-10/TEAM-A.md` en `training/2026-11-10/team-a`.

## Integración Git · GIT-20261110-01
**Solicitud:** integrar los cambios recibidos de `training/2026-11-10/team-a` (orden por SKU) y `training/2026-11-10/team-b` (orden por estado activo) en **una nueva rama local de integración**, conservando ambos contratos, filtros previos y comportamiento por defecto. Las dos ramas modifican un tramo común; resolver cualquier conflicto sin descartar ninguno de los requisitos. No integrar en `main` ni alterar los commits remotos del laboratorio.

**Aceptación:** la versión integrada acepta ambos valores `sort=sku` y `sort=active` con direcciones válidas, y sigue rechazando columnas arbitrarias con el error HTTP habitual. Documentar SHA final y pruebas ejecutadas, sin publicar en `main`.

La gestión de ramas, preparación del entorno y estado real de validación figuran en `MANIFEST.md` y `VALIDATION.md`.
