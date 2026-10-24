# Laboratorio efímero de Jenkins y Kubernetes

Este flujo practica la entrega de la aplicación existente en un runner Linux desechable. No crea otro producto, no publica una imagen y no necesita un servidor permanente. GitHub Actions inicia Docker, Kind y Jenkins; al terminar elimina el clúster y el contenedor de Jenkins.

## Qué hace cada herramienta

1. **GitHub Actions** recibe un push, una pull request o una ejecución manual. Reserva temporalmente `ubuntu-latest` y solo necesita permiso `contents: read`.
2. **Docker** construye una imagen local. El stage `Test` ejecuta una sola vez `bash ./mvnw -B -ntp clean verify`, incluidas las integraciones PostgreSQL de Testcontainers. El stage `Package` verifica el JAR y su SHA-256. El Dockerfile usa ese JAR con `BUILD_MODE=prebuilt`; el flujo normal de Docker Compose conserva la construcción desde fuentes.
3. **Jenkins** se construye y arranca dentro del mismo runner. `infra/jenkins/init.groovy` crea un job temporal cuyo script proviene del `Jenkinsfile` versionado. El job recorre `Checkout`, `Test`, `Package`, `Docker Build`, `Kubernetes Deploy` y `Verify`. Jenkins y Actions no ejecutan dos veces Maven: la suite completa vive en el stage `Test` de Jenkins.
4. **Kind** inicia un clúster Kubernetes de un nodo dentro del Docker del runner. No hay registro externo: Jenkins carga la imagen construida directamente al nodo.
5. **Kubernetes** aplica ConfigMap, Secret temporal, volumen local, PostgreSQL y la aplicación. El nombre, la clave JWT y las contraseñas se generan durante el pipeline; no están en el repositorio. El administrador temporal se crea con el inicializador de bootstrap existente.
6. **Verify** espera los dos deployments Ready y consulta `/actuator/health/readiness`. Comprueba que una ruta protegida responde 401 sin token, obtiene un token, crea un cliente, reinicia la aplicación y después el pod de PostgreSQL. Finalmente confirma por SQL que el cliente permanece en la base y que readiness vuelve a `UP`. El PVC local conserva los datos durante el escenario.
7. Los informes Maven, consola de Jenkins, logs, eventos, estado de pods y consulta de persistencia se adjuntan como artifact. El paso `always()` destruye namespace, clúster, contenedor e imágenes locales incluso si falla una etapa.

## Coste y límites

La infraestructura creada por el flujo solo vive durante el job y no se expone como servicio de producción. Kind, Docker, Kubernetes y Jenkins son software libre. El workflow no configura proveedores cloud, registros, dominios ni despliegues permanentes. Este repositorio es público y `ubuntu-latest` es un runner estándar; GitHub documenta ese uso como gratuito e ilimitado para repositorios públicos ([runners](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job), [facturación](https://docs.github.com/en/billing/concepts/product-billing/github-actions)). Los artifacts se conservan un día para reducir almacenamiento. Si el repositorio pasa a privado, se aplican la cuota y la facturación de la cuenta. No se solicita ni se necesita un secreto de aplicación de GitHub.

Los secretos de Kubernetes se codifican como Secret de ejecución; sirven para el ejercicio y desaparecen al destruir el clúster. El PVC usa `hostPath` en el nodo único de Kind, así que no representa almacenamiento productivo ni conserva datos tras acabar el runner.

## Practicar un fallo controlado

En GitHub, abre **Actions → Backend lab with Jenkins and Kind → Run workflow** y elige `image-pull`. Jenkins carga una imagen válida, pero entrega al Deployment una etiqueta distinta que no está en Kind. Kubernetes debe mostrar `ImagePullBackOff`; el rollout falla y Jenkins registra el stage fallido. Aun así, el workflow guarda eventos, estado final y logs antes de limpiar el clúster. La opción por defecto `none` ejecuta el escenario correcto.

Para estudiar otros fallos localmente, cambia temporalmente el nombre del Secret referenciado por un Deployment, la URL JDBC del ConfigMap o la ruta HTTP de readiness. Observa eventos y logs con `kubectl -n backend-lab describe pod`, `kubectl -n backend-lab get events` y `kubectl -n backend-lab logs deployment/backend-app`. Revertir el cambio permite repetir el flujo sin modificar las migraciones ni el comportamiento funcional.

## Archivos del flujo

- `.github/workflows/ci.yml`: runner temporal, herramientas, creación y limpieza del clúster y artifacts.
- `scripts/run-github-jenkins.sh`: imagen y arranque temporal del controlador Jenkins, y ejecución bloqueante del job.
- `infra/jenkins/Dockerfile` y `infra/jenkins/init.groovy`: plugins y job de pipeline efímero.
- `Jenkinsfile`: stages ejecutables y evidencia de cada etapa.
- `infra/kubernetes/`: namespace, configuración, volúmenes, PostgreSQL, aplicación, Services y probes.
