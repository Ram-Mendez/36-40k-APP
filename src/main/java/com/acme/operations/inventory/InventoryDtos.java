package com.acme.operations.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// Agrupa DTO de entrada y salida; los records transportan datos y no consultan la base automáticamente.
public final class InventoryDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private InventoryDtos() {}

    // Resumen actual por producto: existencias físicas, reservas y unidades aún disponibles.
    public record Balance(
            // Referencia al producto del catálogo, no al id del pedido.
            long productId,
            // Código de catálogo del producto.
            String sku,
            // Nombre descriptivo del producto.
            String productName,
            // Existencias físicas registradas, incluidas las unidades que están reservadas.
            long onHand,
            // Unidades comprometidas que siguen físicamente en almacén.
            long reserved,
            // Unidades ofrecibles a otro pedido: onHand menos reserved.
            long available) {}

    // Entrada de ajuste físico y motivo; el servicio comprueba rango y que no sea cero.
    public record Adjustment(@NotNull Long quantity, @NotBlank @Size(max = 500) String reason) {}

    // Movimiento histórico que explica un cambio; no representa por sí solo el saldo actual.
    public record Movement(
            // Identificador propio de este registro, distinto de las referencias a cliente o producto.
            long id,
            // Referencia al producto del catálogo, no al id del pedido.
            long productId,
            // Long admite null cuando el movimiento no procede de un pedido, como un ajuste manual.
            Long orderId,
            // Tipo de movimiento y cantidad registrada; el significado del signo depende del tipo.
            String kind,
            // Unidades de este producto en la línea.
            long quantity,
            String reason,
            // Fecha de creación del registro de movimiento.
            Instant createdAt) {}

    // Detalle de una reserva: qué pedido compromete cuántas unidades de qué producto.
    public record Reservation(long orderId, long productId, String sku, long quantity) {}
}
