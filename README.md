# business-operations-backend

**Practice V3.** [Startup guide and five support tickets](practica-v3/PRACTICA-V3.md).

A B2B distribution application for customers, products, inventory, reservations, orders, shipping and auditing. Two Java 21 / Spring Boot 3.5.12 services communicate over REST. The operations backend uses PostgreSQL 16 and serves the Angular application in `frontend/`. The shipping service quotes delivery costs.

**November 10, 2026 expansion.** Suppliers, purchase orders, partial receipts, returns, inspections, invoices, payments, refunds and replenishment expanded the API from 36 to 71 HTTP operations. See the [scope](docs/expansion/2026-11-10/SCOPE.md), [API contracts](docs/expansion/2026-11-10/API.md), [tickets](docs/expansion/2026-11-10/TICKETS.md) and [validation](docs/expansion/2026-11-10/VALIDATION.md). Flyway migrations V5–V8 support the additional workflows.

Open the root `pom.xml` and `services/shipping-service/pom.xml` in IntelliJ to work on both services. This repository includes the frontend, study comments and fixes for the five exercises.

## Getting started

The [80-hour roadmap](docs/WAREHOUSE-ROADMAP.md) covers 19 activities. The [Jenkins and Kubernetes lab](docs/JENKINS-KIND-LAB.md) uses the `ram-jenkins` account to deploy both services in Kind and exercise orders, shipping outages and recovery. See the [verification record](docs/MICROSERVICES-VERIFICATION.md) for test results.

| Service | Responsibility | Data and contract |
| --- | --- | --- |
| Core (`app` in Compose, `backend-app` in Kubernetes) | Customers, catalog, inventory, sales, purchases, returns, billing, replenishment, reports, audit and JWT | Dedicated PostgreSQL database. API `/api/v1` on 8086 |
| [`shipping-service`](services/shipping-service/README.md) | Shipping quote of EUR 7.50, free for orders of EUR 200 or more | Stateless service. `GET /quotes` on 9096 |

## Angular frontend

**Almacén en vivo:** abre [la vista animada](http://localhost:4200/#/warehouse). Representa las peticiones reales con operarios y ocho zonas, muestra existencias y reservas, e incluye formularios para los 70 endpoints del backend. Los ejemplos y las repeticiones de animaciones no modifican los datos. Consulta el [uso y las pruebas del frontend](frontend/README.md#almacén-en-vivo).

For the November 12 exercises, start the lab backend and open the [warehouse lab](http://localhost:38086/#/lab). Each step displays the request and the response in English. To run Angular separately against the lab backend, use `npm run lab` in `frontend/`.

The Angular workspace covers authentication, users, catalog, customers, inventory, orders, reports and audit history. It includes request forms and displays HTTP responses.

```powershell
cd frontend
npm install
npm start
```

The development server runs on `http://localhost:4200` and proxies `/api` to `http://localhost:8086`. To build the frontend and copy it into the backend, run:

```powershell
npm run build:backend
```

Restart Spring Boot to serve the workspace at `http://localhost:8086/`.

## Run the complete stack with Docker Desktop

From the repository root in PowerShell:

```powershell
docker compose up -d --build --wait
```

Docker downloads the images and dependencies on first startup. PostgreSQL and both services have health checks. Published ports bind to localhost.

- Swagger: http://localhost:8086/swagger-ui/index.html
- OpenAPI: http://localhost:8086/v3/api-docs
- Readiness: http://localhost:8086/actuator/health/readiness
- PostgreSQL: `localhost:5436`, database `business_operations`, user `business`, development password `business_dev_only`.
- Shipping readiness: http://localhost:9096/actuator/health/readiness

**Development accounts:** `admin` (ADMIN), `operations` (OPERATIONS), and `readonly` (READONLY). All three use `DevPassword123!`.

In Swagger, run `POST /api/v1/auth/login`, copy `accessToken` and paste it into **Authorize**. Swagger adds the Bearer prefix. The development seed includes three customers, eight products, opening stock movements and a draft sales order.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
docker compose logs --tail=100 app
docker compose restart app
docker compose stop
docker compose start
```

The smoke test creates its own product, stock and orders. It verifies completion, cancellation, permissions and token rotation. `docker compose down` retains the data volume. Adding `-v` also removes the volume and its data.

## IntelliJ IDEA and Java 21 on Windows

1. Open the root `pom.xml` with **File → Open → Open as Project** and import Maven.
2. Set the project SDK and language level to Java 21 under **File → Project Structure**.
3. In **Settings → Build Tools → Maven**, choose the Maven wrapper. Choose JDK 21 for the runner and importer.
4. Start PostgreSQL with `docker compose -f compose.yaml -f compose.intellij.yaml up -d --wait`. If Docker is already running the application services, stop them with `docker compose stop app shipping-service` before launching them in IntelliJ.
5. Import `services/shipping-service/pom.xml` as a Maven project and run `ShippingApplication` on port 9096.
6. Run `BusinessOperationsApplication` with the `dev` profile. The program argument is `--spring.profiles.active=dev`.

To run the shipping service in Docker and the backend locally, adjust the JDK path as needed:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -version
docker compose up -d --build --wait postgres shipping-service
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

To debug an order confirmation, set a breakpoint in `OrderService.confirm` and submit the request from Swagger. The catalog uses JPA. Inventory and order workflows use JDBC for explicit queries and locking.

## Verification

With Java 21 and Docker Desktop running:

```powershell
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml clean verify
```

Both builds require Java 21. Surefire runs unit and HTTP client tests. Failsafe runs `*IntegrationTest` against PostgreSQL in Testcontainers. The container uses a separate database and port from Compose. Results are in `target/surefire-reports`, `target/failsafe-reports` and `target/site/jacoco/index.html`. The shipping service tests pricing, configuration and HTTP behavior. Its results are in `services/shipping-service/target/surefire-reports`.

See [local verification results](docs/VERIFICATION.md). The temporary GitHub Actions workflow runs Jenkins, deploys to Kind and publishes artifacts. The [Jenkins and Kind lab](docs/JENKINS-KIND-LAB.md) describes the workflow and exercises.

## Learning exercises

The integrated application is on `main`. Tags `ejercicios/debug-v1` and `ejercicios/debug-v2` preserve the intentional debugging exercises. Run `git worktree add --detach ../practica-v2 ejercicios/debug-v2` to work on the second set separately.

The [learning lab](docs/LEARNING-LAB.md) covers the 19 tickets in the [original 80-hour roadmap](docs/WAREHOUSE-ROADMAP.md). It includes exercises, recall practice and criteria for independent work. The [learning audit](docs/LEARNING-AUDIT.md) records local tests, remote evidence and pending checks.

Read the [business overview](docs/01-BUSINESS.md), [architecture](docs/02-ARCHITECTURE.md), [database](docs/03-DATABASE.md), [workflows](docs/04-BUSINESS-FLOWS.md), [security](docs/05-SECURITY.md), [testing](docs/06-TESTING.md), [operations](docs/07-OPERATIONS.md) and [engineering decisions](docs/08-ENGINEERING-DECISIONS.md).

Executable requests are in [business-operations.http](http/business-operations.http). The [15 work items](work-items/README.md) refer to the supplied code and describe how to reproduce each incident.

## Product scope

The application models one warehouse, whole units, amounts in EUR before tax or discounts, and one role per account. Shipping quotes have no external side effects. Completing an order records the outbound stock movement. Physical delivery, external carriers, ERP and tax invoicing are outside the scope of this project. Kubernetes is used for temporary labs. `compose.simulator.yaml` supplies the older simulator for its exercises.

The project uses [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) and the [3.5.12 release](https://spring.io/blog/2026/03/19/spring-boot-3-5-12-available-now/). The pinned version keeps the exercises reproducible.
