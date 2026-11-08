package com.acme.operations.report;

import java.math.BigDecimal;

// Agrupa DTO de entrada y salida; los records transportan datos y no consultan la base automáticamente.
public final class ReportDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private ReportDtos() {}

    // Una fila de ventas agrega pedidos completados de un mismo cliente.
    public record CustomerSales(
            // Referencia al cliente al que pertenece el pedido.
            long customerId,
            // Nombre del cliente obtenido al consultar su relación con el pedido.
            String customerName,
            // Número de pedidos completados incluidos por el filtro de fechas.
            long completedOrders,
            // Suma de importes de pedidos; el coste de envío se mantiene separado.
            BigDecimal netSales,
            // Coste cotizado del envío, separado del total de productos.
            BigDecimal shippingCost) {}

    // Una fila por producto activo cuyo disponible cumple el umbral de stock bajo.
    public record LowStock(
            // Referencia al producto del catálogo, no al id del pedido.
            long productId,
            // Código de catálogo del producto.
            String sku,
            // Nombre descriptivo del producto.
            String productName,
            // Físico registrado y parte de ese físico comprometida en reservas.
            long onHand,
            // Unidades físicas comprometidas con pedidos.
            long reserved,
            // Disponible para nuevas reservas: físico menos reservado.
            long available,
            // Conteo de registros de reserva del producto, no cantidad de unidades.
            long confirmedOrders) {}
}
