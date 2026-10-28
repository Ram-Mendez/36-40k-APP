# Laboratorio temporal: Jenkins autenticado y dos servicios en Kubernetes

El laboratorio corre online en un runner desechable de GitHub Actions. Jenkins, Kind y los datos del ejercicio desaparecen al terminar. No exige mantener encendido el PC ni contratar infraestructura permanente.

## Recorrido ejecutable

1. `.github/workflows/ci.yml` reserva un runner Linux, instala Kind/kubectl y crea el clúster `backend-lab`.
2. `scripts/run-github-jenkins.sh` construye e inicia un Jenkins real; `infra/jenkins/init.groovy` crea el usuario `ram-jenkins` y el job `backend-java-lab` a partir del `Jenkinsfile` versionado.
3. Jenkins comprueba el commit del checkout y ejecuta `clean verify` una vez para el núcleo y una vez para shipping. La suite del núcleo incluye PostgreSQL real mediante Testcontainers.
4. Empaqueta dos JAR, construye dos imágenes con `BUILD_MODE=prebuilt` y las carga directamente en Kind. No publica imágenes ni necesita un registry.
5. Aplica namespace, ConfigMap, Secret temporal, volumen y PostgreSQL, además de los Deployments/Services `backend-app` y `shipping-service`. Espera sus probes antes de continuar.
6. Verifica el acceso al núcleo, crea datos del ejercicio y ejecuta `scripts/smoke-microservices.sh`: DNS interno, cotización repetible, pedido completado, stock, rechazo de doble finalización, caída de shipping, reserva conservada, recuperación y nueva finalización. La prueba también comprueba la tarifa gratuita desde 200,00 EUR y exactamente dos movimientos SHIP.
7. Reinicia el núcleo y PostgreSQL, comprueba que el cliente creado persiste y guarda informes, logs, eventos y evidencias. El paso `always()` destruye los recursos temporales incluso si una etapa falla.

## Usuario fijo de Jenkins

**Nombre: `ram-jenkins`.** El usuario se recrea automáticamente en cada runner; no es el administrador de la API Warehouse ni el usuario de PostgreSQL.

Para que su contraseña sea la misma en futuros runs de confianza:

1. Abre el repositorio en GitHub: **Settings → Secrets and variables → Actions → New repository secret**.
2. Nombre exacto: **`JENKINS_ADMIN_PASSWORD`**.
3. Valor: una contraseña propia de al menos 16 caracteres; guárdala en tu gestor de contraseñas.

La configuración lee ese Secret en ejecuciones que no son pull requests. Si falta, genera una contraseña aleatoria para ese run; los PR usan siempre esta alternativa. El nombre permanece fijo, pero la contraseña alternativa no es persistente. Esta entrega configura el aprovisionamiento; no crea ni revela un Secret de la cuenta.

Jenkins rechaza el registro de usuarios y el acceso anónimo, mantiene CSRF y autentica la CLI con un token temporal mediante archivo privado. Los archivos de autenticación están fuera del repositorio y se eliminan al terminar; no forman parte de los artifacts. Se comprueban `whoAmI`, denegación anónima y disponibilidad del emisor CSRF antes de ejecutar el pipeline.

El controlador escucha en `127.0.0.1:8080` **dentro del runner**. No es una URL pública para abrir desde el PC. La captura con el contenedor local `jenkins`, imagen `ram-jenkins` y puerto `8081:8080` corresponde a otra instancia; estos cambios no administran esa instalación local.

## Kubernetes establecido

| Recurso | Nombre y finalidad |
| --- | --- |
| Namespace | `backend-lab`, aislado para este ejercicio |
| Deployment + Service | `backend-app:8086`, API y transacciones del núcleo |
| Deployment + Service | `shipping-service:9096`, cálculo independiente de cotizaciones |
| Deployment + Service | `postgres:5432`, datos del núcleo |
| ConfigMap | `backend-config`, JDBC y URL `http://shipping-service:9096` |
| Secret | `backend-secrets`, credenciales temporales de BD, JWT y bootstrap API |
| PV + PVC | Datos conservados durante reinicios del ejercicio |

Las aplicaciones usan startup, readiness y liveness probes y límites de recursos. Shipping no tiene base de datos: su cotización es un cálculo puro. Su Service es interno al clúster y no expone autenticación de usuario ni una API pública de producción. La autorización de las operaciones de negocio sigue en el núcleo.

El PVC usa `hostPath` en el único nodo Kind. Conserva datos mientras dura el clúster; no conserva información cuando el runner y el clúster se destruyen. No representa almacenamiento de producción.

## Ejecutar y leer el resultado

Los pushes a `main` o `codex/backend-java-lab` y las pull requests activan el workflow; una rama de trabajo se prueba mediante su PR, evitando duplicar el mismo run por push y por PR. La ejecución manual usa **Actions → Backend lab with Jenkins and Kind → Run workflow**, seleccionando la rama con estos cambios y `failure_simulation=none`.

En el artifact `backend-lab-evidence`, examina:

- `jenkins-identity.json` y `jenkins-access.txt`: usuario autenticado, origen de contraseña y acceso anónimo bloqueado; sin contraseñas ni tokens.
- `jenkins-console.log`: stages y resultados Maven de ambos servicios.
- `microservices-smoke.txt`, `completed-order.json` y `recovered-order.json`: comunicación real y efectos de negocio.
- `postgres-persistence.txt`: evidencia posterior a reinicios.
- Logs de núcleo/shipping/PostgreSQL, eventos y recursos Kubernetes: diagnóstico.
- Informes Surefire/Failsafe y `cleanup.txt`: pruebas y fin del ejercicio.

El éxito del run anterior a esta separación no probaba completar pedidos en Kubernetes: faltaba el servicio carrier aunque su URL estaba configurada. El nuevo criterio exige la comunicación y los efectos de negocio descritos. Consulta [MICROSERVICES-VERIFICATION.md](MICROSERVICES-VERIFICATION.md) y el run de la rama.

## Ejercicios de fallo

`failure_simulation=image-pull` entrega a Kubernetes una etiqueta del núcleo que no se cargó en Kind. El rollout debe fallar y dejar evidencia `ImagePullBackOff`; un run rojo es el resultado esperado del ejercicio. La opción normal `none` debe terminar correctamente.

El smoke normal provoca y repara una caída controlada de shipping: escala sus réplicas a cero, espera la retirada de pods, exige HTTP 503 y stock conservado, restaura una réplica y reintenta el mismo pedido. No reintenta automáticamente peticiones de escritura.

Para el simulador antiguo de respuestas lentas o malformadas en Docker Desktop, usa el overlay explicado en [CONTROLLED-DEBUGGING.md](../work-items/CONTROLLED-DEBUGGING.md). El Compose normal ejecuta el nuevo servicio Spring Boot.

## Coste y fuentes

Se mantiene el runner estándar de un repositorio público y no se contratan servicios. El almacenamiento de artifacts se limita a un día; si cambian la visibilidad o el tipo de runner, revisa los [límites y facturación de GitHub Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

Referencias primarias: [control de acceso Jenkins](https://www.jenkins.io/doc/book/security/access-control/), [CLI autenticada](https://www.jenkins.io/doc/book/managing/cli/), [hooks de inicio](https://www.jenkins.io/doc/book/managing/groovy-hook-scripts/), [DNS de Services Kubernetes](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/) y [probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/).
