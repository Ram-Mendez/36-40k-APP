# INC-21 · El alta rechaza a un proveedor con plazo de 45 días

Compras necesita dar de alta a un proveedor con un plazo de 45 días. El contrato admite de 0 a 365 días.

1. Ejecuta `POST /api/v1/suppliers` con `{"name":"Importaciones de práctica","taxId":"T24-LEAD-DEMO","email":"import@example.test","leadTimeDays":45}`.
2. La API devuelve `400 VALIDATION_ERROR`. En `violations`, el plazo aparece limitado a 30 días.
3. Tras el arreglo, debe responder `201` con `leadTimeDays = 45`. Comprueba también que `366` se rechace usando otro `taxId`.

Empieza en [SupplierController.create](../../../src/main/java/com/acme/operations/procurement/SupplierController.java) y sigue la petición con el depurador.
