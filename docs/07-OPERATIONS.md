# Operación local y mantenimiento

## Windows e IntelliJ IDEA

Instalar JDK 21 y Docker Desktop, habilitar su motor Linux y abrir esta carpeta seleccionando `pom.xml` como proyecto Maven en IntelliJ. Configurar Project SDK, Language level y Maven Runner JRE a Java 21; usar el Maven Wrapper del proyecto. Importar Maven y esperar a resolver dependencias.

Para ejecutar en IntelliJ, arrancar primero dependencias y crear una configuración Spring Boot para `com.acme.operations.BusinessOperationsApplication` con perfil activo `dev`. Usa puerto 8086, PostgreSQL en localhost:5436 y transporte localhost:9096. No ejecutar simultáneamente el contenedor app y esta configuración en el mismo puerto.

```powershell
docker compose up -d --build postgres shipping-service
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

O ejecutar todo en contenedores, sin instalar Maven local:

```powershell
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://localhost:8086/actuator/health/readiness
Start-Process http://localhost:8086/swagger-ui/index.html
```

Detener con `docker compose stop`; reanudar con `docker compose start`; reiniciar la aplicación con `docker compose restart app`. Después de cambiar código, usar `docker compose up -d --build app`. `docker compose down` elimina contenedores/red, conserva el volumen de datos. **No añadir `-v` si se quieren conservar los datos**.

## Configuración

| Variable | Uso |
| --- | --- |
| DB_URL | JDBC; local por defecto localhost:5436/business_operations. |
| DB_USER / DB_PASSWORD | Usuario y secreto PostgreSQL. |
| JWT_SECRET | Clave HS256 de al menos 32 bytes. |
| JWT_ACCESS_TTL / JWT_REFRESH_TTL | Duraciones ISO 8601; PT15M y P7D por defecto. |
| SERVER_PORT | Puerto HTTP, 8086. |
| SHIPPING_BASE_URL | Transporte, localhost:9096 en ejecución local. |
| SWAGGER_ENABLED | Acceso público a documentación fuera de dev/test. |
| BOOTSTRAP_ADMIN_ENABLED | true solo para el primer aprovisionamiento de una base sin usuarios. |
| BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD | Credenciales propias del administrador inicial; retirar tras aprovisionar. |

Perfil `dev`: secretos de ejemplo y datos iniciales. Perfil `test`: Testcontainers y datos de prueba. Perfil `container`: DNS de Compose para PostgreSQL y shipping-service. El Compose combina `dev,container` porque es una distribución **local de demostración**, no una receta para producción.

En otro entorno establecer secretos propios, no activar dev, aplicar las migraciones de producción y aprovisionar el primer ADMIN con las variables BOOTSTRAP_ADMIN descritas en `05-SECURITY.md`. Una base inicial de producción no crea automáticamente usuarios conocidos. El README describe los comandos de uso inmediato.

## Investigación de incidencias

```powershell
docker compose logs --tail 200 app
docker compose logs --tail 100 postgres shipping-service
docker compose exec postgres psql -U business -d business_operations
```

Cada petición devuelve `X-Request-ID`; puede enviarse uno con 1–64 caracteres alfanuméricos, guion o guion bajo. Buscar ese valor en logs y en `/api/v1/audit?requestId=...`. Los logs incluyen método, ruta, status, duración y rechazos de negocio. No incluyen bodies ni credenciales. Para SQL de comprobación consultar `03-DATABASE.md`.

401: verificar expiración, refresh ya utilizado, logout y estado del usuario. 403: verificar el rol actual. 409 de stock: comparar físicas, reservadas y reservas, no aumentar saldo sin motivo de negocio. 503 de transporte: verificar health/logs shipping-service y conectividad; el pedido debe conservar reserva y puede reintentarse.

Si Swagger y el código difieren, comprobar puerto, proceso y artefacto realmente ejecutado antes de cambiar código. En Windows: `Get-NetTCPConnection -LocalPort 8086 -State Listen`, después identificar su PID con `Get-Process -Id ...`. Revisar también `docker compose ps`. Recompilar y reconstruir únicamente la instancia pertinente.

## Salud, logs y recuperación

`/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` son públicos y no muestran detalles sensibles. Readiness incluye PostgreSQL; el transportista no bloquea la consulta de pedidos ni el login si falla. Timeouts de conexión/lectura del cliente de transporte: 2/3 segundos. Spring usa cierre graceful.

Respaldar PostgreSQL con `pg_dump` antes de cambios de datos relevantes; probar restauración en una base aislada. Nunca corregir una migración aplicada: añadir otra. Flyway rechaza checksums incoherentes. Los volúmenes son persistentes; cambiar una contraseña en variables no cambia la contraseña de una base ya inicializada.

## CI y laboratorio temporal

El workflow de GitHub Actions inicia Jenkins y Kind en un runner temporal. Jenkins ejecuta el `Jenkinsfile` versionado; su stage Test ejecuta `clean verify` una vez por servicio. Después empaqueta los dos JAR, construye dos imágenes locales, despliega PostgreSQL y ambos servicios Spring Boot, y comprueba readiness, autenticación, pedidos con cotización real, caída/recuperación de shipping y persistencia tras reiniciar la aplicación. Los resultados y logs se guardan como artifacts y el workflow elimina los recursos del runner al finalizar. Para estudiar cada herramienta y ejecutar un rollout fallido controlado, consulta [JENKINS-KIND-LAB.md](JENKINS-KIND-LAB.md).

Una edición del workflow o la ejecución en un equipo local no demuestra que GitHub Actions haya corrido. Solo el enlace al run y sus artifacts verifican Jenkins, los pods y las pruebas en GitHub.
