# 22 de noviembre · 10 incidencias de Java y PostgreSQL

Empieza por el **01**. Son casos independientes. Cada uno tiene una petición que reproduce el síntoma y al menos un control que debe seguir funcionando después del arreglo.

## Abrir el primer caso

Desde tu carpeta habitual `C:\Users\balar\Desktop\36-40k-APP`, con las aplicaciones detenidas, ejecuta estos comandos en PowerShell.

```powershell
git fetch origin
git switch codex/training-2026-11-22
docker compose -f compose.lab.yaml up -d --wait
```

Arranca **Shipping APP** y **Backend APP** en Debug. Con el mismo Node que usas para el frontend, prepara el caso 01.

```powershell
node scripts/lab-22.mjs 01 --preparar
```

Abre `target/lab-22/01.http` en IntelliJ. Ejecuta el login y después las peticiones de arriba abajo. El archivo ya lleva tus IDs, fechas y cuerpos JSON. También puedes copiarlos a Swagger en el puerto **38088**. Para el 10 hay instrucciones de bloqueo al principio del archivo.

Después de cambiar el código, reinicia **Backend APP** y comprueba el caso con este comando.

```powershell
node scripts/lab-22.mjs 01
```

Cambia `01` por el número que quieras. El comprobador crea datos nuevos en cada ejecución e indica el valor esperado y el obtenido. **PASS** significa que pasan el caso y sus controles. **FAIL** indica una diferencia de comportamiento. **ERROR** indica que no se pudo ejecutar la comprobación. Para comprobar la tanda completa, usa `node scripts/lab-22.mjs todos`.

**Verificación del 22/11.** Con Java 21, Backend APP, Shipping APP y PostgreSQL 16.13, el mismo comprobador dio 10 PASS en `main` (`e741031`) y 10 FAIL en esta rama. Se reprodujeron los síntomas descritos y se generaron los diez archivos HTTP con datos de práctica.

## 01 · Al pedir un pedido por página desaparece una línea

`GET /api/v1/orders` en `OrderController`. Un cliente tiene dos borradores. El primero contiene dos productos por un total de 20 € y el segundo está vacío.

Compara el detalle del primero con la página 0 del listado ordenado por fecha ascendente. Con `size=1` el total sigue siendo 20 €, pero solo ves un producto. Con `size=3` vuelven a aparecer los dos.

**PASS:** el primer pedido trae las mismas líneas, cantidades y precios con ambos tamaños. Con `size=1` hay dos páginas y la segunda contiene el pedido vacío.

## 02 · La cabecera de una devolución ofrece 56 € por mercancía de 18,50 €

`POST /api/v1/returns` en `ReturnController`. Se vendieron cuatro unidades de P a 12,50 € y dos de Q a 3 €. El cliente devuelve **una de P y las dos de Q**.

En la devolución, compara `creditAmount` de la cabecera con `lines[].creditAmount`. Consulta después el listado de devoluciones del pedido para comprobar qué importe quedó guardado.

**PASS:** la cabecera indica 18,50 € y las líneas indican 12,50 € y 6 €. Una nueva solicitud de cuatro P devuelve `409 RETURN_QUANTITY_EXCEEDED`, porque solo quedan tres por devolver.

## 03 · Descargar cuatro cajas hace subir el stock previsto

`GET /api/v1/replenishment/proposals?productId=…` en `ReplenishmentController`. Hay una compra enviada por 10 unidades y otra de 20 en borrador. El objetivo es 25.

Lee la propuesta, registra una recepción de 4 y vuelve a leerla. El almacén cuenta 4 y al proveedor le faltan 6. En la rama de práctica, la previsión sube de 10 a 14 sin que se haya comprado nada más.

**PASS:** después de la recepción, `available=4`, `incoming=6`, `projected=10` y `suggestedQuantity=15`. Las 20 unidades en borrador quedan fuera de ambos cálculos.

## 04 · Reenviar el mismo pago de 12,50 € devuelve conflicto

`POST /api/v1/billing/invoices/{id}/payments` en `BillingController`. La factura es de 47,50 € y ya tiene un pago de 12,50 €. El archivo contiene el cuerpo original, con el mismo UUID y el número JSON `12.5`.

Reenvíalo tal cual. Luego conserva el UUID y envía `12.51`. La rama de práctica devuelve el mismo conflicto en ambos casos.

**PASS:** el reenvío exacto responde 200 con el ID del primer apunte. Cambiar un céntimo responde `409 IDEMPOTENCY_CONFLICT`. Quedan un apunte, 12,50 € cobrados y 35 € pendientes.

## 05 · Una deuda de 61 días aparece en un tramo demasiado reciente

`GET /api/v1/billing/receivables` en `BillingController`. Hay una sola factura abierta de 17,50 €. El archivo calcula las fechas de consulta a partir de su vencimiento.

Consulta el día del vencimiento y después a 30, 31, 60 y 61 días. Sigue el tramo con `invoiceCount=1`. El importe total puede cuadrar incluso cuando la antigüedad está mal.

**PASS:** el día de vencimiento cae en `NOT_DUE`. Los días 1–30, 31–60 y 61–90 caen en sus respectivos tramos. El día 91 cae en `OVER_90`. Cada vez hay una sola factura por 17,50 €.

## 06 · Confirmar falla, pero quedan dos unidades reservadas

`POST /api/v1/orders/{id}/confirm` en `OrderController`. Un borrador pide dos unidades de A y una de B. B se desactivó después de añadirlo. A tiene cinco unidades y ninguna reserva previa.

Confirma. Tras el `409 PRODUCT_UNAVAILABLE`, lee el pedido, el saldo de A y sus movimientos **antes** de retirar B. La rama deja el pedido en borrador y compromete dos unidades de A.

**PASS:** tras el 409, el pedido sigue en `DRAFT`, A tiene cero reservas y el pedido no ha generado movimientos. Al retirar B y confirmar de nuevo, se reservan exactamente dos unidades de A.

## 07 · La compra se cierra cuando llega el primer producto

`POST /api/v1/purchase-orders/{id}/receipts` en `PurchaseOrderController`. La compra tiene dos líneas, dos unidades de A y tres de B. Llegan en albaranes distintos.

Recibe A completo y lee la compra. La línea B conserva tres unidades pendientes, pero la cabecera dice `RECEIVED` y rechaza el segundo albarán.

**PASS:** tras recibir A, la compra queda `PARTIALLY_RECEIVED`. Recibir B responde 200. Solo entonces queda `RECEIVED`, con dos albaranes y saldos físicos de dos y tres unidades.

## 08 · Una factura antigua cambia cuando el cliente se muda

`GET /api/v1/billing/invoices/{id}` en `BillingController`. La primera factura se emitió con «Calle Olmo 4». Hay otro pedido completado, aún sin facturar.

Lee la primera factura, cambia la dirección del cliente a «Calle Roble 9» y vuelve a leer esa misma factura. Después emite la segunda y compara ambas en el listado del cliente.

**PASS:** la ficha actual y la segunda factura muestran «Calle Roble 9». La primera conserva «Calle Olmo 4» tanto en el detalle como en el listado.

## 09 · La propuesta muestra un proveedor 0 que nunca se asignó

`GET /api/v1/replenishment/proposals?productId=…` en `ReplenishmentController`. Compara tres productos con stock bajo. Uno carece de proveedor, otro tiene proveedor activo y el tercero conserva un proveedor desactivado.

Lee la política del primero y después las tres propuestas. En la primera, la política muestra `preferredSupplierId=null`, pero la propuesta muestra `0` y atribuye el bloqueo a una baja.

**PASS:** sin proveedor, el ID y `supplierActive` son `null` y el motivo es `NO_PREFERRED_SUPPLIER`. Con proveedor activo, `purchasable=true`. Con el desactivado, se conserva su ID y el motivo es `SUPPLIER_INACTIVE`.

## 10 · Una confirmación falla mientras otra transacción tiene la fila de stock

`POST /api/v1/orders/{id}/confirm` en `OrderController`. Hay cinco unidades. El primer pedido necesita cuatro. Una segunda sesión mantiene la fila de ese producto bloqueada durante la confirmación, sin cambiar cantidades.

El comprobador coordina ambas sesiones y observa el bloqueo en PostgreSQL. Para hacerlo a mano, usa el SQL con el ID que aparece en `10.http`. Abre la sesión desde la raíz del proyecto.

```powershell
docker compose -f compose.lab.yaml exec postgres psql -U lab_20261117 -d lab_20261117
```

Envía la confirmación mientras la transacción sigue abierta. Ejecuta `COMMIT;` en la consola SQL y observa la respuesta de la **petición original**. En la rama de práctica falla antes de liberar la fila y el mismo pedido sí se confirma al reintentarlo después.

**PASS:** PostgreSQL registra la espera y la petición original responde 200 después del `COMMIT`. Quedan cinco unidades físicas, cuatro reservadas, una disponible y un solo movimiento de reserva. El segundo pedido, de dos unidades, sí recibe `409 INSUFFICIENT_STOCK`.
