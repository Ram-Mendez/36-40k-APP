# Evidencia de validación de la ampliación

Fecha de ejecución: 10/11/2026, Europe/Madrid. Base anterior: `48a5b99440e4e6f3fbe125c822209d3499822b25`. Resultado de esta entrega: **PASS en ejecución local aislada**. El SHA de publicación se registra después de escribir el commit en `training/control/docs/training/EXPANSION-BASELINE.json`.

## Resultado ejecutado

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Pruebas unitarias/contratos del núcleo | PASS: 34, cero fallos/errores/omitidas | Surefire; detalle de suites y casos en `validation-summary.json` |
| Integración del núcleo con PostgreSQL real | PASS: 100, cero fallos/errores/omitidas | Failsafe, PostgreSQL 16.13 recién inicializado |
| Servicio shipping | PASS: 24, cero fallos/errores/omitidas | Suite del módulo independiente |
| Migraciones V1–V8 y arranque | PASS | Contexto real de Spring, Flyway y dos jars ejecutables |
| Readiness de ambos servicios | PASS | Puertos temporales 18086 y 19096 |
| Publicación de contratos OpenAPI nuevos | PASS | `/v3/api-docs` incluye compras, devoluciones, facturación y reposición |
| Recorrido completo por HTTP | PASS: 32 peticiones, 23 comprobaciones | `smoke-result.json`, capturado de `scripts/smoke-expansion.py` |
| Diferencias y migraciones anteriores | PASS | `git diff --check`; V1–V4 y roadmap conservados |
| Jenkins, Docker y Kind en este entorno | NOT_EXECUTED | No se dispone de daemon Docker; su workflow remoto se consulta por separado |

**Total: 158 pruebas automatizadas**, además del smoke con servicios reales. Las 42 pruebas de integración añadidas se reparten entre compras (13), devoluciones (9), facturación (11), reposición (8) y compatibilidad de catálogo (1). Se mantienen las 58 integraciones anteriores.

`source-manifest.json` contiene SHA-256 de los archivos de aplicación, pruebas, recursos, POMs y script smoke que corresponden a esta validación. Su huella agregada está en `validation-summary.json`. La documentación no interviene en esa huella para evitar una referencia circular al commit.

## Entorno y comandos

Java 21.0.13, Maven Wrapper 3.9.12, Spring Boot 3.5.12, PostgreSQL 16.13 nativo y temporal. Cada ejecución final usa un cluster nuevo; no se conectó a la base de trabajo del usuario. El arranque HTTP usa el perfil dev con datos sintéticos y un segundo cluster nuevo. El servicio shipping real devuelve una cotización de 7,50 EUR para el pedido de 20,00 EUR del smoke.

La suite estándar para un JDK 21 completo y Docker continúa siendo:

```sh
./mvnw verify
./mvnw -f services/shipping-service/pom.xml verify
```

En el ejecutor de esta entrega se usaron los mismos objetivos con:

```sh
sh ./mvnw -B -ntp \
  -Dmaven.compiler.release= -Dmaven.compiler.source=21 -Dmaven.compiler.target=21 \
  -Dtest.database.url=jdbc:postgresql://127.0.0.1:55439/postgres \
  -Dtest.database.username=business \
  '-DargLine=-javaagent:/home/agent/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar -javaagent:/home/agent/.m2/repository/org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar=destfile=target/jacoco.exec' \
  verify
```

Se suministró además un archivo temporal de configuración del proxy de dependencias mediante `-s`; no se publica porque es configuración del ejecutor. El runtime disponible no incluye `ct.sym`, por eso se especifican source/target 21 usando el compilador 21; no se rebaja el JDK ni se modifica el POM. Mockito se cargó al inicio mediante `-javaagent` porque el entorno restringe su attach dinámico. No se desactivó ninguna assertion ni prueba. Shipping se ejecutó con los mismos parámetros de compilación y agente Mockito, `-f services/shipping-service/pom.xml clean verify`.

Para el smoke se iniciaron ambos jars, con `SERVER_PORT=18086` para el núcleo, `SERVER_PORT=19096` para shipping, `SHIPPING_BASE_URL=http://127.0.0.1:19096` y PostgreSQL aislado en 55440. Después de comprobar readiness y OpenAPI:

```sh
python3 scripts/smoke-expansion.py --base-url http://127.0.0.1:18086
```

`LAB_USERNAME` y `LAB_PASSWORD` se suministraron como variables de entorno con credenciales exclusivamente de desarrollo; el script no las imprime. Los procesos temporales se detuvieron al finalizar.

## Resultado de negocio del smoke

El run real `c53c550fddab43b181e2101196bbd98a` registró compra de diez unidades, una venta de dos, devolución recuperable de una y reembolso de 10,00 EUR. Resultado final: stock físico **9**, reservado **0**, factura **27,50 EUR**, crédito **10,00 EUR**, cobro neto **17,50 EUR**, saldo **0,00 EUR**, propuesta de reposición **11** y **un único comprobante** de compra tras repetir la recepción. Los IDs reales del entorno temporal y las 23 comprobaciones están en `smoke-result.json`.

## Incidencias resueltas durante la validación

La base anterior tenía un CI remoto fallido: [run 42782787505](https://github.com/Ram-Mendez/36-40k-APP/actions/runs/42782787505), prueba de búsqueda literal del catálogo. Se corrigió el comodín añadido al escapar `_`, se restauró la ordenación por `active` y se verificaron ambos contratos.

Un intento intermedio reutilizó la base temporal de la ejecución anterior y encontró dos fixtures históricos donde el test de informe esperaba uno. La ejecución final se realizó con un cluster nuevo, como hace Testcontainers por defecto; no se relajó ni eliminó la aserción existente. También se resolvieron los requisitos de compilador/agente del ejecutor descritos arriba antes del resultado final. Solo se presenta como PASS la ejecución final completa.

La validación comprueba comportamiento del código, no certifica conocimientos del usuario, ni cierra por su cuenta sus tickets de entrenamiento. No se generó un segundo lote ni se envió correo adicional el 10/11.
