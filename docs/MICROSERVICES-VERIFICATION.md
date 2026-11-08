# Verificación de la separación mínima

Fecha de preparación: **28 de octubre de 2026**. Base revisada: `62890b1b5c7e6ae819623db692d8f23036873d5b`, rama `codex/backend-java-lab`. Propuesta: `codex/warehouse-microservices`.

## Resultados ejecutados antes de abrir la propuesta

| Comprobación | Resultado |
| --- | --- |
| Núcleo: `bash ./mvnw -B -ntp test` con Java 21 | **27 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS** |
| Shipping: `bash ./mvnw -B -ntp -f services/shipping-service/pom.xml clean verify` con Java 21 | **24 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS; JAR generado** |
| Shell del runner, smoke y seis bloques shell del Jenkinsfile | Sintaxis Bash correcta |
| Compose, manifiestos Kubernetes, workflow y ambos POM | YAML/XML parseados correctamente |
| Diff | Sin errores de whitespace |

Se utilizó Java 21.0.13 en un directorio temporal; la resolución Maven necesitó el proxy y certificados de confianza del entorno de revisión. Esa configuración temporal no forma parte del repositorio ni cambia las instrucciones normales del usuario.

El entorno de revisión no dispone de Docker/Kind: las **48 integraciones PostgreSQL** del núcleo y el despliegue real se verifican en el runner de GitHub. No se han omitido ni deshabilitado en el pipeline. La suite prevista allí es **75 casos originales + 24 de shipping = 99**; el resultado ejecutado y la revisión concreta se comprueban en el run, no se deducen de ese total previsto.

## Evidencia del runner

Consulta [Actions para esta rama](https://github.com/Ram-Mendez/36-40k-APP/actions?query=branch%3Acodex%2Fwarehouse-microservices) y los checks de su pull request. El commit de `evidence/checkout.txt` identifica exactamente lo ejecutado; en un PR GitHub puede probar el merge temporal, no solo su head.

La aceptación exige:

1. Autenticación Jenkins como `ram-jenkins`, rechazo anónimo y CSRF activo.
2. Los dos builds Maven completos y las dos imágenes construidas desde sus JAR verificados.
3. PostgreSQL, `backend-app` y `shipping-service` disponibles en Kind.
4. `microservices-smoke.txt` con `result=PASS`: DNS interno, consulta repetible, finalización real, caída de shipping con HTTP 503, reserva conservada, recuperación, envío gratuito en el umbral y doble finalización rechazada.
5. Dos movimientos SHIP para los dos pedidos, stock final 7, reservas 0 y persistencia posterior a reinicios.
6. Evidencias subidas y eliminación del clúster y Jenkins al terminar.

La opción deliberada `image-pull` debe fallar el rollout; un fallo en ese ejercicio no equivale al resultado esperado del run normal.

## Hallazgo que corrige esta entrega

El [run previo del 24 de octubre](https://github.com/Ram-Mendez/36-40k-APP/actions/runs/40094748590) fue correcto para sus comprobaciones. Su ConfigMap apuntaba a `carrier:9096`, pero faltaban Deployment/Service e imagen de carrier. El smoke solo verificaba autenticación, cliente y persistencia; no completaba un pedido. Por eso aquel verde no demostraba comunicación entre servicios.

Ahora hay un servicio Spring Boot de cotizaciones independiente, DNS configurado y un smoke con efecto de negocio. El núcleo sigue siendo amplio y modular: catálogo, pedidos e inventario no se presentan como microservicios separados. Las migraciones y transacciones existentes se conservan.

## Límite de Jenkins

El usuario tiene nombre fijo. La contraseña solo se conserva entre runs de confianza cuando el propietario configure `JENKINS_ADMIN_PASSWORD` en GitHub; los PR y runs sin ese Secret usan una contraseña aleatoria temporal. No se ha modificado el Jenkins local mostrado en Docker Desktop ni se ofrece una URL Jenkins permanente.
