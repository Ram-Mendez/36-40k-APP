# Batch del 16/11 — arrancar la app en IntelliJ

El flujo es el habitual: arrancar PostgreSQL, ejecutar las aplicaciones con **Debug**, enviar peticiones desde Swagger o el cliente HTTP y seguir los breakpoints. Mantén abierto este proyecto: `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-16`.

## 1. PostgreSQL del lote

Desde el terminal de este proyecto:

```powershell
docker compose -f compose.batch16.yaml up -d --wait
```

Este Compose solo arranca la base `lab_20261116` en `localhost:35437`, con un volumen propio. El backend y el servicio de envíos se ejecutan desde IntelliJ.

## 2. Configuraciones Application

Ya están guardadas en `.run`. En el selector de arriba deben aparecer **Shipping LAB 16-11** y **Backend LAB 16-11**. Selecciona cada una y pulsa **Debug**. Arranca primero Shipping y después Backend. Son las mismas dos configuraciones para los cinco ejercicios.

Si todavía no aparecen, recarga los archivos del proyecto. También puedes crearlas con **Run → Edit Configurations → + → Application** y estos valores exactos.

### Backend LAB 16-11

| Campo de IntelliJ | Valor |
| --- | --- |
| Name | `Backend LAB 16-11` |
| Main class | `com.acme.operations.BusinessOperationsApplication` |
| Use classpath of module | `business-operations-backend` |
| JRE | `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` |
| Working directory | `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-16` |
| Environment variables | Dejar vacío; los valores van en Program arguments |

**Program arguments** — pega el bloque completo en ese campo, no en VM options:

```text
--spring.profiles.active=dev --server.address=127.0.0.1 --server.port=38087 --spring.datasource.url=jdbc:postgresql://localhost:35437/lab_20261116 --spring.datasource.username=lab_20261116 --spring.datasource.password=lab_20261116_dev --shipping.base-url=http://localhost:39097 --spring.application.name=backend-lab-16-11 --security.jwt.secret=batch16-development-only-secret-change-before-production
```

### Shipping LAB 16-11

| Campo de IntelliJ | Valor |
| --- | --- |
| Name | `Shipping LAB 16-11` |
| Main class | `com.acme.shipping.ShippingApplication` |
| Use classpath of module | `shipping-service` |
| JRE | `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` |
| Working directory | `C:\Users\balar\Desktop\36-40k-BATCH-2026-11-16\services\shipping-service` |
| Environment variables | Dejar vacío |

**Program arguments:**

```text
--server.address=127.0.0.1 --server.port=39097 --spring.application.name=shipping-lab-16-11 --shipping.pricing.flat-cost=7.50 --shipping.pricing.free-threshold=200.00
```

Si no puedes elegir `shipping-service`, añade `services/shipping-service/pom.xml` como proyecto Maven. Ambas configuraciones usan Java 21, aunque el SDK general de IntelliJ sea otro.

## 3. Abrir la aplicación

- Swagger: http://localhost:38087/swagger-ui/index.html
- Backend: http://localhost:38087/actuator/health/readiness
- Shipping: http://localhost:39097/actuator/health/readiness
- Cotización del primer ejercicio: http://localhost:39097/quotes?orderId=42&amount=200.00

En Swagger ejecuta `POST /api/v1/auth/login` con `{"username":"admin","password":"DevPassword123!"}`. Copia `accessToken` en **Authorize**. Las cuentas `operations` y `readonly` tienen la misma contraseña. El perfil `dev` prepara los usuarios, tres clientes, ocho productos y existencias iniciales.

El archivo [http/batch-2026-11-16.http](http/batch-2026-11-16.http) permite enviar las peticiones desde IntelliJ. Guarda los tokens y el ID del pedido en las respuestas. Para el ejercicio 05, resulta cómodo porque permite enviar la cabecera `X-Request-ID`.

## 4. Elegir ejercicio

| Ticket | Tema | Incidencia | Tiempo estimado |
| --- | --- | --- | --- |
| 01 | Java y precios | Se cobra envío justo en 200 € | 15–25 min |
| 02 | Validación REST | Se acepta un correo sin arroba | 20–35 min |
| 03 | Permisos | OPERATIONS recibe 403 al consultar ventas | 30–45 min |
| 04 | Paginación | El catálogo anuncia menos páginas de las necesarias | 20–35 min |
| 05 | Comunicación HTTP | La petición cambia de identificador entre servicios | 35–50 min |

Los pasos completos están en [TICKETS.md](docs/training/2026-11-16/TICKETS.md). Son independientes y puedes empezar por el 01. Los tiempos son estimaciones, no un límite.

Pon los breakpoints en el código de **esta carpeta** y usa la petición correspondiente. El primer bug está en el servicio de envíos: debes depurar **Shipping LAB 16-11**. El backend de los demás ejercicios escucha en **38087**.

## Comprobar tu corrección

Primero repite la petición y comprueba la respuesta. Después puedes ejecutar la prueba de regresión del ticket:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-batch-2026-11-16.ps1 -Ticket 01
```

Sustituye `01` por el ticket elegido, o por `all` al terminar los cinco. Los tests son una comprobación adicional; el ejercicio se trabaja con la app en ejecución. Anota causa, cambio y resultado en [PROGRESO.md](docs/training/2026-11-16/PROGRESO.md).

Rama: `codex/training-2026-11-16`. [Evidencia de validación](docs/training/2026-11-16/VALIDATION.md).
