## INC-005 — An order is completed while the shipping service is unavailable
**Estimated difficulty:** Difficult — microservice communication and remote errors.
**Observed symptom:** During an outage of the quoting service, orders are recorded as completed with zero shipping cost and stock is deducted.
**Affected endpoint:** `POST /api/v1/orders/{id}/complete`.
**Reproduction steps:**
1. Confirm the shipping service is running. Create a customer with `POST /api/v1/customers` and save its `id` as `<C5>`:
   ```json
   {"name":"INC005 Customer","taxId":"B5-<R>","email":"inc005@example.test","address":"5 Test Street"}
   ```  13
2. Create a product with `POST /api/v1/products` and save its `id` as `<P5>`:
   ```json
   {"sku":"B5-<R>","name":"INC005 Product","unitPrice":10.00}
   ``` 35        					36
3. Send `POST /api/v1/inventory/<P5>/adjustments`:
   ```json
   {"quantity":20,"reason":"INC005 initial stock"}
   ``` yes
   
4. Send `POST /api/v1/orders` with `{"customerId":<C5>}` and save its `id` as `<O5>`. 43
5. Send `PUT /api/v1/orders/<O5>/lines/<P5>` with `{"quantity":2}`, then send `POST /api/v1/orders/<O5>/confirm` with no body. The order should be `CONFIRMED` with `total=20.00`. YES
6. 
6. Directly request `GET http://localhost:9096/quotes?orderId=<O5>&amount=20.00`, without authentication. Confirm it returns `200` and `cost=7.50`.
7. {"reference":"QUOTE-42-20.00","cost":7.50}
7. From the repository directory, stop only the shipping service:
   ```powershell
   docker compose stop shipping-service
   docker compose ps -a shipping-service
   ```
   Confirm that the service is stopped and its readiness endpoint no longer responds. Keep the core service and PostgreSQL running. YES
8. Send `POST /api/v1/orders/<O5>/complete` with no body. Record the response,
9. {
   "id": 42,
   "customerId": 13,
   "customerName": "INC005 Customer",
   "status": "COMPLETED",
   "total": 20,
   "version": 3,
   "shippingReference": "LOCAL-42",
   "shippingCost": 0,
   "createdAt": "2026-11-05T08:52:54.859419Z",
   "updatedAt": "2026-11-05T08:59:29.285523Z",
   "lines": [
   {
   "id": 52,
   "productId": 35,
   "sku": "B5-<R>",
   "productName": "INC005 Product",
   "quantity": 2,
   "unitPrice": 10,
   "lineTotal": 20
   }
   ]
   }
   {
   "content": [
   {
   "productId": 35,
   "sku": "B5-<R>",
   "productName": "INC005 Product",
   "onHand": 18,
   "reserved": 0,
   "available": 18
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
10. then check `GET /api/v1/orders/<O5>` and `GET /api/v1/inventory?productId=<P5>`.
9. When finished, restart the service:
   ```powershell
   docker compose start --wait shipping-service
   ```
   Check its readiness again. To repeat this ticket, create new data starting from step 1.
**Expected result:** `503 Service Unavailable` with `code=SHIPPING_UNAVAILABLE`. The order remains `CONFIRMED`, with no shipping reference or cost; the product retains `onHand=20`, `reserved=2`, and `available=18`. The request should be retryable once the service is back.

**Incorrect result:** `200 OK`; the order is `COMPLETED` with a shipping reference and `shippingCost=0.00`. The product has `onHand=18`, `reserved=0`, and `available=18`, even though the shipping service was stopped.
EN /complete 
## Delivery validation

Validation performed on November 3, 2026:

| Check | Result |
| --- | --- |
| Maven compilation and packaging for both services | Passed with Java 21 |
| Both services and PostgreSQL 16.13 startup | Health checks passed |
| Flyway on a fresh database | Four migrations validated and applied; migrations unchanged |
| Five scenarios against the original baseline | Expected behavior confirmed for all five through HTTP |
| All five incidents together on the training branch | All five incorrect symptoms reproduced through HTTP |
| Independence | Five temporary copies, each with a single bug: 25 scenarios checked; in each copy, only its ticket's behavior fails |
| Persistence | Observed states persisted after restarting the core service |
| Original test suite before introducing bugs | Core: 75/75; shipping: 24/24 |
| Core test suite with the bugs | 75 tests run: 58 passed, 17 regression failures, 0 execution errors, 0 skipped |

The core tests were run with `-Dmaven.test.failure.ignore=true` so both unit and integration tests could run; this result is not considered a passing suite. Compilation was also verified separately with `-DskipTests package`.

Validation used data created through HTTP and a separate Docker project. The temporary environment was stopped when validation finished, and its volume was preserved. Temporary verification programs and source copies are not part of this delivery. Use the startup instructions in the common setup to begin the exercises.

The existing tests remain unchanged. A successful build does not mean the regression suite should pass on this branch: it contains intentional failures to investigate.
