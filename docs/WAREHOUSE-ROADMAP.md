# Warehouse: microservicios y práctica backend

## Objetivo y estado comprobado

Convertir este proyecto en un laboratorio pequeño de servicios comunicados, conservando su riqueza funcional y dedicando la mayor parte del estudio a Java, pruebas y PostgreSQL. Trabajamos únicamente en `Ram-Mendez/36-40k-APP`, desde la rama `codex/backend-java-lab`; cada entrega debe identificar su rama y commit efectivos.

La revisión parte del commit [`62890b1b5c7e6ae819623db692d8f23036873d5b`](https://github.com/Ram-Mendez/36-40k-APP/tree/62890b1b5c7e6ae819623db692d8f23036873d5b): Java 21, Spring Boot 3.5.12, PostgreSQL 16.13 y 75 casos originales. El núcleo incluye clientes, productos, inventario, reservas, pedidos, informes, auditoría y JWT con tres roles; no contiene frontend.

El [run anterior terminó correctamente](https://github.com/Ram-Mendez/36-40k-APP/actions/runs/40094748590), pero no demuestra el circuito completo de pedidos: la configuración apuntaba a `carrier:9096` sin su Deployment y Service. Una API disponible y una prueba de persistencia correctas no verifican esa dependencia ausente.

**Estado de esta propuesta:** 27 pruebas unitarias del núcleo y 24 pruebas de shipping ejecutadas correctamente con Java 21. Las integraciones PostgreSQL, Jenkins y Kind se comprueban en el run de esta rama; consulta [la verificación](MICROSERVICES-VERIFICATION.md). El run anterior se mantiene como referencia independiente.

## Arquitectura elegida

Dos aplicaciones desplegables por separado:

- **Core actual:** conserva clientes, catálogo, inventario, reservas, pedidos, informes, auditoría, seguridad y PostgreSQL. Mantiene sus claves foráneas y transacciones locales.
- **`shipping-service`:** aplicación Spring Boot en `services/shipping-service/`, sin base de datos, que calcula presupuestos mediante `GET /quotes?orderId=...&amount=...`. Devuelve JSON con `reference` y `cost`; tarifa de 7,50 y envío gratuito desde un importe de 200,00, incluido ese límite.

El core llama a shipping por HTTP usando `ShippingClient`, con timeout de conexión de 2 segundos y lectura de 3 segundos. `OrderCompletionService` obtiene una instantánea del pedido, solicita el presupuesto fuera de una transacción y entrega el resultado a `OrderService.complete`, que bloquea y revalida estado y versión antes de completar. Una cancelación concurrente no debe quedar sobrescrita.

El presupuesto es una consulta sin efectos secundarios: no crea expediciones ni cobra dinero. Repetirlo no duplica operaciones de transporte; completar el pedido dos veces tampoco debe consumir stock dos veces. Eso no equivale a haber implementado idempotencia general para futuras escrituras remotas.

Esta primera separación deja un core amplio y una responsabilidad remota real. Extraer ahora inventario o pedidos añadiría coordinación distribuida a operaciones que actualmente son atómicas. Kafka, Eureka, gateway, Helm, malla de servicios y sagas quedan fuera de este alcance.

## Jenkins y Kubernetes

El flujo continúa siendo **GitHub Actions → Jenkins temporal → Maven → imágenes Docker → Kind → PostgreSQL/core/shipping → verificación → limpieza**. Se busca mantener el laboratorio online sin servidor permanente ni necesidad de dejar encendido el PC.

El usuario previsto de Jenkins es **`ram-jenkins`**, recreado al arrancar cada runner. Para conservar también la contraseña entre ejecuciones debe existir el secreto de GitHub **`JENKINS_ADMIN_PASSWORD`**. Sin ese secreto, CI utiliza una contraseña aleatoria para esa ejecución; no debe mostrarse ni versionarse. El usuario de Jenkins es distinto del administrador de la aplicación.

La captura de Docker Desktop muestra además un Jenkins local, imagen `ram-jenkins`, puerto `8081:8080`. Ese contenedor no es el controlador temporal del pipeline y no se ha administrado desde esta revisión. Una identidad estable en CI tampoco supone disponer de una URL Jenkins permanente.

Kind vive durante el job. Su volumen permite estudiar reinicios de PostgreSQL dentro de esa ejecución, pero desaparece al eliminar el clúster. La aceptación debe comprobar los tres componentes, el DNS interno de `shipping-service`, la cotización real y la finalización de un pedido; después debe guardar evidencia y limpiar incluso si algo falla.

## Distribución de estudio: 80 horas

Estas son las horas exactas de la imagen aportada; son una distribución de práctica, no una garantía de duración de implementación.

| Bloque | Horas | Evidencia principal |
|---|---:|---|
| Java + Spring Boot | 18 | Explicar y ejecutar tres flujos completos |
| Testing | 18 | Pruebas de negocio, integración y fallos remotos |
| PostgreSQL | 18 | Consultas, restricciones y concurrencia justificadas |
| Microservicios + problemas | 12 | Comunicación HTTP y diagnóstico de dependencia caída |
| Git, Maven, Docker y CI/CD | 6 | Pipeline reproducible con evidencia |
| Seguridad | 4 | Autenticación, permisos y secretos diferenciados |
| Arquitectura | 4 | Límites y decisiones explicados con código |
| **Total** | **80** | **54 horas dedicadas a backend, pruebas y SQL** |

## Tickets ejecutables

Cada ticket termina con código localizado, una comprobación reproducible y una explicación propia. Las rutas Java siguientes son relativas a `src/main/java/com/acme/operations/`; las de pruebas, a `src/test/java/com/acme/operations/`.

| Ticket y tiempo | Actividad y archivos | Resultado verificable |
|---|---|---|
| J1 · 6 h | Recorrer alta y actualización de producto: `product/ProductController.java`, `ProductService.java`, `ProductRepository.java`. | Producto persistido, SKU repetido rechazado y versión antigua detectada; explicar validación, JPA y transacción. |
| J2 · 6 h | Seguir entradas, reservas y liberaciones: `inventory/InventoryService.java` y `InventoryRepository.java`. | Diferenciar existencias, reservado y disponible; un fallo no deja saldo parcial. |
| J3 · 6 h | Ejecutar crear pedido, añadir línea, confirmar, cancelar y completar: `order/OrderController.java`, `OrderService.java`, `OrderCompletionService.java`. | Explicar cada transición y demostrar que una operación inválida conserva el estado. |
| T1 · 6 h | Revisar y ampliar casos útiles en `InventoryServiceTest.java` y los tests de `OrderService`. | Cubrir reglas concretas, stock insuficiente y estados inválidos; evitar pruebas que solo repitan la implementación. |
| T2 · 6 h | Seguir `AbstractIntegrationTest.java` y `BusinessIntegrationTest.java` con PostgreSQL real de Testcontainers. | Verificar persistencia, rollback y restricciones SQL; identificar qué parte está simulada. |
| T3 · 6 h | Probar `ShippingClientTest.java` y la finalización concurrente en las integraciones. | Timeout/respuesta inválida producen fallo controlado; cancelación concurrente y repetición no duplican consumo. |
| D1 · 6 h | Leer `src/main/resources/db/migration/V1__catalog_audit.sql` y `V3__orders_inventory.sql`. | Relacionar tablas, claves y restricciones con producto, pedido, reserva y movimientos; conservar migraciones aplicadas. |
| D2 · 6 h | Trabajar `report/ReportRepository.java`: ventas por cliente y stock bajo. | Explicar JOIN, agregación, filtros y paginación; comparar `EXPLAIN ANALYZE` antes de proponer índices. |
| D3 · 6 h | Relacionar `order/OrderRepository.java` e `inventory/InventoryRepository.java` con dos transacciones concurrentes. | Mostrar cuándo hay espera, conflicto o rollback y justificar el orden de bloqueo. |
| M1 · 4 h | Revisar `services/shipping-service/` y el contrato usado por `shipping/ShippingClient.java`. | Presupuestos para 199,99 y 200,00; mismo contrato JSON entre aplicaciones e imagen independiente. |
| M2 · 4 h | Seguir `OrderCompletionService` y `OrderService.complete`. | La espera HTTP sucede fuera de la transacción; estado y versión se comprueban al volver. |
| M3 · 4 h | Provocar caída del servicio shipping y revisar `infra/kubernetes/` y logs. | Core responde con error controlado, conserva reserva y permite reintentar cuando la dependencia vuelve. |
| I1 · 2 h | Revisar `infra/jenkins/init.groovy` y `scripts/run-github-jenkins.sh`. | Login de `ram-jenkins`, autenticación del pipeline y ausencia de contraseña en consola. |
| I2 · 2 h | Seguir `Jenkinsfile`, `.github/workflows/ci.yml` y manifiestos de `infra/kubernetes/`. | Imágenes cargadas en Kind; PostgreSQL, core y shipping disponibles; configuración y puertos coherentes. |
| I3 · 2 h | Ejecutar el workflow y revisar artifacts. | Pedido completado mediante shipping real, persistencia tras reinicio, diagnóstico de fallo y limpieza final. |
| S1 · 2 h | Revisar `security/SecurityConfiguration.java` y `security/PermissionAndErrorIntegrationTest.java`. | Diferenciar 401/403 y demostrar permisos de los tres roles y sesión revocada. |
| S2 · 2 h | Revisar bootstrap de la aplicación y configuración Jenkins/Kubernetes. | Distinguir usuario Jenkins, usuario API y credenciales PostgreSQL; secretos fuera del repositorio y logs. |
| A1 · 2 h | Actualizar `docs/02-ARCHITECTURE.md` con las responsabilidades reales. | Identificar dependencias, propiedad de datos y límites transaccionales. |
| A2 · 2 h | Registrar decisiones en `docs/08-ENGINEERING-DECISIONS.md`. | Explicar por qué shipping es independiente y por qué pedidos/inventario siguen juntos. |

Orden práctico: I1–I2, M1–M2 y las comprobaciones correspondientes de T3/I3 para estabilizar la base; después J1–J3, D1–D3, T1–T2, M3, S1–S2 y A1–A2. Las horas de verificación se contabilizan en su ticket, sin duplicarlas.

## Modelo y tamaño de cada encargo

Usar **GPT-5.6 Luna Ligero**, visible en la captura, para tareas pequeñas con aceptación precisa: documentación, validaciones concretas, casos de prueba y cambios acotados de configuración. Reservar **GPT-6.1 Sol**, si está disponible, con esfuerzo predeterminado o Medio para extracción de servicios, límites transaccionales y revisión de concurrencia. Es una recomendación por dificultad; no se afirma un ahorro porcentual.

La [documentación oficial de OpenAI sobre modelos](https://learn.chatgpt.com/docs/models) orienta Luna a tareas delimitadas y Sol al trabajo complejo. Mantener velocidad Standard: [Fast aumenta consumo](https://learn.chatgpt.com/docs/agent-configuration/speed). El [cupo depende también del contexto, razonamiento y herramientas](https://learn.chatgpt.com/docs/pricing); los precios de API no sirven para calcular cuántos encargos incluye la suscripción.

Plantilla para un ticket con Luna:

> Ejecuta únicamente J1 en esta rama. Lee las clases indicadas y sus pruebas. Conserva API, migraciones y cambios ajenos. Corrige solo el defecto que muestre la evidencia y verifica alta, SKU repetido y versión antigua. Entrega archivos cambiados, comando ejecutado, resultado real y siguiente paso.

Plantilla para una revisión con Sol:

> Revisa exclusivamente el recorrido OrderCompletionService → ShippingClient → OrderService.complete. Comprueba transacción durante HTTP, versión obsoleta, cancelación concurrente y doble consumo. Corrige defectos demostrados y valida sus escenarios. Mantén dos desplegables y el contrato existente.

## Ayúdame a ayudarte

Al continuar, aporta **rama y commit**, **ticket**, **archivo o clase**, **comando y primer error relevante**, **resultado esperado frente al observado**. No pegues secretos. Al cerrar, guarda qué quedó verificado y cuál es el siguiente ticket; así no hace falta reauditar todo el proyecto.

Una interfaz mínima para productos, stock y pedidos puede añadirse después, como actividad opcional fuera de estas 80 horas. Primero debe funcionar y poder explicarse el recorrido backend completo.
