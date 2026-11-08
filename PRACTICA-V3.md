# Mesa de operaciones · Práctica V3

**Página de equipo · Soporte de aplicaciones.** La distribuidora ha recibido cinco incidencias. Puedes cogerlas en cualquier orden. Cada ticket contiene el relato del usuario, una muestra del fallo y los criterios de aceptación. Tú decides qué inspeccionar, cómo corregirlo y qué prueba lo protegerá.

| Ticket | Cola | Incidencia |
| --- | --- | --- |
| [INC-011](practica-v3/INC-011.md) | Catálogo | La búsqueda de una referencia devuelve dos etiquetas |
| [INC-012](practica-v3/INC-012.md) | Almacén | El diario de movimientos no cuadra con el recuento |
| [INC-013](practica-v3/INC-013.md) | Pedidos | La cabecera del presupuesto suma menos que sus líneas |
| [INC-014](practica-v3/INC-014.md) | Atención al cliente | Un usuario de consulta ve el pedido en la lista y recibe un 403 al abrirlo |
| [INC-015](practica-v3/INC-015.md) | Expediciones | Al completar un pedido se aplica una tarifa distinta a la cotizada |

## Arranque desde IntelliJ

1. Abre la carpeta **36-40k-DEBUG-V3** y selecciona **JDK 21**. Carga el `pom.xml` raíz y el de `services/shipping-service` como proyectos Maven. Esta práctica tiene su propia base de datos.
2. Desde la terminal de esa carpeta, ejecuta `docker compose -f compose.autonomia.yaml up -d --wait`. En la ventana Database puedes conectarte a `localhost:25436/autonomia_v3`, usuario `business`, contraseña local `business_dev_only`.
3. Ejecuta estas dos clases desde la copia V3 con los siguientes **Program arguments**. En ambos casos puedes usar **Debug**.

| Clase principal | Program arguments (una línea por aplicación) |
| --- | --- |
| `com.acme.shipping.ShippingApplication` | `--server.port=29096` |
| `com.acme.operations.BusinessOperationsApplication` | `--spring.profiles.active=dev --server.port=28086 --spring.datasource.url=jdbc:postgresql://localhost:25436/autonomia_v3 --spring.datasource.username=business --spring.datasource.password=business_dev_only --shipping.base-url=http://localhost:29096` |

4. Abre [Swagger del backend](http://localhost:28086/swagger-ui/index.html). El [estado de shipping](http://localhost:29096/actuator/health/readiness) debe indicar `UP` para INC-015.
5. En `POST /api/v1/auth/login` usa `{"username":"operations","password":"DevPassword123!"}`. Copia el valor de `accessToken` en **Authorize**. Para comprobar el perfil de consulta, repite con `readonly` y la misma contraseña local.

Para parar la base conservando tus datos, ejecuta `docker compose -f compose.autonomia.yaml stop`. Al volver, repite el paso 2.

## Operaciones habituales

Los identificadores entre llaves en las rutas representan los números que devuelve tu API. Para preparar datos nuevos elige un sufijo distinto para los SKU de cada intento. En Swagger, escribe la búsqueda tal como aparece en la etiqueta.

| Operación | Petición | Ejemplo de cuerpo |
| --- | --- | --- |
| Alta de producto | `POST /api/v1/products` | `{"sku":"LAB-A01","name":"Muestra","unitPrice":6.00}` |
| Leer el saldo o diario de un producto | `GET /api/v1/inventory?productId={id}` o `GET /api/v1/inventory/movements?productId={id}` | Sin cuerpo |
| Ajustar stock | `POST /api/v1/inventory/{id}/adjustments` | `{"quantity":12,"reason":"Recuento inicial"}` |
| Elegir un cliente activo | `GET /api/v1/customers?active=true` | Usa un `id` de `content` |
| Crear pedido | `POST /api/v1/orders` | `{"customerId":1}` (sustituye 1 por tu cliente) |
| Fijar cantidad de una línea | `PUT /api/v1/orders/{pedido}/lines/{producto}` | `{"quantity":2}` |
| Ver el pedido | `GET /api/v1/orders/{pedido}` | Sin cuerpo |
| Confirmar o completar | `POST /api/v1/orders/{pedido}/confirm` y después `POST /api/v1/orders/{pedido}/complete` | Sin cuerpo |

## Acuerdos de negocio

- El catálogo admite búsquedas parciales por nombre o SKU sin distinguir mayúsculas. Los signos que forman parte de una etiqueta tienen valor literal.
- El saldo físico de un producto nuevo empieza en cero. En un ajuste, el diario conserva la cantidad **con el signo** que introdujo el almacén.
- Cada línea de un pedido aporta su precio por su cantidad. El importe de cabecera debe cuadrar con todas sus líneas.
- El perfil `READONLY` puede listar y consultar pedidos. Crear o modificar pedidos requiere `OPERATIONS` o `ADMIN`.
- Con las tarifas de desarrollo, el porte cuesta 7,50 € por debajo de 200,00 € y es gratuito desde 200,00 €. La referencia de shipping recoge el identificador del pedido y el importe cotizado con dos decimales.

## Entrega de un ticket

Al abrir la práctica pasan las 106 pruebas existentes del backend y de shipping. Los cinco síntomas se han reproducido por HTTP con PostgreSQL. Tu prueba nueva debe detectar el comportamiento que se escapó a esa batería.

Deja en el ticket una nota breve con la petición que reproduce el síntoma, tu primera hipótesis y la observación que la confirmó o descartó. Evita pegar tokens de sesión.

Añade una prueba automatizada que reproduzca la incidencia. Ejecútala antes de corregir para ver fallar la aserción de negocio y después con el cambio aplicado. Registra el comando, el resultado antes/después y un caso de control que siga funcionando. Desde la raíz, `.\mvnw.cmd verify` ejecuta las pruebas del backend con PostgreSQL de Testcontainers y `.\mvnw.cmd -f services/shipping-service/pom.xml verify` las de shipping. Para registrar el avance de un ticket puedes ejecutar solo su prueba en IntelliJ.
