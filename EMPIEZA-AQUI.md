# Batch del 16/11: cinco bugs, cinco temas

Empieza por el **01**. Los otros cuatro son independientes: puedes elegir el orden.

| Ticket | Tema | Problema | Tiempo estimado |
| --- | --- | --- | --- |
| [01](docs/training/2026-11-16/TICKETS.md#01--el-envío-gratis-falla-justo-en-el-límite) | Java: condiciones y dinero | Se cobran 7,50 € de envío a una compra de 200 € | 15–25 min |
| [02](docs/training/2026-11-16/TICKETS.md#02--se-acepta-un-correo-sin-arroba) | REST: validación de entrada | Se acepta un correo sin arroba | 20–35 min |
| [03](docs/training/2026-11-16/TICKETS.md#03--operaciones-no-puede-consultar-ventas) | Spring Security: permisos | OPERATIONS recibe 403 al consultar ventas | 30–45 min |
| [04](docs/training/2026-11-16/TICKETS.md#04--el-listado-dice-que-no-hay-segunda-página) | Colecciones y paginación | Hay 21 resultados, pero se anuncia una sola página de 20 | 20–35 min |
| [05](docs/training/2026-11-16/TICKETS.md#05--se-pierde-el-identificador-entre-servicios) | HTTP entre servicios: trazabilidad | La cotización llega, pero su identificador de petición se pierde | 35–50 min |

Estimaciones de trabajo por ejercicio; no son un límite. No hace falta terminar la tanda en un día.

## Arranque mínimo

1. Abre **esta carpeta** en IntelliJ: `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-16`.
2. Usa JDK 21. Importa `pom.xml` y añade `services/shipping-service/pom.xml` como proyecto Maven.
3. Desde PowerShell, en esta carpeta, ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-batch-2026-11-16.ps1 -Ticket 01
```

La prueba empieza en rojo porque el bug está preparado. Lee el ticket, reproduce el fallo, corrige el código de aplicación y repite el mismo comando. Cambia `01` por `02`, `03`, `04` o `05` para otro ejercicio.

**No necesitas Docker, PostgreSQL, login, frontend ni arrancar el backend.** Las pruebas preparan sus propios datos en memoria. Cuando hace falta HTTP real, abren un puerto local libre y lo cierran al terminar.

## Depurar sin confundir el backend

Abre la clase de test indicada en el ticket y ejecútala con **Debug**, desde el icono verde junto al método o a la clase. El breakpoint debe estar en el código de **esta copia**. Esa ejecución lanza su propia JVM; no depende de qué backend esté escuchando en 8086 o 38086.

Para el primer ejercicio abre `services/shipping-service/src/test/java/com/acme/shipping/QuoteServiceTest.java` y depura `appliesShippingPolicyAtItsBoundaries`. Observa los casos de la tabla del ticket.

## Qué entregar en cada ejercicio

- Qué respuesta o valor fallaba.
- La causa y tu cambio de código.
- La prueba que ahora pasa y un caso que sigue funcionando.

Puedes anotarlo en [PROGRESO.md](docs/training/2026-11-16/PROGRESO.md). No se incluyen soluciones paso a paso. Las aserciones de los tests representan el contrato y deben conservarse.

Al terminar los cinco:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-batch-2026-11-16.ps1 -Ticket all
```

## Aislamiento y evidencia

Rama local: `codex/training-2026-11-16`. Base: `74ccb5e53a06af0c41fe870b8d617586c6038c5d`.

La carpeta `36-40k-APP` y su rama del 12/11 siguen siendo tu trabajo anterior. Esta tanda conserva ese historial. Los ejercicios pendientes del 12/11 no son requisitos para estos cinco, y el comando del batch no ejecuta sus pruebas de integración.

Consulta [VALIDATION.md](docs/training/2026-11-16/VALIDATION.md) para ver las pruebas ejecutadas y su alcance. El estado rojo inicial de esta rama es intencionado; es una rama de entrenamiento local.
