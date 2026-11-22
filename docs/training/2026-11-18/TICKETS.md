## 01 — La tienda quiere abrir la ficha de un producto por su SKU
## 02 — Compras necesita saber cuántas unidades faltan por recibir
## 03 — Facturación quiere una prueba automática para el último céntimo
## 04 — Logística quiere ensayar una tarifa de campaña

Prepara otra ejecución de Shipping APP para probar esta oferta en el puerto `39118`. Los pedidos de menos de 80 € pagan **4,90 €** de envío. A partir de 80 €, el envío es gratuito. En IntelliJ puedes duplicar la configuración `Shipping APP` con el nombre `Shipping Campaign` y editar sus `Program arguments`. [Shipping APP.run.xml](../../../.run/Shipping%20APP.run.xml)

**Cómo comprobarlo.** Arranca `Shipping Campaign` y ejecuta estas dos peticiones en PowerShell. El campo `cost` debe valer **4,90** en la primera y **0,00** en la segunda.

```powershell
Invoke-RestMethod 'http://localhost:39118/quotes?orderId=1&amount=79.99'
Invoke-RestMethod 'http://localhost:39118/quotes?orderId=1&amount=80.00'
```

## 05 — Atención al cliente necesita un CSV de clientes activos

Escribe un script PowerShell que consulte `GET /api/v1/customers` con el token del lab y genere `clientes-activos.csv` con las columnas `id,name,email`. El equipo lo ejecutará de nuevo cada vez que necesite una copia actualizada. [CustomerController.java](../../../src/main/java/com/acme/operations/customer/CustomerController.java)

**Cómo comprobarlo.** Empieza por `GET /api/v1/customers?active=true&page=0&size=2&sort=id&direction=asc` para que la prueba use varias páginas. El número de filas de datos del CSV debe coincidir con `totalElements`. Cada ID debe aparecer una sola vez, incluso después de ejecutar el script dos veces.
