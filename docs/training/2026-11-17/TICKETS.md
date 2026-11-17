# Batch 17/11 — incidencias 4/5

Dificultad estimada para tu nivel actual. No se mide por las líneas del arreglo: se exige explicar el fallo, descartar otra causa y comprobar los controles. Trabaja con Application + Debug y las peticiones de [batch-2026-11-17.http](../../../http/batch-2026-11-17.http). Backend 38088, Shipping 39098. Los IDs y las versiones se guardan automáticamente en el archivo HTTP.

## 01 — La cotización recuerda un importe antiguo

**Tema:** estado en memoria, caché e identidad de una petición. **Dificultad estimada:** 4/5.

Un comercial vuelve a cotizar una compra después de aumentar el importe. El pedido no se ha enviado: consultar `/quotes` solo calcula una tarifa. Desde 200 € el envío es gratis.

1. En Shipping, consulta `/quotes?orderId=170101&amount=199.99`.
2. Sin reiniciar Shipping, consulta el mismo `orderId` con `amount=200.00`.
3. Consulta otro ID, `170102`, directamente con `amount=200.00`.
4. Vuelve al primer ID con `amount=199.99`.

**Fallo preparado:** el paso 2 conserva el precio y la referencia anteriores, mientras que un ID nuevo sí recibe envío gratis. Reiniciar Shipping cambia el resultado del diagnóstico: no lo reinicies entre los pasos. Para repetir sin reiniciar usa dos IDs nuevos.

**Esperado:** cada respuesta corresponde al importe solicitado ahora; 199,99 cuesta 7,50, 200 cuesta cero. La referencia contiene el importe actual. Repetir la misma consulta produce el mismo resultado; `200` y `200.00` son equivalentes. El historial de otro pedido no influye.

**Entrega:** explica por qué una comparación de precios correcta puede devolver una respuesta antigua; prueba cambios en ambos sentidos y otro ID. No basta con probar un pedido nuevo cada vez.

## 02 — Dos pestañas pisan el precio del producto

**Tema:** edición concurrente, versión del DTO y persistencia JPA. **Dificultad estimada:** 4/5.

Dos personas abren la misma ficha. A sube el precio a 20 €. B conserva la ficha antigua y guarda 30 € sin haber visto el cambio de A. Simulamos ambas pestañas enviando versiones guardadas; no necesitas lanzar llamadas simultáneas.

1. Crea un producto nuevo de 10 € y guarda `id`, `sku` y `version` como la versión leída por A y B.
2. A hace `PUT /api/v1/products/{id}` con precio 20 y esa versión.
3. B hace el mismo PUT con precio 30 y la versión antigua.
4. Consulta de nuevo el producto. Compara la versión inicial, la respuesta de A y el estado final.

**Fallo preparado:** ambos PUT se aceptan y B sobrescribe a A. El servicio contiene una comprobación de versión, pero la protección efectiva no funciona.

**Esperado:** A recibe 200 y una versión incrementada; B recibe `409 CONCURRENT_MODIFICATION`. El precio queda en 20. Una nueva lectura permite editar con la versión actual. La petición rechazada no genera una modificación ni una auditoría de actualización.

**Entrega:** explica qué controla la versión recibida y qué controla JPA durante la escritura; comprueba persistencia después del rechazo. No elimines el control de versión ni fuerces una versión en el JSON de respuesta.

## 03 — Se detecta la reutilización del refresh, pero la sesión sigue viva

**Tema:** seguridad, rotación de tokens y límites transaccionales. **Dificultad estimada:** 4/5.

El `accessToken` sirve para consultar recursos. El `refreshToken` renueva la sesión y solo puede utilizarse una vez. Reutilizarlo debe revocar también la sesión sucesora de esa misma familia.

1. Haz login y guarda el refresh original **R1**.
2. Envía R1 a `POST /api/v1/auth/refresh`; guarda el access nuevo **A2** y el refresh nuevo **R2**.
3. Comprueba que A2 permite consultar `/api/v1/products`.
4. Envía de nuevo **R1**, no R2: debe devolver 401 por reutilización.
5. Consulta productos usando **A2** otra vez. Como control, abre una sesión nueva mediante login.

**Fallo preparado:** el paso 4 devuelve 401, pero el 5 sigue devolviendo 200. El rechazo HTTP parece correcto mientras la revocación no queda aplicada.

**Esperado:** después de reutilizar R1, A2 y R2 se rechazan. Una familia creada mediante otro login sigue funcionando. La revocación y el evento de reutilización deben quedar persistidos.

**Entrega:** demuestra por qué ver un 401 no prueba que se haya revocado una sesión; sigue la escritura y el final de la transacción. El archivo HTTP conserva R1 para que no lo sustituyas accidentalmente.

## 04 — Shipping vuelve, pero Backend no se recupera

**Tema:** ciclo de vida de un cliente HTTP y recuperación de dependencias. **Dificultad estimada:** 4/5.

Un corte temporal del servicio de envíos debe permitir reintentar cuando se recupera. Un pedido confirmado conserva las unidades reservadas hasta que la cotización funciona.

1. Reinicia Backend al comenzar este ejercicio. Mantén Shipping en marcha.
2. Con el bloque 04 crea un pedido nuevo del cliente inicial y añade dos cajas de `GLOVE-100`; confirma el pedido. Su total es 49,80 €.
3. Detén **solo Shipping LAB 17-11** desde IntelliJ. Intenta completar el pedido: debe devolver 503.
4. Arranca Shipping otra vez. Confirma que `http://localhost:39098/actuator/health/readiness` devuelve UP y una consulta directa a `/quotes` devuelve 200.
5. Sin reiniciar Backend, vuelve a completar **el mismo pedido confirmado**.

**Fallo preparado:** el paso 5 vuelve a dar 503 y Shipping no recibe una nueva consulta. Reiniciar Backend permite completar el pedido.

**Esperado:** una caída no invalida permanentemente el cliente; al recuperarse Shipping, reintentar completa el pedido una sola vez. Antes del éxito no se consume stock ni se pierde la reserva. No añadas reintentos infinitos ni soluciones que devuelvan cotizaciones ficticias.

**Entrega:** diferencia conexión rechazada, servicio recuperado y estado interno del cliente; comprueba si el segundo intento llega por la red. Al terminar reinicia Backend si no has corregido todavía el fallo, para no interferir con otros tickets.

## 05 — Datos reales reaparecen después de cerrar sesión

**Tema:** Angular, respuestas asíncronas y cambios de sesión. **Dificultad estimada:** 4/5.

La pantalla está sincronizando datos reales cuando sales de la sesión. Debe quedarse mostrando la demo. Una respuesta iniciada antes del logout llega más tarde y contamina la pantalla actual.

Para reproducirlo sin depender de la velocidad de tu equipo, el proxy de prueba retiene la primera respuesta real de productos durante diez segundos. No modifica su cuerpo ni guarda credenciales.

1. Deja Backend y Shipping arrancados. Detén el servidor Angular si estaba ejecutándose en 4201.
2. En un terminal de la carpeta `frontend`: `node scripts/batch17-delayed-proxy.mjs`.
3. En otro terminal de `frontend`: `npm run ng -- serve --proxy-config proxy.batch17-race.json --port 4201`.
4. Abre `http://localhost:4201`, entra en **Settings** y cierra cualquier sesión anterior. Reinicia el proxy para rearmar el retraso si ya apareció el mensaje de retención.
5. Inicia sesión. Cuando el proxy indique `Product response held`, pulsa **Sign out** en Settings antes de diez segundos.
6. Observa la demo inmediatamente después del logout y después de que termine el retraso. Entra en Products: reaparecen productos reales del almacén, aunque la pantalla siga en demo. Los reales incluyen `GLOVE-100`; la demo tiene otro catálogo.

**Esperado:** la respuesta de la sesión anterior no puede modificar ninguna lista de la nueva pantalla. Las respuestas de una carga todavía vigente sí deben mostrarse. No resuelvas el problema ocultando productos o dejando de actualizar siempre.

**Control determinista adicional:** `node scripts/test-batch17.mjs` controla el orden de entrega sin sleeps: carga real, cambio a demo, respuesta antigua. Incluye un control que exige aceptar respuestas de la carga actual. El test utiliza el servicio Angular real y su backend HTTP de pruebas.

**Entrega:** explica por qué una respuesta HTTP 200 puede ser demasiado antigua para aplicarla; protege todas las listas y el estado de carga. Para volver al proxy normal, detén ambos procesos del ejercicio y usa `npm run lab -- --port 4201`.
