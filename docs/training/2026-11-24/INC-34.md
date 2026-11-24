# INC-34 · Un bulto refrigerado y dañado pasa directo a cámara

Dificultad estimada **7/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

Recepción envía a inspección cualquier bulto dañado. Los bultos refrigerados en buen estado pasan a cámara.

1. Ejecuta `POST /api/v1/warehouse-transfers/routing` con `{"damaged":false,"refrigerated":true}`. Comprueba `destination = COLD_STORAGE`.
2. Ejecuta el mismo POST con `{"damaged":true,"refrigerated":true}`.

**Fallo observado.** La segunda respuesta también indica `COLD_STORAGE`.

**Para darlo por resuelto.** Cuando hay daños, la respuesta debe indicar `INSPECTION`, incluso si el bulto es refrigerado. El primer caso debe seguir devolviendo `COLD_STORAGE` y un bulto sin ninguno de los dos atributos debe devolver `STANDARD_STORAGE`.

Pon el primer breakpoint en [`TransferPracticeController.route`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeController.java).
