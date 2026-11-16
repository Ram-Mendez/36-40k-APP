# Validación del batch — 16/11/2026

**Estado:** cinco defectos introducidos y reproducidos mediante pruebas. Rama de práctica local, sin publicar.

**Base:** `74ccb5e53a06af0c41fe870b8d617586c6038c5d`. **Rama:** `codex/training-2026-11-16`.

**Carpeta:** `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-16`.

## Evidencia ejecutada

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

No se ejecutaron PostgreSQL, Docker, la interfaz Angular, Jenkins ni la suite completa de integración. La validación solo certifica estos cinco ejercicios. El historial del 12/11 se conserva y sus incidencias no quedan declaradas como resueltas por este lote.

La carpeta original `C:\Users\balar\Desktop\36-40k-APP` se mantuvo limpia en `training/2026-11-12/incidents`; sus archivos no se modificaron. La nueva rama conserva los defectos para que el alumno los corrija.
