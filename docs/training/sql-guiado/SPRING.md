# Seguir una petición del botón de Swagger al resultado SQL

Los diez bugs se reproducen en la consola SQL de `lab_20261117`, dentro de `sql_lab10`. Este recorrido opcional usa la aplicación real de esa misma base para aprender dónde frenar Java y qué mirar en cada parada.

**Pregunta de partida.** Al pedir la primera página del informe de ventas con dos clientes por página, ¿qué valores envía Java a PostgreSQL y de dónde sale `netSales`?

1. **Prepara tres paradas.** En IntelliJ pulsa en el margen junto a estas tres líneas. Debe aparecer un punto rojo en cada una.

   | Dónde | Línea que debes buscar | Qué vas a mirar |
   | --- | --- | --- |
   | [ReportController.java](../../../src/main/java/com/acme/operations/report/ReportController.java), método `sales` | `return service.sales(from, to, page, size);` | Campos recibidos de Swagger |
   | [ReportRepository.java](../../../src/main/java/com/acme/operations/report/ReportRepository.java), método `sales` | `var rows =` justo antes de `jdbc.query(` | SQL y parámetros preparados |
   | Mismo método del repositorio | `return new PageResponse<>(rows, page, size, total,` | Filas que ya devolvió PostgreSQL |

2. **Envía una sola petición.** Ejecuta **Backend APP** en modo **Debug** con su configuración habitual. En [Swagger del lab](http://localhost:38088/swagger-ui/index.html), abre **POST /api/v1/auth/login**, usa `username = admin` con la contraseña de [EMPIEZA-AQUI.md](../../../EMPIEZA-AQUI.md) y pega el `accessToken` en **Authorize**. El informe admite los roles `ADMIN`, `OPERATIONS` y `READONLY`. En **Reports → GET /api/v1/reports/sales → Try it out**, deja `from` y `to` vacíos, pon `page = 0` y `size = 2`, y pulsa **Execute**. Swagger seguirá esperando mientras Java esté parado.
3. **Comprueba la entrada.** En **Threads & Variables** mira `from = null`, `to = null`, `page = 0` y `size = 2`. Si algo difiere, revisa el campo correspondiente en Swagger. Entre esta parada y el repositorio, [ReportService.sales](../../../src/main/java/com/acme/operations/report/ReportService.java) valida el orden de las fechas cuando llegan las dos. Si quieres verlo, pon otro punto en su `if (from != null ...)`.
4. **Pulsa F9 hasta llegar a `var rows`.** Despliega `args`. El elemento 0 vale `2` y alimenta `LIMIT ?`. El elemento 1 vale `0` y alimenta `OFFSET ?`. Mira `filter.toString()` en **Evaluate Expression**. Con ambas fechas vacías, el filtro usa el estado `COMPLETED`. En este punto `total` ya contiene el número de clientes que cumplen el filtro. La consulta de las filas se ejecuta a continuación.
5. **Pulsa F9 hasta el `return`.** Despliega `rows` y abre su primer elemento si existe. Anota `customerId` y `netSales`. En el código de `jdbc.query`, la cuarta expresión del `SELECT` es `sum(o.total)` y `rs.getBigDecimal(4)` la copia a `netSales`. Pulsa **F9** para terminar la petición y compara esos campos con `content` en Swagger.

**Comprobación independiente.** En la consola SQL de la misma base, selecciona y ejecuta esta consulta. Usa las tablas reales de ventas. Compara el ID y el importe de la primera fila con lo que anotaste en Java. Si aún no hay ventas completadas, ambas consultas dan una página vacía.

```sql
SELECT o.customer_id, SUM(o.total) AS ventas
FROM public.sales_order AS o
WHERE o.status = 'COMPLETED'
GROUP BY o.customer_id
ORDER BY ventas DESC, o.customer_id
LIMIT 2 OFFSET 0;
```

**Cómo usar esto en otra tarea.** Escribe un ejemplo con una entrada concreta y el resultado que esperas. Repite esa misma entrada y busca la primera diferencia. Si cambia un parámetro, mira el controlador. Para una condición de negocio, mira el servicio. Si el importe ya es incorrecto en la consola, inspecciona las filas que utiliza la consulta. Si el importe en SQL es correcto y cambia en `rows`, mira el orden del `SELECT` y del `RowMapper`. Cambia una sola cosa y repite el ejemplo junto con un segundo caso en el límite.

## Cómo seguir un error 404

1. Abre `getBySku` en [ProductService.java](../../../src/main/java/com/acme/operations/product/ProductService.java). Pon un punto en `if (product == null)`. Para entonces ya habrá terminado la búsqueda. En [ApiExceptionHandler.java](../../../src/main/java/com/acme/operations/common/ApiExceptionHandler.java), método `business`, pon otro punto en `return response(e.getStatus(), e.getCode(), e.getMessage(), request);`.
2. En Swagger ejecuta **GET /api/v1/products/by-sku/{sku}** con `sku = LAB18-NO-EXISTE`. En la primera parada mira `sku` y `product`. El texto contiene el SKU solicitado y `product` vale `null` porque no hay una ficha con ese código.
3. Pulsa **F9**. En la segunda parada, evalúa `e.getStatus().value()` y `e.getCode()`. Deben dar `404` y `SKU_NOT_FOUND`. Pulsa **F9** y busca esos valores en la respuesta de Swagger. Para contrastar, repite con `GLOVE-100`. Esta vez `product` contiene la ficha y la petición termina con `200`.

## Cómo leer una prueba que automatiza un caso manual

Abre [BillingIntegrationTest.java](../../../src/test/java/com/acme/operations/BillingIntegrationTest.java) y busca `finalCentSettlesInvoice`. Para este recorrido basta con leer ese método de arriba abajo.

| En una prueba manual | En el método |
| --- | --- |
| Preparas una factura de 32,50 €. | `sale(2, "10.00")` prepara la venta. `issue(...)` crea la factura con los 12,50 € de envío del transportista simulado. Guarda su `invoiceId`. |
| Pagas 32,49 € y vuelves a abrir la factura. | `settle(..., "32.49")` registra el pago. `invoice(invoiceId)` vuelve a consultar la factura. Los dos `assertThat` comparan lo recibido con `0.01` y `PARTIALLY_PAID`. |
| Pagas el último céntimo y compruebas el cierre. | El segundo `settle` registra `0.01`. Los últimos `assertThat` exigen `0.00` y `SETTLED`. |

Para convertir otro ejemplo manual en un test, anota primero **qué datos preparas, qué acción ejecutas y qué valor exacto debería cambiar**. Esas tres respuestas te indican qué llamadas y comprobaciones buscar en una prueba cercana.
