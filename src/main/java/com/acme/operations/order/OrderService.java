package com.acme.operations.order;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;
import com.acme.operations.inventory.InventoryService;
import com.acme.operations.shipping.ShippingClient;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
public class OrderService {
    // Acceso a persistencia separado de las reglas que coordina este servicio.
    private final OrderRepository repository;
    // Gestiona reservas y saldos dentro de la transacción del pedido.
    private final InventoryService inventory;
    // Servicio de auditoría compartido para registrar las acciones de esta operación.
    private final AuditService audit;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public OrderService(
            OrderRepository repository, InventoryService inventory, AuditService audit) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.repository = repository;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.inventory = inventory;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.audit = audit;
    }

    // Spring abre una lectura REPEATABLE_READ: cabecera y líneas ven una instantánea consistente.
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    // Recupera el DTO con datos generales y líneas del pedido.
    public OrderDtos.Order get(long id) {
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.get(id);
    }

    // Mantiene una visión consistente entre el conteo, las cabeceras y las líneas de esta página.
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<OrderDtos.Order> search(
            Long customerId, String status, int page, int size, String sort) {
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.search(customerId, status, page, size, sort);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Crear un pedido requiere un cliente; las líneas se añaden en operaciones posteriores.
    public OrderDtos.Order create(OrderDtos.Create request) {
        // Rechaza un cliente inexistente o inactivo antes de insertar el pedido.
        repository.requireActiveCustomer(request.customerId());
        // El repositorio inserta customer_id y devuelve el nuevo id del pedido, no el del cliente.
        long id = repository.create(request.customerId());
        // Añade la auditoría del alta dentro de la misma transacción.
        audit.record(
                "ORDER_CREATED", "ORDER", Long.toString(id), "Customer " + request.customerId());
        // Vuelve a consultar el pedido creado para devolver su DTO con valores iniciales y líneas.
        return repository.get(id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order update(long id, OrderDtos.Create request) {
        // Bloquea la cabecera y exige DRAFT antes de cambiar el cliente.
        requireState(repository.header(id, true), "DRAFT");
        // Verifica que el nuevo cliente exista y siga activo.
        repository.requireActiveCustomer(request.customerId());
        // Guarda la relación con el cliente; incrementa versión y fecha de actualización.
        repository.customer(id, request.customerId());
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record(
                "ORDER_UPDATED", "ORDER", Long.toString(id), "Customer " + request.customerId());
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.get(id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order setLine(long id, long productId, OrderDtos.SetLine request) {
        // Bloquea el pedido; solo los borradores permiten editar líneas.
        requireState(repository.header(id, true), "DRAFT");
        // Comprueba que el producto esté activo y obtiene su precio actual.
        BigDecimal currentPrice = repository.activePrice(productId);
        // Lee las líneas actuales para calcular los límites del pedido.
        var lines = repository.lines(id);
        // Busca si ese producto ya tiene línea; findFirst devuelve un Optional.
        var existing = lines.stream().filter(line -> line.productId() == productId).findFirst();
        // El límite afecta a productos distintos, no a la cantidad de unidades de una línea.
        if (existing.isEmpty() && lines.size() >= 100)
            // 409 impide añadir una línea nueva cuando ya se alcanzó el máximo.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ORDER_LINE_LIMIT",
                    "An order supports at most 100 distinct products");
        // Si la línea existe conserva su precio pactado; si es nueva usa el del catálogo.
        BigDecimal price = existing.map(OrderDtos.Line::unitPrice).orElse(currentPrice);
        // Calcula el total sustituyendo la línea afectada, sin sumarla dos veces.
        BigDecimal newTotal =
                lines.stream()
                        .filter(line -> line.productId() != productId)
                        .map(OrderDtos.Line::lineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(price.multiply(BigDecimal.valueOf(request.quantity())));
        // Compara BigDecimal para evitar superar el importe que admite la aplicación.
        if (newTotal.compareTo(new BigDecimal("999999999999.99")) > 0)
            // 400 informa que la nueva cantidad produciría un importe excesivo.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ORDER_AMOUNT_TOO_LARGE",
                    "Order amount exceeds supported maximum");
        // Inserta o actualiza la línea y recalcula el total en la base de datos.
        repository.setLine(id, productId, request.quantity(), currentPrice);
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record(
                "ORDER_LINE_SET",
                "ORDER",
                Long.toString(id),
                "Product " + productId + ", quantity " + request.quantity());
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.get(id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order removeLine(long id, long productId) {
        // Bloquea el pedido y comprueba que aún se pueda editar.
        requireState(repository.header(id, true), "DRAFT");
        // Borra la línea y recalcula el total; si no existe lanza 404.
        repository.removeLine(id, productId);
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record("ORDER_LINE_REMOVED", "ORDER", Long.toString(id), "Product " + productId);
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.get(id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Confirma el borrador y reserva todas sus líneas como una sola transacción.
    public OrderDtos.Order confirm(long id) {
        // FOR UPDATE evita que otra operación cambie este pedido mientras se confirma.
        var order = repository.header(id, true);
        // Un pedido ya confirmado, completado o cancelado no puede confirmarse otra vez.
        requireState(order, "DRAFT");
        // Vuelve a validar al cliente porque pudo desactivarse después del alta.
        repository.requireActiveCustomer(order.customerId());
        // Obtiene las líneas ordenadas por producto para bloquear inventario en un orden estable.
        var lines = repository.lines(id);
        // No se puede confirmar un pedido sin productos.
        if (lines.isEmpty())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EMPTY_ORDER", "An order must contain at least one line");
        // Procesa cada línea en orden de productId para reducir bloqueos en orden contrario.
        for (var line : lines) {
            // Verifica disponibilidad del producto en catálogo sin cambiar su precio pactado.
            repository.activePrice(line.productId());
            // Se une a esta transacción mediante MANDATORY; si una línea falla, se deshacen todas.
            inventory.reserve(id, line.productId(), line.quantity());
        }
        // Solo cambia a CONFIRMED después de reservar todas las líneas.
        repository.transition(id, "CONFIRMED", null, null);
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record(
                "ORDER_CONFIRMED",
                "ORDER",
                Long.toString(id),
                "Reserved " + lines.size() + " product(s)");
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.get(id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Cancela un borrador o un confirmado; no borra el pedido del historial.
    public OrderDtos.Order cancel(long id) {
        // Lee el estado bajo bloqueo para que la decisión no compita con una finalización.
        var order = repository.header(id, true);
        // Los pedidos completados o cancelados no admiten esta transición.
        if (!List.of("DRAFT", "CONFIRMED").contains(order.status()))
            // Devuelve un conflicto de estado sin continuar con las reservas.
            throw transitionError(order.status(), "CANCELLED");
        // Solo los confirmados tienen reservas que liberar.
        if (order.status().equals("CONFIRMED"))
            // Recorre los productos del pedido en el orden estable del repositorio.
            for (var line : repository.lines(id))
                // false libera reservado sin restar existencias físicas, porque no se ha enviado nada.
                inventory.release(id, line.productId(), line.quantity(), false);
        // Guarda CANCELLED después de liberar las reservas que correspondían.
        repository.transition(id, "CANCELLED", null, null);
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record("ORDER_CANCELLED", "ORDER", Long.toString(id), "Reservations released");
        // Devuelve los datos obtenidos por el repositorio; la consulta y el mapeo están en esa capa.
        return repository.get(id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Recibe la versión leída antes de shipping para detectar cambios durante la llamada HTTP.
    public OrderDtos.Order complete(long id, long expectedVersion, ShippingClient.Quote quote) {
        // Abre el tramo protegido leyendo la cabecera con FOR UPDATE.
        var order = repository.header(id, true);
        // Revalida CONFIRMED: la foto anterior ya podría estar desactualizada.
        requireState(order, "CONFIRMED");
        // Compara la versión actual con la que se usó para pedir la cotización.
        if (order.version() != expectedVersion)
            // 409 evita aplicar una cotización sobre un pedido que cambió mientras se esperaba.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ORDER_CHANGED",
                    "Order changed while shipping was being quoted; reload and retry");
        // Consume las reservas de cada línea dentro de esta misma transacción.
        for (var line : repository.lines(id))
            // true descuenta tanto reservado como físico, porque ahora el pedido sale.
            inventory.release(id, line.productId(), line.quantity(), true);
        // Guarda COMPLETED y los datos de shipping solo tras consumir todas las líneas.
        repository.transition(id, "COMPLETED", quote.reference(), quote.cost());
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record(
                "ORDER_COMPLETED",
                "ORDER",
                Long.toString(id),
                "Shipping quote " + quote.reference());
        // Devuelve el pedido actualizado; Spring confirma al salir correctamente por su proxy.
        return repository.get(id);
    }

    // Regla reutilizable que compara el estado actual con el requerido.
    public static void requireState(OrderRepository.Header order, String expected) {
        // Si no coincide, interrumpe la operación antes de que siga modificando datos.
        if (!order.status().equals(expected)) throw transitionError(order.status(), expected);
    }

    // Construye el error; el llamador decide lanzarlo y el handler construye la respuesta HTTP.
    private static BusinessException transitionError(String state, String target) {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "INVALID_ORDER_STATE",
                "Operation requires " + target + "; current state is " + state);
    }
}
