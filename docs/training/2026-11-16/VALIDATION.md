# Validación del batch — 16/11/2026

**Estado:** cinco defectos introducidos y reproducidos mediante pruebas. Rama de práctica local, sin publicar.

**Base:** `74ccb5e53a06af0c41fe870b8d617586c6038c5d`. **Rama:** `codex/training-2026-11-16`.

**Carpeta:** `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-16`.

## Validación inicial automatizada

Se añadieron cuatro clases de test y se ejecutaron primero contra el código de la base, antes de introducir los defectos. Se reutilizaron las pruebas existentes de cotización y cliente HTTP. Las 48 comprobaciones que selecciona el batch pasaron sin fallos, errores ni casos omitidos.

Después se introdujeron cinco cambios intencionados, uno por ticket. Se ejecutó el lanzador completo con JDK 21:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-batch-2026-11-16.ps1 -Ticket all
```

| Ticket | Comprobaciones | Base correcta | Con defecto | Síntoma verificado |
| --- | ---: | --- | --- | --- |
| 01 · Java y precios | 20 | 20 pasan | 3 fallan, 17 pasan | En 200 €, `cost=7.50`; también falla un umbral configurado distinto |
| 02 · Validación REST | 3 | 3 pasan | 1 falla, 2 pasan | Correo sin arroba obtiene 201 en vez de 400 |
| 03 · Permisos | 5 | 5 pasan | 1 falla, 4 pasan | OPERATIONS obtiene 403 en vez de 200 |
| 04 · Paginación | 6 | 6 pasan | 3 fallan, 3 pasan | 21 elementos anuncian una página; un único elemento anuncia cero |
| 05 · Trazabilidad HTTP | 14 | 14 pasan | 3 fallan, 11 pasan | El servidor remoto recibe `X-Request-ID=null` |
| **Total** | **48** | **48 pasan** | **11 fallan, 37 pasan** | **Cinco bugs reproducidos** |

En ambos estados: **0 errores de ejecución y 0 pruebas omitidas** en las comprobaciones seleccionadas. Los 11 fallos son aserciones del contrato, no errores de arranque ni compilación. Un bug puede hacer fallar varias aserciones o casos de frontera.

El lanzador recorrió los cinco tickets pese a los fallos y terminó con código **1**. Este código es el resultado inicial esperado. Tras corregir los cinco, el mismo comando debe terminar con **0** y las 48 comprobaciones en verde.

Resúmenes extraídos de los XML de Surefire: [antes de los defectos](evidence/baseline.json) y [con los defectos activos](evidence/defects.json). Los logs locales de preparación están en `batch16-baseline-core.log`, `batch16-baseline-shipping.log` y `batch16-defects.log`; Git ignora esos logs. Los informes XML actuales están en `target/surefire-reports` y `services/shipping-service/target/surefire-reports`.

## Alcance

- Ticket 01: cálculo real y HTTP contra el servicio de envíos en puerto aleatorio.
- Ticket 02: controlador, DTO y validación reales con servicio de clientes simulado.
- Ticket 03: cadena de seguridad y permisos de métodos reales; identidades y servicio de informes de prueba. No valida login, firma o revocación JWT.
- Ticket 04: conversión real de una página preparada en memoria; no valida consultas SQL.
- Ticket 05: filtro y cliente reales con un servidor HTTP local; no ejecuta el flujo completo de pedidos.

La validación inicial automatizada no ejecutó PostgreSQL ni Docker. La adaptación posterior sí valida la app real, como se detalla abajo. No se ejecutaron la interfaz Angular, Jenkins ni la suite completa de integración. El historial del 12/11 se conserva y sus incidencias no quedan declaradas como resueltas por este lote.

La carpeta original `C:\Users\balar\Desktop\36-40k-APP` se mantuvo limpia en `training/2026-11-12/incidents`; sus archivos no se modificaron. La nueva rama conserva los defectos para que el alumno los corrija.

## Adaptación al flujo Application de IntelliJ

Se guardaron dos configuraciones de tipo **Application** en `.run/Backend LAB 16-11.run.xml` y `.run/Shipping LAB 16-11.run.xml`, con Main class, módulo, JRE 21, Working directory y Program arguments. No se modificaron los cinco defectos ni sus tests.

Se arrancó PostgreSQL 16.13 mediante `compose.batch16.yaml`, en `127.0.0.1:35437`, con base `lab_20261116` y volumen propios. Flyway creó el esquema y los datos de desarrollo. Se lanzaron ambas clases Main con Java 21 leyendo los mismos argumentos guardados en los XML. Esta ejecución verifica las clases y los argumentos; no certifica la selección de configuraciones dentro de la interfaz de IntelliJ.

| Comprobación HTTP real | Resultado observado |
| --- | --- |
| Backend 38087 y Shipping 39097 | Ambos readiness `UP` |
| Swagger de Backend | `200` |
| Login ADMIN, OPERATIONS y READONLY | Los tres `200` |
| 01 · Cotización de 200 € | `200`, `cost=7.50`: defecto reproducido; controles 199,99 y 200,01 correctos |
| 02 · Correo sin arroba | `201`, cliente creado: defecto reproducido; correo válido también `201` |
| 03 · Informe de ventas | OPERATIONS `403`, ADMIN y READONLY `200`, anónimo `401` |
| 04 · Ocho productos, tres por página | `totalPages=2`; la página 2 devuelve dos productos: defecto reproducido |
| 05 · Completar pedido de 49,80 € | `COMPLETED`, envío 7,50 €; Backend y Shipping registran identificadores distintos |
| 05 · Petición directa a Shipping | Conserva `X-Request-ID=lab16-direct-001` |

La evidencia está en [http-runtime.json](evidence/http-runtime.json). Se usaron autenticación, PostgreSQL y llamadas entre los servicios reales. La validación dejó dos clientes de prueba y un pedido completado en esta base de laboratorio; el catálogo conserva sus ocho productos.

Al terminar, los dos procesos Java usados para verificar se detuvieron para dejar libres 38087 y 39097. PostgreSQL quedó listo para arrancar las configuraciones desde IntelliJ. Los logs locales son `batch16-backend-runtime.log` y `batch16-shipping-runtime.log`.
