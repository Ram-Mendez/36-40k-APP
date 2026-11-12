# Backlog operativo — 2026-11-11
## LAB-20261111-01 · Compras con fecha límite incluidas indebidamente
where.append(" AND o.expected_on<=?"); ---- el "=" añade a la consulta resultados con la fecha limite  

## LAB-20261111-02 · La propuesta de reposición desaparece después de una entrega parcial
                SELECT l.product_id, SUM(l.quantity) AS incoming
esta linea suma toda la cantidad de productos de la linea, incluidos los ya recibidos 
y por eso la suma de 12 iniciales, mas 4 recibidos, resulta en 16, y pasa el umbral de 15 unidades
para hacer sugerencias
## LAB-20261111-03 · Mercancía deteriorada reaparece como disponible tras la inspección
onHand = Math.addExact(balance.onHand(), disposition.restockQuantity() + disposition.damagedQuantity());
la propiedad onHand estaba incluyendo de nuevo los productos dañados en el cálculo de existencias vendibles.
## LAB-20261111-04 · El saldo de factura conserva un cobro ya anulado
WHERE reversed_at IS NULL
El fallo era que la consulta seguía sumando los cobros anulados. Con WHERE reversed_at IS NULL, solo cuentan para el saldo los asientos que siguen vigente
## LAB-20261111-05 · Reutilización de referencia de recepción con contenido distinto
al buscar en base de datos si existia previamente ese recibo con el mismo identificador
se quedaba con la referencia del recibo anterior, y no la nueva
## FEATURE-20261111-01 · Compromisos abiertos de suministro por proveedor
## GIT-20261111-01 · Integrar cambios de compañeros sin perder contratos

**Tipo:** Task · **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Ramas recibidas:** `training/2026-11-11/team-a` y `training/2026-11-11/team-b`. Los commits exactos figuran en `MANIFEST.md`.

**Entrega A.** La búsqueda `GET /api/v1/suppliers?search=...` ahora encuentra además coincidencias en la dirección de email, con búsqueda literal y compatibilidad de `active`, nombre e identificador fiscal.

**Entrega B.** `GET /api/v1/billing/invoices/{id}/entries?page=0&size=...` presenta primero los asientos más recientes, sin duplicados entre páginas; conserva los asientos revertidos para auditoría.

**Resultado requerido.** Integrar ambos commits en **una nueva rama personal**, partiendo de la base declarada; utilizar **cherry-pick** como maniobra de incorporación de la entrega B. No fusionar en `main` ni reescribir ramas de entrenamiento. La feature de compromisos queda pendiente e independiente.

**Aceptación.** AC1: una búsqueda por email —incluido el carácter literal `_`— obtiene el proveedor correcto sin alterar filtros previos; AC2: tres asientos del mismo documento aparecen por ID descendente entre páginas `size=2` y `size=1`, incluidos los revertidos; AC3: se conservan rutas/permisos anteriores y se documentan commit final y pruebas efectivamente ejecutadas.

**Evidencia:** commits de compañeros publicados; validación de ejecución e integración personal pendientes.
