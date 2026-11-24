# INC-17 · La API devuelve solo un error de un formulario inválido

El formulario de ajuste envía dos campos incorrectos. La API solo informa de uno y el operador tiene que reintentar para ver el otro.

1. Haz `POST /api/v1/inventory/47/adjustments` con `{"reason":""}`. Deja `quantity` fuera del JSON.
2. Comprueba el `400 VALIDATION_ERROR`. El array `violations` solo contiene un error. El que aparece primero puede variar.
3. Tras el arreglo, la misma petición debe devolver un error para `quantity` y otro para `reason` en la misma respuesta.

Empieza en [ApiExceptionHandler.validation](../../../src/main/java/com/acme/operations/common/ApiExceptionHandler.java) y sigue la petición con el depurador.
