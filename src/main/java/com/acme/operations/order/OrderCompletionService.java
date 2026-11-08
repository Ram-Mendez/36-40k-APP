package com.acme.operations.order;

import com.acme.operations.common.BusinessException;
import com.acme.operations.shipping.ShippingClient;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
// Coordina la consulta al transportista y la finalización; son dos pasos separados.
public class OrderCompletionService {
    // Servicio que lee el pedido y ejecuta sus cambios dentro de transacciones.
    private final OrderService orders;
    // Cliente HTTP que obtiene la cotización del transportista.
    private final ShippingClient shipping;

    // Spring inyecta ambos servicios al crear este objeto mediante su único constructor.
    public OrderCompletionService(OrderService orders, ShippingClient shipping) {
        // Conserva la referencia al servicio de pedidos para usarla después.
        this.orders = orders;
        // Conserva el cliente HTTP; aquí todavía no se envía ninguna petición.
        this.shipping = shipping;
    }

    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Devuelve el DTO del pedido completo; no convierte el id del cliente en un pedido.
    public OrderDtos.Order complete(long id) {
        // Obtiene una foto del pedido. La transacción de lectura termina al volver en el flujo normal.
        var snapshot = orders.get(id);
        // Solo permite continuar si esa foto muestra un pedido confirmado.
        if (!snapshot.status().equals("CONFIRMED"))
            // Interrumpe el flujo con un error de negocio; ApiExceptionHandler lo transforma en HTTP.
            throw new BusinessException(
                    // 409 indica conflicto con el estado del pedido; código y mensaje explican la causa.
                    HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATE",
                    "Only a confirmed order can be completed");
        // La cotización es de solo lectura en el transportista: pedirla otra vez no crea un envío.
        // Consulta shipping sin abrir aquí una transacción; si falla, no se llega a completar el pedido.
        var quote = shipping.quote(snapshot.id(), snapshot.total());
        // El otro servicio abre la transacción, bloquea y vuelve a comprobar estado y versión antes de guardar.
        return orders.complete(id, snapshot.version(), quote);
    }
// Fin del servicio: las transacciones las gestionan los métodos de OrderService llamados arriba.
}
