# business-operations-backend

Backend de operaciones B2B de una distribuidora: clientes, catálogo, inventario, reservas, pedidos, transporte y auditoría. Dos servicios Java 21 / Spring Boot 3.5.12 comunicados por REST: el núcleo de operaciones con PostgreSQL 16 y un servicio independiente de cotizaciones. El núcleo conserva sus módulos y transacciones locales; no contiene frontend.

## Empieza aquí

La [hoja de ruta de 80 horas](docs/WAREHOUSE-ROADMAP.md) contiene 19 actividades con clases, resultados verificables y modelos Codex recomendados. El [laboratorio Jenkins + Kubernetes](docs/JENKINS-KIND-LAB.md) usa el usuario fijo `ram-jenkins`, despliega ambos servicios en Kind y verifica pedidos, caída de shipping y recuperación. Consulta [la verificación de esta entrega](docs/MICROSERVICES-VERIFICATION.md) para distinguir resultados ejecutados de comprobaciones pendientes.

| Servicio | Responsabilidad | Datos y contrato |
| --- | --- | --- |
| Núcleo (`app` en Compose, `backend-app` en K8s) | Clientes, catálogo, inventario, pedidos, informes, auditoría y JWT | PostgreSQL propio; API `/api/v1` en 8086 |
| [`shipping-service`](services/shipping-service/README.md) | Cotización: 7,50 EUR; gratis desde 200,00 EUR | Sin estado ni acceso a la BD del núcleo; `GET /quotes` en 9096 |

## Arranque completo con Docker Desktop

En PowerShell, desde esta carpeta:

```powershell
docker compose up -d --build --wait
```

No requiere Maven ni JDK en el host. Primera ejecución: descarga imágenes y dependencias. PostgreSQL y ambos servicios tienen health checks. Los puertos publicados están limitados a localhost.

- Swagger: http://localhost:8086/swagger-ui/index.html
- OpenAPI: http://localhost:8086/v3/api-docs
- Readiness: http://localhost:8086/actuator/health/readiness
- PostgreSQL: `localhost:5436`, base `business_operations`, usuario `business`, contraseña de desarrollo `business_dev_only`.
- Servicio de cotizaciones: http://localhost:9096/actuator/health/readiness

Usuarios **exclusivamente de desarrollo**: `admin` (ADMIN), `operations` (OPERATIONS), `readonly` (READONLY). Contraseña de los tres: `DevPassword123!`.

En Swagger ejecuta `POST /api/v1/auth/login`, copia `accessToken` y pégalo en **Authorize**. No añadas manualmente el prefijo Bearer. Los datos de demostración incluyen 3 clientes, 8 productos, existencias, movimientos iniciales y un pedido borrador.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
docker compose logs --tail=100 app
docker compose restart app
docker compose stop
docker compose start
```

El smoke crea un producto, existencias y pedidos propios; verifica el ciclo completo, cancelación, permisos y rotación de tokens. No borra datos. `docker compose down` elimina los contenedores y conserva el volumen. No uses `down -v` salvo que quieras destruir expresamente los datos locales.

## IntelliJ IDEA y Java 21 en Windows

1. **File → Open → selecciona `pom.xml` → Open as Project**. Acepta importar Maven.
2. **File → Project Structure → Project SDK: JDK 21**. Puedes usar Download JDK → Eclipse Temurin → 21. Language level: 21.
3. **Settings → Build, Execution, Deployment → Build Tools → Maven → Maven home path: Wrapper**; en Runner e Importing selecciona JDK 21.
4. Arranca únicamente dependencias: `docker compose up -d --build --wait postgres shipping-service`. Si el servicio app estaba iniciado, detenlo con `docker compose stop app` para liberar 8086.
5. Ejecuta `BusinessOperationsApplication` con **Active profiles: dev**. En Community, añade `--spring.profiles.active=dev` a Program arguments. La configuración del perfil contiene los valores locales documentados.

Alternativa PowerShell (ajusta únicamente la ruta del JDK si es distinta):

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -version
docker compose up -d --build --wait postgres shipping-service
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Para depurar, coloca un breakpoint en `OrderService.confirm`, envía la petición desde Swagger y sigue la transacción. Las entidades JPA se usan para el catálogo; inventario y pedidos usan JDBC para controlar bloqueos y consultas explícitas.

## Verificación

Con Java 21 y Docker Desktop funcionando:

```powershell
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml clean verify
```

Los dos builds exigen Java 21. En el núcleo, Surefire ejecuta unitarias y pruebas del cliente HTTP; Failsafe ejecuta `*IntegrationTest` con Testcontainers y PostgreSQL real. No usa H2 ni omite las integraciones si falta Docker. Informes en `target/surefire-reports`, `target/failsafe-reports` y `target/site/jacoco/index.html`. El testcontainer tiene base y puerto independientes del Compose. Shipping tiene pruebas propias de cálculo, configuración y contrato HTTP sin Docker, en `services/shipping-service/target/surefire-reports`.

Resultados locales registrados: [docs/VERIFICATION.md](docs/VERIFICATION.md). El flujo temporal de GitHub Actions ejecuta un Jenkins real, construye y despliega en Kind y adjunta evidencias. Su recorrido y ejercicios están explicados en [docs/JENKINS-KIND-LAB.md](docs/JENKINS-KIND-LAB.md); la ejecución remota solo se considera validada cuando su run y artifacts estén disponibles.

## Trabajo en el sistema

Lee en orden [negocio](docs/01-BUSINESS.md), [arquitectura](docs/02-ARCHITECTURE.md), [base de datos](docs/03-DATABASE.md), [flujos](docs/04-BUSINESS-FLOWS.md), [seguridad](docs/05-SECURITY.md), [testing](docs/06-TESTING.md), [operación](docs/07-OPERATIONS.md) y [decisiones](docs/08-ENGINEERING-DECISIONS.md).

Las peticiones ejecutables están en [http/business-operations.http](http/business-operations.http). Los [15 tickets](work-items/README.md) se basan en el código entregado; los ejercicios de incidencias explican cómo reproducirlos de forma controlada. La versión principal conserva sus garantías transaccionales.

## Límites del producto

Un almacén lógico, cantidades enteras, EUR sin impuestos ni descuentos, una función de usuario por cuenta. Shipping facilita una cotización sin efectos externos; completar marca la salida comercial de mercancía, no acredita una entrega física real. No se integra un transportista real, ERP o facturación fiscal. Kubernetes se usa como laboratorio temporal; no se aprovisiona infraestructura permanente. El simulador anterior se conserva exclusivamente para ejercicios con `compose.simulator.yaml`.

La selección tecnológica utiliza la [compatibilidad oficial de Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html) y la [publicación estable de 3.5.12](https://spring.io/blog/2026/03/19/spring-boot-3-5-12-available-now/). La versión queda fijada para reproducir la entrega; sus actualizaciones se gestionan como mantenimiento con ejecución de la suite.
