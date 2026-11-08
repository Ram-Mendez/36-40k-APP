# Práctica guiada sobre las mismas 80 horas

Este complemento desarrolla los tickets de [WAREHOUSE-ROADMAP.md](WAREHOUSE-ROADMAP.md).
El original permanece intacto. No añade horas, funcionalidades ni soluciones de ejercicios.
Base: `f1f9496cc451bd9d0ac5ea9a2deeafac063488e8`, rama de trabajo
`codex/warehouse-learning`. Lee el [estado verificado](LEARNING-AUDIT.md) antes de
atribuir una prueba o un despliegue a esta versión.

## Cómo trabajar y medir progreso

Una tarea cada vez. Primero escribe tu predicción sin consultar la solución; después
recorre el código y ejecuta la comprobación; finalmente explica causa → funcionamiento
→ validación. El mentor espera tu respuesta antes de dar pistas. Una pista debe señalar
una evidencia o pregunta, no escribir la implementación. No se resuelven aquí los tests
nuevos: los redacta el alumno y demuestra qué fallo detectarían.

Dentro de cada ticket: 10% recuperación sin apuntes, 20% recorrido, 50% práctica,
20% comprobación y explicación. Son partes de sus horas, no bloques adicionales.
Revisa un concepto al inicio de la siguiente sesión, a los 3 días y a los 7 días;
usa datos y contexto diferentes. Si no hay sesión ese día, usa la próxima.
No hay tareas programadas ni plataforma de seguimiento.

Registra por ticket una fila con: fecha, rama/commit, predicción, evidencia, ayuda recibida,
error de razonamiento, próximo repaso y nivel demostrado. Un resultado de tests es
evidencia de software; no certifica por sí solo una competencia.

| Nivel | Evidencia |
|---|---|
| Pendiente | Aún no observado; no equivale a desconocimiento. |
| Guiado | Explica o ejecuta con pistas; registra cuáles. |
| Autónomo | Explica y resuelve un caso distinto sin ayuda, con HTTP y/o SQL verificable. |
| Transferible | Repite días después con otro escenario y justifica una alternativa y su coste. |

Para cerrar un ticket, aporta: explicación propia, artefacto reproducible y caso nuevo.
Si no cabe en su tiempo, marca la competencia pendiente; no aumentes las 80 h ni certifiques
dominio por agotarlas. Las evidencias compartidas entre tickets se producen una sola vez
y se referencian; por ejemplo T3 usa la carrera de M2 e I3 conserva la evidencia de M3.

Rutas completas del repositorio en cada ficha. Las flechas identifican métodos, no
sustituyen leerlos. Los nombres de pruebas existentes son puntos de observación; no
son el enunciado resuelto del ejercicio nuevo.

## J1 · 6 h — Producto y persistencia

**Objetivo:** explicar alta y actualización desde HTTP hasta PostgreSQL, incluyendo SKU y versión.
**Previos:** clases, parámetros, DTO, petición/respuesta HTTP, clave primaria.
**Código:** `src/main/java/com/acme/operations/product/ProductController.java` (`create`, `update`);
`src/main/java/com/acme/operations/product/ProductService.java` (`create`, `update`);
`src/main/java/com/acme/operations/product/ProductRepository.java` (JPA heredado);
`src/main/java/com/acme/operations/product/Product.java` (`update`, campo version).
**Recorrido:** POST/PUT /api/v1/products → controller → service → saveAndFlush/flush → product;
el alta también inserta inventory_balance y auditoría en la transacción.
**Práctica:** crea un SKU propio; intenta repetirlo con distinto uso de mayúsculas;
actualiza desde dos copias del mismo producto. Predice qué guarda cada petición.
**Cierre:** conserva IDs, HTTP, código de error y SQL del precio/versión final; explica quién
genera el ID y quién detecta versión antigua. Observa
`BusinessIntegrationTest.staleProductUpdateIsConflictAndPreservesNewPrice` y
`duplicateProductSkuIsConflict`.
**Transferencia:** ¿bastaría comparar versiones en Java si faltara @Version en la entidad?

## J2 · 6 h — Existencias y reservas

**Objetivo:** distinguir ajuste físico, reserva, liberación y salida; explicar cada escritura.
**Previos:** J1; UPDATE/INSERT, parámetros posicionales, suma y resta.
**Código:** `src/main/java/com/acme/operations/inventory/InventoryController.java` (`adjust`);
`src/main/java/com/acme/operations/inventory/InventoryService.java` (`adjust`, `reserve`, `release`);
`src/main/java/com/acme/operations/inventory/InventoryRepository.java` (`lock`,
`updateOnHand`, `insertReservation`, `decreaseReservedAndOnHand`, `insertMovement`).
**Recorrido:** POST /api/v1/inventory/{productId}/adjustments → adjust → lock → comprobación →
saldo/movimiento/auditoría. Las reservas entran desde OrderService, no desde un POST /reserve.
**Práctica:** con un producto propio, establece 12 físicas, confirma 5 unidades,
ajusta -4 y cancela el pedido. Predice antes de ejecutar saldo, disponible, movimientos,
signos y order_id. Haz después otro intento que invada unidades reservadas.
**Cierre:** tabla antes/después respaldada por SQL; explica ADJUSTMENT frente a RESERVE
sin confundir modificar existencias con modificar un movimiento ya registrado.
Observa `BusinessIntegrationTest.adjustmentCannotConsumeReservedUnits`.
**Transferencia:** si actualizas dos columnas de una sola fila, ¿qué cuenta jdbc.update?

## J3 · 6 h — Ciclo del pedido

**Objetivo:** justificar transiciones, snapshots de precio y atomicidad del pedido completo.
**Previos:** J2; estados, transacciones y excepciones.
**Código:** `src/main/java/com/acme/operations/order/OrderController.java` (`create`, `setLine`, `confirm`, `cancel`, `complete`);
`src/main/java/com/acme/operations/order/OrderService.java` (mismos métodos);
`src/main/java/com/acme/operations/order/OrderCompletionService.java` (`complete`);
`src/main/java/com/acme/operations/order/OrderRepository.java` (`header`, `lines`, `transition`).
**Recorrido:** /api/v1/orders → borrador → líneas → confirmación → cancelación o finalización;
la finalización atraviesa HTTP hacia shipping antes de volver a una transacción local.
**Práctica:** dibuja estados y ejecuta dos pedidos distintos, uno cancelado y otro completado.
Intenta editar uno confirmado y completar dos veces el otro. Cambia el precio de catálogo
después de añadir una línea y explica el precio aplicado.
**Cierre:** HTTP y SQL prueban estado, cantidades, precio y ausencia de doble salida;
observa `BusinessIntegrationTest.orderTotalUsesDecimalSnapshotAndLineReplacement`.
**Transferencia:** ¿por qué cancelar un borrador y cancelar un confirmado producen efectos diferentes?

## T1 · 6 h — Unitarias con intención

**Objetivo:** escribir pruebas que fallen por una regla incumplida, no por una llamada irrelevante.
**Previos:** J2/J3; JUnit, Mockito y separación entre dependencia y regla.
**Código:** `src/test/java/com/acme/operations/InventoryServiceTest.java` (`successfulAdjustmentRecordsBusinessContextAfterWrite`);
`src/test/java/com/acme/operations/OrderServiceTest.java` (`staleCarrierQuoteCannotConsumeStock`);
`src/test/java/com/acme/operations/order/OrderServiceTest.java` (`staleQuoteCannotDebitStock`).
**Recorrido:** test → servicio Java directo → repositorios simulados; sin proxy Spring ni PostgreSQL.
**Práctica:** escribe tú un caso de desbordamiento al sumar una cantidad permitida a un saldo
cercano al máximo de long, y un caso límite de reserva. Selecciona primero qué efectos
deben quedar ausentes. Usa nombres completos de clase para distinguir los dos OrderServiceTest.
**Cierre:** prueba nueva pasa con el código correcto y falla ante una mutación local controlada,
revertida por ti; conserva ambas evidencias. No afirmar que Mockito demuestra rollback real.
**Transferencia:** ¿por qué un test que instancia el servicio no prueba MANDATORY?

## T2 · 6 h — Rollback real

**Objetivo:** demostrar persistencia y rollback atravesando Spring y PostgreSQL.
**Previos:** T1, J3; commit/rollback, FK, CHECK y proxy.
**Código:** `src/test/java/com/acme/operations/AbstractIntegrationTest.java` (`infrastructure`, `token`);
`src/test/java/com/acme/operations/BusinessIntegrationTest.java`
(`insufficientSecondLineRollsBackFirstReservationAndMovements`);
`src/main/java/com/acme/operations/inventory/InventoryService.java` (`reserve`).
**Recorrido:** MockMvc → filtros/controller → proxy transaccional → JDBC → PostgreSQL Testcontainers.
**Práctica:** extiende tú un caso de dos líneas donde la segunda falle, comprobando también
que no persiste auditoría de confirmación. Diseña otro que llame al bean de reserva sin
transacción, con autorización válida, para separar fallo de permisos de fallo transaccional.
**Cierre:** verifica tablas tras finalizar la petición; prueba explícitamente ausencia de cambios.
No envuelvas todo el test en @Transactional ni uses H2. Identifica el HTTP de transporte simulado.
**Transferencia:** ¿puede un test con rollback automático ocultar que el código sí hizo commit?

## T3 · 6 h — Dependencia y carrera

**Objetivo:** distinguir contrato, timeout, error remoto y concurrencia durante la cotización.
**Previos:** T2, M1/M2; concurrencia y plazos acotados.
**Código:** `src/test/java/com/acme/operations/ShippingClientTest.java` (`slowCarrierIsBoundedByReadTimeout`);
`src/test/java/com/acme/operations/BusinessIntegrationTest.java`
(`cancellationDuringCarrierCallWinsWithoutPartialShipment`, `completedOrderCannotBeCancelledOrCompletedTwice`);
`src/main/java/com/acme/operations/shipping/ShippingClient.java` (`quote`).
**Recorrido:** completar → HTTP detenido con barrera → cancelar → liberar HTTP → revalidar → comprobar BD.
**Práctica:** añade tú una prueba de dos finalizaciones concurrentes del mismo pedido, con
sincronización explícita y timeout. Diseña primero el estado final y el número de salidas aceptable.
**Cierre:** repetir la carrera con datos nuevos, comprobar stock, reservas, auditoría y movimientos;
distinguir una prueba con servidor HTTP simulado del smoke entre servicios reales.
**Transferencia:** ¿qué cambia si la llamada remota cobra dinero en lugar de consultar una tarifa?

## D1 · 6 h — Modelo y migraciones

**Objetivo:** conectar reglas del negocio con claves, nulabilidad, identidad y CHECK.
**Previos:** J1/J2; tabla/fila/columna y SQL básico.
**Código:** `src/main/resources/db/migration/V1__catalog_audit.sql`;
`src/main/resources/db/migration/V3__orders_inventory.sql`;
`src/main/java/com/acme/operations/inventory/InventoryRepository.java` (`insertMovement`).
**Recorrido:** Flyway → DDL → escrituras JDBC/JPA → restricciones.
**Práctica:** dibuja producto/saldo/pedido/línea/reserva/movimiento y realiza en una BD desechable
tres intentos inválidos: producto inexistente, reserva negativa y reserva superior al físico.
Cada intento usa su propia transacción y termina en rollback. Diseña, sin aplicarla a datos reales,
una migración aditiva hipotética; explica por qué no editar V1–V3 ni reutilizar V4 (datos dev).
**Cierre:** SQLSTATE y restricción de cada fallo, estado final sin cambios y explicación propia
de por qué order_id admite NULL en un ajuste. Distingue ID del movimiento de sus FK.
**Transferencia:** ¿puede una FK apuntar a una clave UNIQUE y permitir NULL según su definición?

## D2 · 6 h — SQL de informes

**Objetivo:** explicar agregación, límites temporales, paginación y coste de consultas.
**Previos:** D1; JOIN, GROUP BY, índices.
**Código:** `src/main/java/com/acme/operations/report/ReportController.java` (`sales`, `lowStock`);
`src/main/java/com/acme/operations/report/ReportService.java` (`sales`, `lowStock`);
`src/main/java/com/acme/operations/report/ReportRepository.java` (`sales`, `lowStock`).
**Recorrido:** GET /api/v1/reports/sales o /low-stock → servicio → count y consulta paginada.
**Práctica:** usa datos propios con pedidos en distintos estados y fechas limítrofes; predice
totales. En BD desechable, aumenta cardinalidad y recoge EXPLAIN (ANALYZE, BUFFERS) de
los SELECT antes de decidir si un índice aporta valor.
**Cierre:** resultados esperados frente a SQL, dos planes comparables con volumen documentado;
explica límite superior exclusivo y productos sin fila de saldo. No añadir índices por intuición.
**Transferencia:** ¿por qué un plan con diez filas no demuestra rendimiento con cien mil?

## D3 · 6 h — Dos sesiones PostgreSQL

**Objetivo:** observar qué fila espera, durante cuánto tiempo y quién libera el bloqueo.
**Previos:** D1, T2; transacciones y lectura/escritura concurrente.
**Código:** `src/main/java/com/acme/operations/inventory/InventoryRepository.java` (`lock`);
`src/main/java/com/acme/operations/order/OrderRepository.java` (`header`, `lines`);
`src/main/java/com/acme/operations/order/OrderService.java` (`confirm`).
**Recorrido:** sesión A bloquea saldo → B solicita la misma fila → A termina → B prosigue.
**Práctica:** en dos sesiones y datos desechables, reproduce espera sobre el mismo producto,
lectura SELECT normal y operación sobre otro producto. Configura lock_timeout local para
no dejar esperas indefinidas. Consulta pg_stat_activity y pg_blocking_pids; cierra ambas con rollback.
**Cierre:** identifica PID bloqueador, fila y límite de transacción; repite con productos solicitados
en orden distinto y explica el orden ascendente de lines. Distingue product.@Version,
inventory_balance.version y sales_order.version según su uso real.
**Transferencia:** ¿cero filas actualizadas prueba siempre conflicto de versión, o también puede faltar el ID?

## M1 · 4 h — Contrato entre dos aplicaciones

**Objetivo:** demostrar que shipping es otro proceso con un contrato HTTP propio.
**Previos:** HTTP/JSON y BigDecimal; I2 para despliegue.
**Código:** `services/shipping-service/src/main/java/com/acme/shipping/QuoteController.java` (`quote`);
`services/shipping-service/src/main/java/com/acme/shipping/QuoteService.java` (`quote`);
`src/main/java/com/acme/operations/shipping/ShippingClient.java` (`quote`).
**Recorrido:** core → GET /quotes?orderId=...&amount=... → QuoteController → QuoteService → JSON.
**Práctica:** calcula y consulta tarifas para 199,99 y 200,00; repite consultas equivalentes
y una entrada inválida. Identifica los dos POM y el artefacto ejecutable de cada uno.
**Cierre:** respuestas reales, frontera exacta y prueba de arranque independiente de shipping
sin PostgreSQL; observa `QuoteHttpTest.amountAtTheFreeShippingThresholdReturnsZeroCost`.
**Transferencia:** ¿por qué repetir esta consulta no equivale a tener idempotencia para crear envíos?

## M2 · 4 h — Transacción frente a HTTP

**Objetivo:** explicar por qué una cotización lenta no debe retener bloqueos del pedido.
**Previos:** J3, D3, M1.
**Código:** `src/main/java/com/acme/operations/order/OrderCompletionService.java` (`complete`);
`src/main/java/com/acme/operations/order/OrderService.java` (`get`, `complete`);
`src/main/java/com/acme/operations/shipping/ShippingClient.java` (`quote`).
**Recorrido:** snapshot transaccional → termina lectura → HTTP → nueva transacción → revalidar estado/versión.
**Práctica:** dibuja la línea temporal y sitúa breakpoints antes y después de HTTP;
observa la cancelación concurrente del test de T3. Explica qué garantía se perdería moviendo la
llamada HTTP al método transaccional; no cambies la implementación de producción.
**Cierre:** demuestra qué llamada atraviesa qué bean/proxy y por qué una cotización antigua no
permite completar. No confundir REQUIRED con REQUIRES_NEW ni MANDATORY con reserva previa.
**Transferencia:** ¿por qué una llamada entre métodos del mismo objeto requiere revisar el proxy?

## M3 · 4 h — Caída y recuperación

**Objetivo:** diagnosticar dependencia caída sin perder las reservas del cliente.
**Previos:** M1/M2, T3.
**Código:** `src/main/java/com/acme/operations/shipping/ShippingClient.java` (`quote`);
`infra/kubernetes/shipping.yaml`; `scripts/smoke-microservices.sh` (`expect_status`, `cleanup`).
**Recorrido:** pedido confirmado → shipping no disponible → error controlado → recuperar → completar.
**Práctica:** en laboratorio desechable, provoca la indisponibilidad y predice el estado de
pedido, físicas, reservado y movimientos. Relaciona DNS, conexiones, timeouts y logs;
recupera el servicio incluso si una comprobación falla.
**Cierre:** evidencia HTTP y SQL antes/durante/después; correlación por requestId sin tokens;
usa el smoke existente como referencia tras tu predicción. No detener servicios de otros proyectos.
**Transferencia:** ¿puede readiness del core estar UP mientras completar pedidos devuelve un error remoto?

## I1 · 2 h — Acceso Jenkins

**Objetivo:** distinguir Jenkins efímero de CI y Jenkins local persistente.
**Previos:** Docker, usuario, contraseña y token; S2 se comparte sin duplicar horas.
**Código:** `infra/jenkins/init.groovy` (aprovisionamiento y `writePrivateFile`);
`scripts/run-github-jenkins.sh` (arranque y comprobaciones de identidad/CSRF).
**Recorrido:** secreto/aleatorio → archivo privado → bootstrap → token CLI → job backend-java-lab.
**Práctica:** sigue la procedencia del secreto sin imprimirlo; localiza evidencia de identidad,
rechazo anónimo y CSRF. Describe qué sobreviviría al eliminar el runner.
**Cierre:** referencias a jenkins-access e identidad sanitizada; diferencia puerto del runner
de localhost:8081. No ejecutar el bootstrap efímero sobre el volumen del Jenkins local.
**Transferencia:** ¿un nombre de usuario fijo implica contraseña fija o URL permanente?

## I2 · 2 h — Compilar y desplegar

**Objetivo:** seguir artefactos desde código hasta imágenes y servicios en Kind.
**Previos:** Maven y Docker básico.
**Código:** `Jenkinsfile` (Test, Package, Docker Build, Kubernetes Deploy);
`.github/workflows/ci.yml`; `infra/kubernetes/backend.yaml`;
`infra/kubernetes/shipping.yaml`; `infra/kubernetes/configmap.yaml`.
**Recorrido:** Actions → Jenkins → dos builds → dos JAR → imágenes → Kind → DNS interno.
**Práctica:** identifica commit real probado, hashes de JAR/imágenes, variables de configuración
y puertos internos/externos. Explica por qué un solo verify del POM raíz no prueba shipping.
**Cierre:** mapa trazable y comandos existentes ejecutados; distingue configuración parseada de
despliegue Ready y Ready de negocio correcto. No crear clúster con el nombre de otro laboratorio activo.
**Transferencia:** ¿qué esperarías en eventos si el Deployment usa una imagen no cargada en Kind?

## I3 · 2 h — Evidencia de pipeline

**Objetivo:** distinguir una señal verde de pruebas de aceptación completas.
**Previos:** I1/I2, M3.
**Código:** `.github/workflows/ci.yml` (cleanup y artifacts);
`Jenkinsfile` (Verify y post); `scripts/smoke-microservices.sh` (`api`, `create_order`).
**Recorrido:** revisión → run → job → pasos → artifacts → negocio → limpieza.
**Práctica:** usa un run real e identifica SHA ejecutado, resultado de Maven, smoke, reinicios
y recursos finales. Contrasta cleanup.txt con errores previos y estado real del motor.
Si no hay artifact o ha expirado, registra pendiente; no infieras PASS.
**Cierre:** enlace y fecha, evidencia positiva/negativa, y límites de lo demostrado.
Los artifacts de un run anterior no validan cambios locales aún sin publicar.
**Transferencia:** ¿cómo puede un cleanup terminar verde si sus comandos toleran errores?

## S1 · 2 h — Autenticación y permisos

**Objetivo:** explicar identidad, autorización y revocación con evidencia HTTP.
**Previos:** HTTP, roles y sesión.
**Código:** `src/main/java/com/acme/operations/security/SecurityConfiguration.java`
(`securityFilterChain`, `jwtDecoder`);
`src/main/java/com/acme/operations/security/AuthService.java` (`refresh`, `logout`);
`src/test/java/com/acme/operations/security/PermissionAndErrorIntegrationTest.java`
(`readonlyCannotMutateAnyBusinessResourceOrReadAuditAndDatabaseIsUnchanged`).
**Recorrido:** Bearer → firma/issuer/expiración/sesión → permisos → controller.
**Práctica:** construye una matriz sin token, READONLY y OPERATIONS para listar y ajustar;
predice antes HTTP y cambios persistidos. Rota un refresh y comprueba los tokens anteriores.
**Cierre:** estados y códigos sin secretos, SQL sin efectos en rechazos; explica por qué errores
de filtros de seguridad no pasan necesariamente por ApiExceptionHandler.
**Transferencia:** ¿por qué un JWT firmado y no caducado puede ser rechazado?

## S2 · 2 h — Secretos y bootstrap

**Objetivo:** reconocer tres identidades independientes y sus ciclos de vida.
**Previos:** S1, I1.
**Código:** `src/main/java/com/acme/operations/security/BootstrapAdminInitializer.java` (`run`);
`src/main/resources/application.yml`; `infra/jenkins/init.groovy`;
`src/test/java/com/acme/operations/security/BootstrapAdminIntegrationTest.java`
(`provisionsEmptyDatabaseOnceWithoutResettingExistingAdministrator`).
**Recorrido:** configuración → validación → bootstrap solo si procede → persistencia/hash.
**Práctica:** clasifica credenciales Jenkins/API/PostgreSQL, origen, consumidor y permanencia.
Explica perfiles dev frente a despliegue sin dev y qué sucede al arrancar de nuevo.
**Cierre:** inventario sin valores de secretos, prueba existente ejecutada y revisión de logs;
distingue Base64 de cifrado y hash de contraseña de token legible.
**Transferencia:** ¿qué cambiarías de configuración antes de exponer el laboratorio fuera de localhost?

## A1 · 2 h — Límites y responsabilidades

**Objetivo:** describir la arquitectura real sin confundir paquetes con servicios.
**Previos:** J1–J3, M1/M2.
**Código:** `docs/02-ARCHITECTURE.md`;
`src/main/java/com/acme/operations/BusinessOperationsApplication.java` (`main`);
`services/shipping-service/src/main/java/com/acme/shipping/ShippingApplication.java` (`main`);
`compose.yaml`.
**Recorrido:** cliente → core → PostgreSQL y llamada HTTP → shipping sin base de datos.
**Práctica:** redacta tú un diagrama con propiedad de datos, transacciones, procesos y contratos;
confronta cada flecha con código. Propón correcciones al documento solo si hay una discrepancia demostrada.
**Cierre:** explica qué garantías son locales y cuáles no; señala dos builds y dos desplegables,
sin presentar cada paquete del core como microservicio.
**Transferencia:** ¿qué atomicidad perderías al separar pedidos de inventario?

## A2 · 2 h — Decisión y transferencia

**Objetivo:** argumentar una decisión con alternativas, riesgo y evidencia.
**Previos:** A1 y evidencias anteriores.
**Código:** `docs/08-ENGINEERING-DECISIONS.md`;
`src/main/java/com/acme/operations/order/OrderCompletionService.java` (`complete`);
`src/main/java/com/acme/operations/inventory/InventoryRepository.java` (`lock`).
**Recorrido:** necesidad → contrato → transacción/bloqueo → prueba → decisión documentada.
**Práctica:** escribe una decisión propia sobre mantener pedidos/inventario juntos y
shipping independiente. Defiende una alternativa sin implementarla; aplica tu razonamiento
a reservar plazas de una actividad, con datos y restricciones nuevos.
**Cierre:** contexto, opción, alternativa, coste, prueba que la sostiene y condición que
obligaría a revisarla; evalúa explicación, implementación, pruebas y diagnóstico por separado.
**Transferencia:** ¿qué evidencia pedirías antes de cambiar a bloqueo optimista?

## Ejecutar y recoger evidencia

Desde la raíz de este checkout, con Java 21; el core necesita Docker Linux:

```powershell
# Estructura pedagógica; NO ejecuta tests ni certifica dominio.
powershell -NoProfile -File scripts/check-learning.ps1
# Unitarias del core:
.\mvnw.cmd -B -ntp test
# Integración concreta (las unitarias también se ejecutan):
.\mvnw.cmd -B -ntp "-Dit.test=BusinessIntegrationTest#insufficientSecondLineRollsBackFirstReservationAndMovements" verify
# Aceptación completa de ambos desplegables, en serie:
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml clean verify
```

Revisa LASTEXITCODE y XML de target/surefire-reports, target/failsafe-reports y
services/shipping-service/target/surefire-reports. No uses -DskipTests como evidencia.
En cada informe conserva número ejecutado, fallos, errores y omitidos; no deduzcas
resultados de una ejecución antigua ni de que exista un directorio target.

Para diagnóstico: registra operación e input sanitizado → HTTP/código/requestId →
primera causa útil de log/stack trace → consulta/constraint o dependencia implicada →
hipótesis falsable → reproducción mínima → estado final. Cambia una variable cada vez.
Un fallo al conectar Docker no demuestra un defecto de negocio.

## Precisiones que conviene recuperar

- ADJUSTMENT cambia existencias físicas; RESERVE aparta unidades para un pedido.
  El movimiento registra lo ocurrido y tiene identidad propia.
- Los ? se asignan por posición. El resultado de jdbc.update cuenta filas afectadas,
  no columnas ni el valor de version.
- long tiene capacidad fija; una variable no empieza llena. Math.addExact detecta
  desbordamiento de la suma, distinto de validar el límite funcional del ajuste.
- @Transactional delimita una transacción, no solicita por sí sola SELECT FOR UPDATE.
  Las escrituras SQL también adquieren bloqueos. Los SELECT normales no quedan
  bloqueados por ese bloqueo de fila como si todos fueran SELECT FOR UPDATE.
- MANDATORY exige una transacción existente; no exige reserved previo.
  REQUIRED se une o crea si no existe. REQUIRES_NEW suspende la exterior y crea otra.
  Una llamada interna al mismo objeto no atraviesa el proxy habitual.
- El rollback por defecto de Spring se aplica a RuntimeException y Error;
  no a cualquier excepción comprobada sin configuración. Aquí BusinessException hereda RuntimeException.
- Incrementar inventory_balance.version no implementa por sí solo control optimista.
  Producto usa @Version; completar pedido revalida estado y versión tras HTTP.
- PK es única y no nula (puede ser compuesta); FK puede apuntar a PK/UNIQUE.
  Un NULL permitido en order_id significa movimiento sin pedido, no movimiento inexistente.
- Elegir locking depende de invariantes, contención y coste de reintento, no del
  número global de peticiones. No prometemos que dominar estos temas sea un porcentaje de un empleo.

## Punto de reanudación

Empezar por J2 para resolver la confusión observada entre ajuste y reserva.
Sin ejecutar nada todavía: tienes 12 físicas y 5 reservadas; propones un ajuste de -4.
Explica qué campo cambiaría, cuáles quedarían iguales y qué movimiento esperas.
Después se contrasta tu respuesta con InventoryService.adjust y PostgreSQL.

