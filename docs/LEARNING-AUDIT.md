# Auditoría y optimización del laboratorio

Fecha: 29 de octubre de 2026. Alcance: base de microservicios, pruebas, documentación,
transacciones, seguridad y circuito de entrega. No constituye certificación de producción
ni evaluación completa de la competencia del alumno.

## Base y preservación

| Elemento | Comprobación de esta revisión |
|---|---|
| Checkout inicial | `C:/Users/balar/Desktop/36-40k-APP`, rama `codex/backend-java-lab`, HEAD `88af8a26b7687063670f8cdcec193fd146427c83` |
| Cambios previos | Cuatro archivos modificados: docs/08-ENGINEERING-DECISIONS.md, ApiExceptionHandler.java, Product.java y application.yml. No copiados ni alterados por esta entrega. |
| Discrepancia | En el checkout inicial faltaban roadmap, informe de microservicios y shipping-service. No se podía aplicar allí la misión tal como estaba descrita. |
| Base localizada | Rama remota `codex/warehouse-microservices`, commit `f1f9496cc451bd9d0ac5ea9a2deeafac063488e8` |
| Rama de entrega | `codex/warehouse-learning`, creada sobre esa base en un worktree separado; cambios de esta entrega sin commit ni push |
| Worktree | `C:/Users/balar/.codex/worktrees/warehouse-learning/36-40k-APP` |
| Roadmap | `docs/WAREHOUSE-ROADMAP.md` intacto; blob Git `91378aa907ccb90b26586aae65746783dbe97ae0` |
| Contratos y migraciones | Sin cambios de producción Java, SQL, API, dependencias ni manifiestos de despliegue |

El README anterior llamaba al core monolito modular. Esta base añade otro desplegable,
no convierte sus paquetes internos en microservicios: hay core Spring Boot con PostgreSQL
y shipping Spring Boot sin BD. El simulador Java anterior es una alternativa didáctica,
no un tercer servicio de negocio requerido. Se mantienen Java 21, Maven, Docker,
GitHub Actions, Jenkins y Kind.

## Evidencia ejecutada ahora

Java local: Temurin 21.0.10; Maven Wrapper 3.9.12. Las pruebas se ejecutaron en el
worktree, sobre el código de la base indicada, sin los cambios locales del checkout inicial.

| Comprobación | Resultado |
|---|---|
| `./mvnw.cmd -B -ntp clean verify` | BUILD SUCCESS; 27 unitarias/cliente HTTP y 48 integraciones PostgreSQL; 0 fallos, errores u omitidos |
| `./mvnw.cmd -B -ntp -f services/shipping-service/pom.xml clean verify` | BUILD SUCCESS; 24 pruebas; 0 fallos, errores u omitidos; JAR generado |
| XML Surefire/Failsafe | Conteos comprobados desde los XML de ambos builds: total 99 |
| `powershell -NoProfile -File scripts/check-learning.ps1` | Roadmap intacto; 19 fichas; 80 h; 44 rutas de código existentes |
| Contraprueba del comprobador | Cambiar temporalmente J1 a 7 h fue rechazado; quitar su campo Objetivo fue rechazado; ambos cambios restaurados |
| Compatibilidad del comprobador | Ejecutado correctamente en Windows PowerShell 5.1 y PowerShell 7; ejecución Linux en CI pendiente |
| Workflow modificado | Tres bloques Bash pasan bash -n; el printf de cleanup ejecutado aisladamente emite intentos y NOT_PERFORMED, sin ejecutar borrados |
| Whitespace y alcance | git diff --check correcto; sin diff en roadmap original, src, services ni infra |
| Contratos de runtime | No modificados; los cambios posteriores a los builds son documentación, comprobador y su paso CI, y veracidad del marcador de limpieza |

Logs locales ignorados por Git: `verification-learning-core.log` y
`verification-learning-shipping.log`. XML en `target/surefire-reports/`,
`target/failsafe-reports/` y `services/shipping-service/target/surefire-reports/`.
Las 48 integraciones usan PostgreSQL 16.13 Testcontainers; MockMvc ejercita Spring/HTTP
sin socket de servidor para el core. El transporte remoto se simula mediante HttpServer
en AbstractIntegrationTest. Los tests HTTP de shipping arrancan su propia aplicación.

No se ha arrancado el Compose del usuario, cambiado su Jenkins local ni ejecutado un
nuevo despliegue Kind. El límite inicial del sandbox para Docker se resolvió ejecutando
las pruebas autorizadas con acceso al motor; no fue un fallo del backend.

## Evidencia remota consultada ahora, ejecución anterior

El [run de la propuesta](https://github.com/Ram-Mendez/36-40k-APP/actions/runs/40650817563)
terminó correctamente el 28 de octubre. Se consultaron estado, pasos, metadatos del
artifact y log completo del job; no se dedujo éxito del texto del roadmap.

- Head de la propuesta: `f1f9496cc451bd9d0ac5ea9a2deeafac063488e8`.
- SHA efectivamente ejecutado según el log de Checkout:
  `7c632c9fe5eec4e965119bb13496e1a10e9ce347`, merge temporal de PR.
- El log contiene los 27 + 48 + 24 tests sin fallos.
- Contiene smoke PASS: DNS, cotización repetida, finalización, shipping caído con 503,
  reserva conservada, recuperación, tarifa gratuita, repetición con 409 y dos movimientos SHIP.
- El job y sus pasos terminaron success; el artifact backend-lab-evidence estaba listado
  como no expirado al consultar. Su retención es de un día; puede expirar después.
- La lectura del log y los metadatos no equivale a inspeccionar todos los archivos del ZIP.
  La limpieza se intentaba con errores tolerados; no se certifica ausencia de recursos solo
  porque el paso y cleanup.txt indicaran éxito.

Ese run valida su propio merge, no los cambios actuales sin publicar. La modificación
del marcador de limpieza queda comprobada localmente y pendiente de una ejecución remota futura.

## Diagnóstico priorizado y tratamiento

| Prioridad | Hallazgo y evidencia | Impacto y tratamiento |
|---|---|---|
| Alta | Base local distinta de la propuesta; ausencia real de los dos documentos rectores | Estudiar contratos/rutas equivocados. Resuelto localizando la rama y aislando la entrega. |
| Alta | Roadmap tenía actividades y resultados, pero no fichas completas con previos, recorrido, predicción y transferencia | Confundir seguir instrucciones con autonomía. Complemento de los 19 tickets, sin sustituir el original. |
| Alta | Mockito instancia InventoryService directamente; el core integra con transporte simulado | Una prueba verde no demuestra proxy transaccional ni microservicios reales. T1/T2/T3/M1/I3 separan explícitamente qué demuestra cada nivel. |
| Alta | Workflow emitía kind_cluster_deleted=true y jenkins_container_removed=true tras comandos con `|| true` | Evidencia de limpieza potencialmente falsa. Corregido a marcadores de intento y NOT_PERFORMED; no se altera la política de eliminación. Comprobación estricta de ausencia sigue pendiente. |
| Media | InventoryRepository.lock incluye creación del saldo si falta, SELECT FOR UPDATE y mapeo; insertReservation también modifica el saldo | Los nombres no bastan para explicar efectos. J2 exige seguir todas las escrituras, parámetros y transacción antes de refactorizar. No se cambian firmas para un ejercicio. |
| Media | Hay dos OrderServiceTest en paquetes distintos, con casos parcialmente superpuestos | Confusión al seleccionar pruebas. T1 identifica ambos archivos y obliga a usar nombre completo cuando se quiera uno. Se conservan los tests. |
| Media | AuthService tiene 319 líneas; OrderRepository 276; BusinessIntegrationTest 680 y SecurityIntegrationTest 459 | Carga de lectura; no prueba un defecto por tamaño. Lectura por métodos y riesgos, sin fragmentar artificialmente reglas/transacciones. |
| Media | Faltan casos dedicados de desbordamiento de suma con cantidad válida, MANDATORY a través del proxy y dos complete simultáneos | Gaps de cobertura distintos de un bug demostrado. T1/T2/T3 dejan el diseño y código de esas pruebas al alumno; no se entregan sus soluciones. |
| Media | Shipping es una consulta sin efectos, timeout acotado y sin retry automático | Base deliberadamente sencilla. M2/M3 exigen distinguir reintento manual de una escritura remota y estudiar estado persistido. No añadir sagas ni colas. |
| Media | Seguridad prueba roles, JWT/sesión y replay; shipping interno no tiene autenticación propia | Correcto dentro del laboratorio limitado; no certifica exposición pública ni TLS/rate limiting. S1/S2 estudian frontera y secretos; no se amplía arquitectura. |
| Media | ReportRepository compone SQL con filtros y mapeos posicionales; no hay evidencia actual de planes con gran cardinalidad | D2 exige datos y planes antes de proponer índices; un test funcional no acredita rendimiento. |
| Media | Registro de progreso inexistente por competencia; preguntas anteriores repetían mucho el mismo concepto | Recuperación espaciada y transferencia dentro del tiempo del ticket, con niveles observables; sin porcentajes inventados de preparación laboral. |

No se reprodujo un defecto funcional de negocio en las suites ejecutadas. Esto no prueba
ausencia de errores. Las brechas de cobertura anteriores son escenarios pendientes,
no afirmaciones de que el sistema ya falla.

## Cambios implementados y correspondencia

| Archivo | Propósito | Tickets |
|---|---|---|
| `docs/LEARNING-LAB.md` | Fichas con objetivo, previos, archivos/métodos, recorrido, práctica, cierre y transferencia; protocolo sin soluciones | Los 19 |
| `scripts/check-learning.ps1` | Comprobar original, horas, fichas y rutas; falla si no se cumple; sin cambios en BD ni Docker | A1/A2, I2 |
| `README.md` | Punto de acceso al complemento y a la auditoría sin reemplazar guías anteriores | Todos |
| `.github/workflows/ci.yml` | Ejecutar el comprobador antes de crear Kind; marcar limpieza intentada sin afirmar eliminación verificada | I2/I3 |
| `docs/LEARNING-AUDIT.md` | Separar evidencia actual, histórica y pendiente | T2/T3, I3, A2 |

| Tickets preservados | Horas |
|---|---:|
| J1, J2, J3 | 18 |
| T1, T2, T3 | 18 |
| D1, D2, D3 | 18 |
| M1, M2, M3 | 12 |
| I1, I2, I3 | 6 |
| S1, S2 | 4 |
| A1, A2 | 4 |
| Total | 80 |

Los 15 BOP existentes siguen disponibles como referencia; no se suman como otro
itinerario obligatorio de 80 horas. Elegir uno para transferencia sustituye práctica
del ticket correspondiente y debe respetar su objetivo y tiempo.

Se descartan como trabajo actual: partir más servicios, migrar toda la persistencia,
añadir frontend, introducir un LMS, Kafka, gateways o reescribir CI. Ninguna de esas
ampliaciones resuelve primero las confusiones observadas sobre filas, reservas y transacciones.

## Siguiente acción única

J2: antes de tocar Swagger, predice qué ocurre al ajustar -4 unidades de un producto
con 12 físicas y 5 reservadas. Escribe físicas, reservado, disponible y tipo de movimiento.
Ubicación: `src/main/java/com/acme/operations/inventory/InventoryService.java`,
método `adjust`. El mentor evalúa tu respuesta antes de avanzar o mostrar el resultado.

