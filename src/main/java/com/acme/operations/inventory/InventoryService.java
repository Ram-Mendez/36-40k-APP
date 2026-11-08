package com.acme.operations.inventory;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
public class InventoryService {
    // Acceso a persistencia separado de las reglas que coordina este servicio.
    private final InventoryRepository repository;
    // Servicio de auditoría compartido para registrar las acciones de esta operación.
    private final AuditService audit;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public InventoryService(InventoryRepository repository, AuditService audit) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.repository = repository;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.audit = audit;
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Ajusta las existencias físicas y registra el motivo dentro de una misma operación.
    public void adjust(long productId, InventoryDtos.Adjustment request) {
        // Rechaza cero y cantidades fuera del rango admitido antes de tocar el inventario.
        if (request.quantity() == 0
                || request.quantity() > 1000000000L
                || request.quantity() < -1000000000L)
            // El handler devolverá 400 con INVALID_QUANTITY; la cantidad enviada no es válida.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "Adjustment must be nonzero and between -1000000000 and 1000000000");
        // Comprueba que el producto exista antes de intentar crear o bloquear su saldo.
        if (!repository.productExists(productId))
            // La ausencia del producto se comunica como 404, no como stock insuficiente.
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
        // Lee y bloquea la fila del producto hasta que termine esta transacción.
        var balance = repository.lock(productId);
        // Guardará el nuevo total físico, no la cantidad disponible ni el incremento aislado.
        long onHand;
        // Captura únicamente el desbordamiento de la suma para dar un error comprensible.
        try {
            // Suma el ajuste al saldo actual y lanza ArithmeticException si el resultado no cabe en long.
            onHand = Math.addExact(balance.onHand(), request.quantity());
        // Convierte ese desbordamiento en una excepción de negocio con HTTP 400.
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity exceeds supported range");
        }
        // Impide dejar menos existencias que las comprometidas; también evita un saldo físico negativo.
        if (onHand < balance.reserved())
            // 409 explica que el ajuste choca con las reservas actuales.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_AVAILABLE_STOCK",
                    "Adjustment would consume reserved stock or make stock negative");
        // Guarda el nuevo total físico e incrementa la versión del saldo.
        repository.updateOnHand(productId, onHand);
        // Añade el movimiento ADJUSTMENT; orderId es null porque no procede de un pedido.
        repository.insertMovement(
                productId, null, "ADJUSTMENT", request.quantity(), request.reason());
        // Registra quién ajustó el stock; saldo, movimiento y auditoría comparten transacción.
        audit.record(
                "STOCK_ADJUSTED",
                "PRODUCT",
                Long.toString(productId),
                request.quantity() + ": " + request.reason());
    }

    // Spring exige una transacción ya abierta; se une a ella y falla si no existe.
    @Transactional(propagation = Propagation.MANDATORY)
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Reserva unidades para un pedido; las cajas todavía permanecen en el almacén.
    public void reserve(long orderId, long productId, long quantity) {
        // Rechaza cantidades cero o negativas antes de bloquear datos.
        requirePositiveQuantity(quantity);
        // Bloquea el saldo del producto para que otra reserva no use los mismos disponibles.
        var balance = repository.lock(productId);
        // Disponible = físico menos reservado; compara ese resultado con lo solicitado.
        if (balance.onHand() - balance.reserved() < quantity)
            // Si no alcanza, la excepción provoca rollback de la operación que llamó a reserve.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "Insufficient available stock for product " + productId);
        // Aumenta reserved y crea la reserva vinculada al pedido en la misma transacción.
        repository.insertReservation(orderId, productId, quantity);
        // Guarda la evidencia de la reserva; un fallo aquí también deshace el cambio anterior.
        repository.insertMovement(productId, orderId, "RESERVE", quantity, "Order confirmed");
    }

    // Spring exige una transacción ya abierta; se une a ella y falla si no existe.
    @Transactional(propagation = Propagation.MANDATORY)
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Libera una reserva; ship distingue una cancelación de una salida física.
    public void release(long orderId, long productId, long quantity, boolean ship) {
        // La cantidad debe ser positiva aunque el movimiento final se registre con signo negativo.
        requirePositiveQuantity(quantity);
        // Mantiene protegido el saldo mientras comprueba y libera la reserva.
        var balance = repository.lock(productId);
        // Exige saldo reservado suficiente y borrar exactamente la reserva de este pedido y cantidad.
        if (balance.reserved() < quantity
                || repository.deleteReservation(orderId, productId, quantity) != 1)
            // Si la reserva no coincide, se aborta la transacción en vez de dejar un cambio parcial.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_MISMATCH",
                    "Reservation does not match order");
        // Siempre resta reserved; solo resta on_hand cuando ship es true y las unidades salen.
        repository.decreaseReservedAndOnHand(productId, quantity, ship ? quantity : 0);
        // Registra SHIP al enviar o RELEASE al cancelar; -quantity refleja la liberación.
        repository.insertMovement(
                productId,
                orderId,
                ship ? "SHIP" : "RELEASE",
                -quantity,
                ship ? "Order completed" : "Order cancelled");
    }

    // Centraliza la misma regla para reservar y liberar.
    private static void requirePositiveQuantity(long quantity) {
        // Cero o un número negativo no representan una cantidad válida para estas operaciones.
        if (quantity <= 0)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "Stock reservation quantity must be positive");
    }

    // Consulta el resumen por producto: físico, reservado y disponible.
    public PageResponse<InventoryDtos.Balance> balances(Long productId, int page, int size) {
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.balances(productId, page, size);
    }

    // Consulta el historial de cambios, no el saldo actual.
    public PageResponse<InventoryDtos.Movement> movements(Long productId, int page, int size) {
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.movements(productId, page, size);
    }

    // Consulta las reservas asociadas a pedidos, no realiza una reserva nueva.
    public PageResponse<InventoryDtos.Reservation> reservations(int page, int size) {
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.reservations(page, size);
    }
}
