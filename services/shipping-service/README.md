# Shipping service

This standalone Spring Boot service quotes shipping costs in EUR. The default charge is €7.50 for orders below €200, with free shipping from €200. Configure the rate and threshold with `SHIPPING_FLAT_COST` and `SHIPPING_FREE_THRESHOLD`. Invalid values prevent startup.

## Run from the repository root

```bash
bash ./mvnw -f services/shipping-service/pom.xml -B -ntp clean verify
bash ./mvnw -f services/shipping-service/pom.xml spring-boot:run
```

Use Java 21. The shipping tests run independently of PostgreSQL and Docker.

```bash
curl -fsS -H 'X-Request-ID: demo-42' 'http://localhost:9096/quotes?orderId=42&amount=199.99'
curl -fsS 'http://localhost:9096/actuator/health/readiness'
```

The first request returns `{"reference":"QUOTE-42-199.99","cost":7.50}`. Use a positive integer for `orderId`. Supply an `amount` between `0.00` and `999999999999.99`, with at most two decimal places. Invalid input returns HTTP 400 with `application/problem+json`.

## Build the container image

Use the repository root as the build context:

```bash
docker build -f services/shipping-service/Dockerfile -t shipping-service:local .
docker build -f services/shipping-service/Dockerfile --build-arg BUILD_MODE=prebuilt -t shipping-service:verified .
```

The second command uses `services/shipping-service/target/shipping-service-1.0.0.jar` from `clean verify`. The container runs as a non-root user on port 9096. Use `/actuator/health/liveness` and `/actuator/health/readiness` for Kubernetes probes.

## Service responsibilities

`GET /quotes` returns the same quote for the same input and settings. Its reference identifies the quote inputs. The operations backend owns orders, reservations and stock. It saves the quote when it completes an order and releases the database transaction before the HTTP call.

Run this service on the internal lab network. The operations backend handles authentication and permissions. The service accepts an `X-Request-ID` containing 1–64 letters, digits, hyphens or underscores. It generates a UUID when the header is missing or invalid. The services communicate through HTTP and JSON.
