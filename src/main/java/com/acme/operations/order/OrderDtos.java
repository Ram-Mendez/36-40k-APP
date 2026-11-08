package com.acme.operations.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Agrupa contratos de entrada y salida del pedido; no es una entidad ni un repositorio.
public final class OrderDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private OrderDtos() {}

    // Para crear basta customerId; debe ser no nulo y positivo, y el servicio comprueba que exista.
    public record Create(@NotNull @Min(1) Long customerId) {}

    // Entrada de una línea: la cantidad es positiva y limitada; el producto viene de la URL.
    public record SetLine(@NotNull @Min(1) @Max(1000000) Long quantity) {}

    // Salida de una línea: producto, unidades y precio pactado para ese pedido.
    public record Line(
            // Identificador propio de este registro, distinto de las referencias a cliente o producto.
            long id,
            // Referencia al producto del catálogo, no al id del pedido.
            long productId,
            // Código de catálogo del producto.
            String sku,
            // Nombre descriptivo del producto.
            String productName,
            // Unidades de este producto en la línea.
            long quantity,
            // Precio unitario pactado, que puede diferir del precio actual del catálogo.
            BigDecimal unitPrice,
            // Importe de esta línea: cantidad multiplicada por precio unitario.
            BigDecimal lineTotal) {}

    // Salida completa: reúne la cabecera del pedido y sus líneas, no se rellena automáticamente.
    public record Order(
            // Identificador propio de este registro, distinto de las referencias a cliente o producto.
            long id,
            // Referencia al cliente al que pertenece el pedido.
            long customerId,
            // Nombre del cliente obtenido al consultar su relación con el pedido.
            String customerName,
            // Estado del pedido; determina qué operaciones de negocio permite el servicio.
            String status,
            // Importe de las líneas del pedido; el envío tiene su campo separado.
            BigDecimal total,
            // Versión leída del recurso para detectar modificaciones posteriores.
            long version,
            // Referencia de la cotización de envío, ausente antes de completar.
            String shippingReference,
            // Coste cotizado del envío, separado del total de productos.
            BigDecimal shippingCost,
            // Instante en que se creó el registro.
            Instant createdAt,
            // Instante de la última actualización registrada.
            Instant updatedAt,
            // Lista de productos del pedido; puede estar vacía al crearlo.
            List<Line> lines) {}
}
