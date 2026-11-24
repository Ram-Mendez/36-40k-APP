# INC-28 · El muelle acepta un turno dentro de otro

Dificultad estimada **7/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

El muelle `DOCK-28` ya está reservado el 2 de diciembre de 09:00 a 12:00 UTC. El almacén permite reservas consecutivas cuando una empieza justo al terminar la anterior.

1. En `POST /api/v1/warehouse-transfers/docks/{dock}/bookings`, usa `dock = DOCK-28` y envía `{"startsAt":"2026-12-02T10:00:00Z","endsAt":"2026-12-02T11:00:00Z"}`.

**Fallo observado.** El POST devuelve 200 y guarda una segunda reserva dentro de la primera.

**Para darlo por resuelto.** Usa el muelle `DOCK-28-B`, que tiene la reserva original de 09:00 a 12:00 UTC. La solicitud de 10:00 a 11:00 debe devolver `409 DOCK_OCCUPIED`. Una reserva desde `12:00Z` hasta `13:00Z` del mismo día debe responder 200.

Pon el primer breakpoint en [`TransferPracticeService.book`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
