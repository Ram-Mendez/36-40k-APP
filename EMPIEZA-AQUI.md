# Batch 17/11 — cinco incidencias, dificultad estimada 4/5

Partimos de tus cinco correcciones del 16/11. Esta copia y rama son independientes. Los escenarios mezclan Java, JPA, seguridad, HTTP y Angular; solo dos requieren estudiar persistencia. El nivel 4/5 es una estimación para tu progreso actual: tendrás que comparar varias peticiones, estados o momentos.

## Arranque en IntelliJ

Abre `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-17`. Importa el `pom.xml` principal y el de `services/shipping-service`. Usa Java 21.

```powershell
docker compose -f compose.batch17.yaml up -d --wait
```

Selecciona **Shipping LAB 17-11** y después **Backend LAB 17-11**; arranca ambos con Debug. Las configuraciones Application están guardadas en `.run`. Si no aparecen, recarga los archivos del proyecto o créalas con estos valores.

| Campo | Backend LAB 17-11 | Shipping LAB 17-11 |
| --- | --- | --- |
| Main class | `com.acme.operations.BusinessOperationsApplication` | `com.acme.shipping.ShippingApplication` |
| Use classpath of module | `business-operations-backend` | `shipping-service` |
| JRE | Java 21 | Java 21 |
| Working directory | Carpeta raíz de este batch | `services/shipping-service` dentro de esta copia |

**Program arguments del Backend:**

```text
--spring.profiles.active=dev --server.address=127.0.0.1 --server.port=38088 --spring.datasource.url=jdbc:postgresql://localhost:35438/lab_20261117 --spring.datasource.username=lab_20261117 --spring.datasource.password=lab_20261117_dev --shipping.base-url=http://localhost:39098 --spring.application.name=backend-lab-17-11 --security.jwt.secret=batch17-development-only-secret-change-before-production
```

**Program arguments de Shipping:**

```text
--server.address=127.0.0.1 --server.port=39098 --spring.application.name=shipping-lab-17-11 --shipping.pricing.flat-cost=7.50 --shipping.pricing.free-threshold=200.00
```

Swagger: http://localhost:38088/swagger-ui/index.html. Login: `admin` / `DevPassword123!`; también existen `operations` y `readonly` con esa contraseña. En Swagger pega `accessToken` en Authorize.

Frontend, desde `frontend`:

```powershell
npm run lab -- --port 4201
```

Abre http://localhost:4201. El proxy apunta a **38088**. Las dependencias ya quedaron instaladas en esta copia. En una copia nueva, ejecuta antes `npm ci`.

## Ejercicios independientes

| Ticket | Tema | Incidencia | Dificultad estimada |
| --- | --- | --- | --- |
| 01 | Java: caché e identidad de una petición | La cotización depende de una consulta anterior | 4/5 |
| 02 | JPA: edición concurrente | Una pestaña antigua sobrescribe un cambio reciente | 4/5 |
| 03 | Seguridad y transacciones | Reutilizar un refresh se rechaza, pero la sesión sucesora sigue viva | 4/5 |
| 04 | Resiliencia HTTP | Shipping se recupera y Backend sigue rechazándolo | 4/5 |
| 05 | Angular: respuestas asíncronas | Datos anteriores reaparecen después de salir de la sesión | 4/5 |

Lee [TICKETS.md](docs/training/2026-11-17/TICKETS.md). Cada ticket incluye control, reproducción y aceptación; no incluye la solución. Estimación orientativa: 45–90 minutos por ticket; no es un límite.

El ejercicio principal se hace con la app y sus breakpoints. Como comprobación adicional:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-batch-2026-11-17.ps1 -Ticket 01
```

Cambia el número por el ticket, o usa `all`. Los checks empiezan en rojo. Los de 02 y 03 usan PostgreSQL desechable de Testcontainers, separado de tu base de práctica. No cambies las aserciones para ocultar los fallos.

Antes del 04, reinicia Backend; después del 04, vuelve a reiniciarlo si quieres hacer otros tickets. Ese ejercicio deja deliberadamente un estado de fallo en memoria. El 01 se aísla usando un `orderId` nuevo. El 05 utiliza un proxy de retraso opcional, explicado en el ticket.

[Validación ejecutada](docs/training/2026-11-17/VALIDATION.md). Rama: `codex/training-2026-11-17`.
