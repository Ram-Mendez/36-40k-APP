# Shipping service

Servicio Spring Boot independiente del núcleo de Warehouse. Calcula una cotización de transporte en EUR: **7,50** por debajo de **200,00**, y **0,00** desde ese importe. La tarifa y el umbral se configuran con `SHIPPING_FLAT_COST` y `SHIPPING_FREE_THRESHOLD`; valores negativos, fuera de rango o con exceso de decimales impiden arrancar.

## Ejecutar desde la raíz del repositorio

```bash
bash ./mvnw -f services/shipping-service/pom.xml -B -ntp clean verify
bash ./mvnw -f services/shipping-service/pom.xml spring-boot:run
```

Requiere Java 21. Las pruebas de este servicio no necesitan PostgreSQL ni Docker.

```bash
curl -fsS -H 'X-Request-ID: demo-42' 'http://localhost:9096/quotes?orderId=42&amount=199.99'
curl -fsS 'http://localhost:9096/actuator/health/readiness'
```

La primera petición devuelve `{"reference":"QUOTE-42-199.99","cost":7.50}`. `orderId` debe ser un entero positivo y `amount` estar entre `0.00` y `999999999999.99`, con un máximo de dos decimales. Una entrada inválida devuelve HTTP 400 con `application/problem+json`.

## Construir la imagen

El contexto es la raíz del repositorio:

```bash
docker build -f services/shipping-service/Dockerfile -t shipping-service:local .
docker build -f services/shipping-service/Dockerfile --build-arg BUILD_MODE=prebuilt -t shipping-service:verified .
```

La segunda opción reutiliza `services/shipping-service/target/shipping-service-1.0.0.jar` generado por `clean verify`. El proceso de la imagen se ejecuta como usuario no root y escucha en 9096. Kubernetes debe usar las rutas `/actuator/health/liveness` y `/actuator/health/readiness` para sus probes.

## Responsabilidad y límites

`GET /quotes` es un cálculo puro y repetible para la misma entrada y configuración: no crea envíos, no reserva mercancía, no conserva cotizaciones y no lee la base de datos de Warehouse. La referencia identifica la entrada de la cotización, no un número de seguimiento de un transportista real. El núcleo mantiene el pedido y guarda el resultado cuando lo completa; ninguna transacción de base de datos queda abierta durante la llamada HTTP.

El servicio está pensado para la red interna del laboratorio, sin autenticación propia ni publicación en Internet. El núcleo conserva la autenticación y los permisos de usuario. La cabecera `X-Request-ID` se acepta solo si contiene de 1 a 64 letras, dígitos, guiones o guiones bajos; en otro caso se genera un UUID antes de registrar o devolver el identificador. No hay dependencia compartida de Java entre los dos servicios: su frontera es HTTP/JSON.
