package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Casos de integración: ejecutan rutas reales de Spring y verifican datos en PostgreSQL de Testcontainers.
class BusinessIntegrationTest extends AbstractIntegrationTest {
    // Cabecera de autorización que se reutiliza en las peticiones de cada caso.
    private String bearer;

    // Antes de cada prueba obtiene un token de administrador y restablece el transportista simulado.
    @BeforeEach
    void authenticate() throws Exception {
        bearer = "Bearer " + token("admin");
        CARRIER_STATUS.set(200);
    }

    // Envía una petición con autenticación y comprueba el estado HTTP esperado.
    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus)
            throws Exception {
        // Adjunta el token a la petición preparada por cada prueba.
        request.header("Authorization", bearer);
        // Si hay cuerpo, lo convierte a JSON; una petición sin cuerpo no añade contenido.
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        // Ejecuta la petición a través de MockMvc y obtiene la respuesta de Spring.
        var result = mvc.perform(request).andReturn().getResponse();
        // Si el estado no coincide, muestra también el cuerpo para facilitar el diagnóstico.
        assertThat(result.getStatus())
                .withFailMessage("HTTP %s: %s", result.getStatus(), result.getContentAsString())
                .isEqualTo(expectedStatus);
        // Para una respuesta vacía devuelve un nodo JSON nulo; si hay cuerpo, lo interpreta.
        return result.getContentAsString().isBlank()
                ? mapper.nullNode()
                : json(result.getContentAsString());
    }

    // Crea un cliente de prueba con valores únicos y devuelve el id generado por la API.
    private long customer() throws Exception {
        return call(
                        post("/api/v1/customers"),
                        Map.of(
                                "name",
                                unique("Client"),
                                "taxId",
                                unique("B"),
                                "email",
                                "buyer@example.test",
                                "address",
                                "Calle Mayor 1, Madrid"),
                        201)
                .path("id")
                .asLong();
    }

    // Crea un producto de prueba al precio indicado y devuelve su id.
    private long product(String price) throws Exception {
        return product(unique("SKU"), unique("Product"), price);
    }

    private long product(String sku, String name, String price) throws Exception {
        return call(
                        post("/api/v1/products"),
                        Map.of(
                                "sku",
                                sku,
                                "name",
                                name,
                                "unitPrice",
                                new BigDecimal(price)),
                        201)
                .path("id")
                .asLong();
    }

    // Registra una entrada de stock usando el mismo endpoint que usaría un cliente.
    private void adjust(long product, long quantity) throws Exception {
        call(
                post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", quantity, "reason", "Test stock receipt"),
                204);
    }

    // Crea un pedido DRAFT, añade una línea con la cantidad indicada y devuelve el id del pedido.
    private long order(long customer, long product, long quantity) throws Exception {
        long id =
                call(post("/api/v1/orders"), Map.of("customerId", customer), 201)
                        .path("id")
                        .asLong();
        call(
                put("/api/v1/orders/{id}/lines/{product}", id, product),
                Map.of("quantity", quantity),
                200);
        return id;
    }

    // Lee un saldo directamente desde la base para contrastar lo que quedó persistido.
    private long balance(long product, String column) {
        // Restringe el nombre de columna permitido antes de concatenarlo en SQL.
        if (!List.of("on_hand", "reserved").contains(column)) throw new IllegalArgumentException();
        return jdbc.queryForObject(
                "SELECT " + column + " FROM inventory_balance WHERE product_id=?",
                Long.class,
                product);
    }

    // Cuenta reservas o movimientos de un pedido para comprobar efectos persistidos.
    private long count(String table, long order) {
        // Solo admite estas dos tablas antes de construir la consulta.
        if (!List.of("stock_reservation", "stock_movement").contains(table))
            throw new IllegalArgumentException();
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE order_id=?", Long.class, order);
    }

    // Actualiza un cliente y comprueba que la baja sea lógica: la fila permanece inactiva.
    @Test
    void customerCanBeUpdatedAndSoftDeleted() throws Exception {
        // Prepara un cliente y lee su versión actual para enviar una actualización válida.
        long id = customer();
        JsonNode before = call(get("/api/v1/customers/{id}", id), null, 200);
        // Cambia los datos usando la versión que acaba de consultar.
        call(
                put("/api/v1/customers/{id}", id),
                Map.of(
                        "name",
                        "Updated company",
                        "taxId",
                        before.path("taxId").asText(),
                        "email",
                        "changed@example.test",
                        "address",
                        "Madrid",
                        "version",
                        before.path("version").asLong()),
                200);
        // Comprueba que el nombre cambió y luego solicita la desactivación.
        assertThat(call(get("/api/v1/customers/{id}", id), null, 200).path("name").asText())
                .isEqualTo("Updated company");
        call(delete("/api/v1/customers/{id}", id), null, 204);
        // Verifica directamente en la tabla que active quedó en false.
        assertThat(jdbc.queryForObject("SELECT active FROM customer WHERE id=?", Boolean.class, id))
                .isFalse();
    }

    // La restricción de identificador fiscal único debe impedir un cliente duplicado.
    @Test
    void duplicateCustomerTaxIdIsConflict() throws Exception {
        // Crea un cliente y recupera su taxId para intentar reutilizarlo.
        long id = customer();
        String taxId =
                jdbc.queryForObject("SELECT tax_id FROM customer WHERE id=?", String.class, id);
        // Espera 409 al enviar un segundo cliente con ese taxId.
        call(
                post("/api/v1/customers"),
                Map.of(
                        "name",
                        "Other",
                        "taxId",
                        taxId,
                        "email",
                        "other@example.test",
                        "address",
                        "Madrid"),
                409);
        // Verifica que la base conserva una sola fila con el identificador fiscal.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customer WHERE tax_id=?", Long.class, taxId))
                .isEqualTo(1);
    }

    // Datos inválidos deben rechazarse sin crear un cliente parcial.
    @Test
    void invalidCustomerDoesNotPersist() throws Exception {
        // Guarda el número de clientes antes de enviar la petición inválida.
        long before = jdbc.queryForObject("SELECT count(*) FROM customer", Long.class);
        // Campos vacíos y correo inválido producen una respuesta 400.
        call(
                post("/api/v1/customers"),
                Map.of("name", "", "taxId", "", "email", "bad", "address", ""),
                400);
        // El conteo posterior debe coincidir con el inicial.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Long.class))
                .isEqualTo(before);
    }

    // Una versión antigua no debe sobrescribir una actualización de producto más reciente.
    @Test
    void staleProductUpdateIsConflictAndPreservesNewPrice() throws Exception {
        // Crea el producto y prepara una edición usando su versión actual.
        long id = product("10.00");
        JsonNode current = call(get("/api/v1/products/{id}", id), null, 200);
        Map<String, Object> update =
                Map.of(
                        "sku",
                        current.path("sku").asText(),
                        "name",
                        "Changed",
                        "unitPrice",
                        20,
                        "version",
                        current.path("version").asLong());
        // La primera edición funciona; repetir la misma versión debe devolver 409.
        call(put("/api/v1/products/{id}", id), update, 200);
        call(put("/api/v1/products/{id}", id), update, 409);
        // Comprueba que el precio correcto permanece en la base después del intento rechazado.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT unit_price FROM product WHERE id=?", BigDecimal.class, id))
                .isEqualByComparingTo("20.00");
    }

    // Un SKU que ya existe debe rechazarse con 409.
    @Test
    void duplicateProductSkuIsConflict() throws Exception {
        long id = product("10.00");
        String sku = jdbc.queryForObject("SELECT sku FROM product WHERE id=?", String.class, id);
        call(
                post("/api/v1/products"),
                Map.of("sku", sku, "name", "Duplicate", "unitPrice", 12),
                409);
    }

    // Un precio negativo debe fallar la validación de entrada con 400.
    @Test
    void negativePriceIsValidationError() throws Exception {
        call(
                post("/api/v1/products"),
                Map.of("sku", unique("SKU"), "name", "Invalid", "unitPrice", -1),
                400);
    }

    @Test
    void catalogSearchTreatsSkuAndNamePunctuationLiterally() throws Exception {
        // El prefijo único permite repetir el test sin encontrar productos de otras pruebas.
        String prefix = unique("ETQ").toUpperCase(Locale.ROOT);
        String sku = prefix + "_A";
        String label = unique("Etiqueta");
        product(sku, label + "-Azul grande", "10.00");
        product(prefix + "XA", label + "-XAzul grande", "10.00");
        product(prefix + "_XA", "Caja roja", "10.00");

        // Las tres referencias comparten prefijo; la etiqueta completa solo identifica una.
        assertThat(searchProducts(prefix).path("totalElements").asInt()).isEqualTo(3);
        for (String query : List.of(sku, sku.toLowerCase(Locale.ROOT))) {
            JsonNode page = searchProducts(query);
            assertThat(page.path("totalElements").asInt()).as("Búsqueda de %s", query).isEqualTo(1);
            assertThat(page.path("content").get(0).path("sku").asText()).isEqualTo(sku);
        }

        // Buscar parte del nombre sigue funcionando y respeta el texto a ambos lados del guion.
        JsonNode byName = searchProducts(label + "-Azul");
        assertThat(byName.path("totalElements").asInt()).isEqualTo(1);
        assertThat(byName.path("content").get(0).path("sku").asText()).isEqualTo(sku);
    }

    private JsonNode searchProducts(String query) throws Exception {
        return call(get("/api/v1/products").param("search", query), null, 200);
    }

    // Busca un producto activo y comprueba filtros, orden y metadatos de paginación.
    @Test
    void productSearchCombinesPaginationAndActiveFilter() throws Exception {
        // Crea un producto y recupera su SKU para buscarlo de manera precisa.
        long id = product("11.25");
        String sku = jdbc.queryForObject("SELECT sku FROM product WHERE id=?", String.class, id);
        // Solicita una página de tamaño uno filtrada por SKU y estado activo.
        JsonNode page =
                call(
                        get("/api/v1/products")
                                .param("search", sku)
                                .param("active", "true")
                                .param("page", "0")
                                .param("size", "1")
                                .param("sort", "unitPrice")
                                .param("direction", "desc"),
                        null,
                        200);
        // Comprueba total y producto devuelto; después lo desactiva.
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(id);
        call(delete("/api/v1/products/{id}", id), null, 204);
        // La misma búsqueda de productos activos ya no debe encontrarlo.
        assertThat(
                        call(
                                        get("/api/v1/products")
                                                .param("search", sku)
                                                .param("active", "true"),
                                        null,
                                        200)
                                .path("totalElements")
                                .asLong())
                .isZero();
    }

    // Parámetros de página fuera de límite y un campo de orden no permitido deben rechazarse.
    @Test
    void invalidPaginationAndSortAreRejected() throws Exception {
        call(get("/api/v1/products").param("size", "101"), null, 400);
        call(get("/api/v1/products").param("sort", "name;DROP TABLE product"), null, 400);
    }

    @Test
    void orderTotalCountsBothLinesWhenTheirAmountsMatch() throws Exception {
        // Dos productos distintos generan el mismo subtotal: 2 × 6 € y 3 × 4 €.
        long firstProduct = product("6.00");
        long secondProduct = product("4.00");
        long orderId = order(customer(), firstProduct, 2);
        String lineUrl = "/api/v1/orders/{id}/lines/{product}";

        // El PUT debe devolver 24 € y un GET posterior debe recuperar ese mismo total.
        JsonNode saved = call(put(lineUrl, orderId, secondProduct), Map.of("quantity", 3), 200);
        assertThat(saved.path("lines").size()).isEqualTo(2);
        assertThat(saved.path("total").decimalValue()).isEqualByComparingTo("24.00");
        JsonNode fetched = call(get("/api/v1/orders/{id}", orderId), null, 200);
        assertThat(fetched.path("total").decimalValue()).isEqualByComparingTo("24.00");

        // Reemplazar la cantidad de la segunda línea deja 12 + 16 €.
        JsonNode updated = call(put(lineUrl, orderId, secondProduct), Map.of("quantity", 4), 200);
        assertThat(updated.path("total").decimalValue()).isEqualByComparingTo("28.00");

        // Al eliminar esa línea, deben quedar la primera y sus 12 €.
        JsonNode remaining = call(delete(lineUrl, orderId, secondProduct), null, 200);
        assertThat(remaining.path("total").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(remaining.path("lines").size()).isEqualTo(1);
        assertThat(remaining.path("lines").get(0).path("productId").asLong()).isEqualTo(firstProduct);
    }

    // El pedido conserva el precio pactado y recalcula su total al retirar una línea.
    @Test
    void orderTotalUsesDecimalSnapshotAndLineReplacement() throws Exception {
        // Tres unidades a 12,35 deben sumar exactamente 37,05.
        long product = product("12.35");
        long id = order(customer(), product, 3);
        assertThat(call(get("/api/v1/orders/{id}", id), null, 200).path("total").decimalValue())
                .isEqualByComparingTo("37.05");
        // Cambiar el precio del catálogo no debe cambiar el precio guardado en la línea existente.
        jdbc.update("UPDATE product SET unit_price=99 WHERE id=?", product);
        assertThat(
                        call(get("/api/v1/orders/{id}", id), null, 200)
                                .path("lines")
                                .get(0)
                                .path("unitPrice")
                                .decimalValue())
                .isEqualByComparingTo("12.35");
        // Al eliminar la única línea, comprueba que el total persistido vuelve a cero.
        call(delete("/api/v1/orders/{id}/lines/{product}", id, product), null, 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT total FROM sales_order WHERE id=?", BigDecimal.class, id))
                .isEqualByComparingTo("0");
    }

    // Confirmar un pedido sin líneas debe fallar y no crear reservas.
    @Test
    void emptyOrderCannotBeConfirmed() throws Exception {
        // Crea únicamente la cabecera, sin añadir productos.
        long id =
                call(post("/api/v1/orders"), Map.of("customerId", customer()), 201)
                        .path("id")
                        .asLong();
        // Exige 409 y comprueba que no haya reservas asociadas a ese pedido.
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(count("stock_reservation", id)).isZero();
    }

    // Una cantidad cero en la línea debe rechazarse sin alterar la cantidad anterior.
    @Test
    void zeroLineQuantityDoesNotPersist() throws Exception {
        // Crea una línea de dos unidades e intenta sustituirla por cero.
        long product = product("10");
        long id = order(customer(), product, 2);
        call(put("/api/v1/orders/{id}/lines/{product}", id, product), Map.of("quantity", 0), 400);
        // Verifica en PostgreSQL que la cantidad sigue siendo dos.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT quantity FROM order_line WHERE order_id=?", Long.class, id))
                .isEqualTo(2);
    }

    // Confirmar reserva unidades, pero no las retira físicamente del almacén.
    @Test
    void confirmationReservesWithoutReducingPhysicalStock() throws Exception {
        // Prepara diez unidades físicas y un pedido de cuatro.
        long product = product("9.99");
        adjust(product, 10);
        long id = order(customer(), product, 4);
        // Confirma y comprueba estado, saldo físico, reservado y registros creados.
        assertThat(call(post("/api/v1/orders/{id}/confirm", id), null, 200).path("status").asText())
                .isEqualTo("CONFIRMED");
        // El físico permanece en diez; reservado sube a cuatro.
        assertThat(balance(product, "on_hand")).isEqualTo(10);
        assertThat(balance(product, "reserved")).isEqualTo(4);
        // Se creó un registro de reserva y uno de movimiento para ese pedido.
        assertThat(count("stock_reservation", id)).isEqualTo(1);
        assertThat(count("stock_movement", id)).isEqualTo(1);
    }

    // Si la segunda línea no tiene stock, se deshace también lo reservado para la primera.
    @Test
    void insufficientSecondLineRollsBackFirstReservationAndMovements() throws Exception {
        // El primer producto tiene diez unidades; el segundo tiene una, pero se piden dos.
        long first = product("10");
        long second = product("20");
        adjust(first, 10);
        adjust(second, 1);
        // Añade ambas líneas y espera que la confirmación completa falle con 409.
        long id = order(customer(), first, 3);
        call(put("/api/v1/orders/{id}/lines/{product}", id, second), Map.of("quantity", 2), 200);
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        // Comprueba rollback: ningún producto queda reservado ni persisten reserva o movimiento.
        assertThat(balance(first, "reserved")).isZero();
        assertThat(balance(second, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(count("stock_movement", id)).isZero();
        // El pedido también debe seguir en DRAFT.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("DRAFT");
    }

    // INC-004: un producto desactivado después de crear las líneas revierte la primera reserva.
    @Test
    void inactiveSecondProductRollsBackFirstReservationAndMovements() throws Exception {
        long first = product("10");
        long second = product("10");
        adjust(first, 10);
        adjust(second, 10);
        long id = order(customer(), first, 3);
        call(put("/api/v1/orders/{id}/lines/{product}", id, second), Map.of("quantity", 2), 200);
        call(delete("/api/v1/products/{id}", second), null, 204);

        JsonNode rejected = call(post("/api/v1/orders/{id}/confirm", id), null, 409);

        assertThat(rejected.path("code").asText()).isEqualTo("PRODUCT_UNAVAILABLE");
        assertThat(balance(first, "on_hand")).isEqualTo(10);
        assertThat(balance(second, "on_hand")).isEqualTo(10);
        assertThat(balance(first, "reserved")).isZero();
        assertThat(balance(second, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(count("stock_movement", id)).isZero();
        assertThat(call(get("/api/v1/orders/{id}", id), null, 200).path("status").asText())
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_event WHERE action='ORDER_CONFIRMED'"
                                + " AND entity_type='ORDER' AND entity_id=?",
                        Long.class,
                        Long.toString(id)))
                .isZero();
    }

    // Cancelar un pedido confirmado libera sus reservas sin descontar existencias físicas.
    @Test
    void cancellingConfirmedOrderReleasesEveryReservation() throws Exception {
        // Prepara nueve unidades físicas y confirma un pedido de cuatro.
        long product = product("10");
        adjust(product, 9);
        long id = order(customer(), product, 4);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Cancela y comprueba saldo, eliminación de la reserva y dos movimientos históricos.
        call(post("/api/v1/orders/{id}/cancel", id), null, 200);
        assertThat(balance(product, "on_hand")).isEqualTo(9);
        assertThat(balance(product, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(count("stock_movement", id)).isEqualTo(2);
    }

    // Completar un pedido confirmado consulta shipping y consume las unidades reservadas.
    @Test
    void completingOrderCallsCarrierAndConsumesStock() throws Exception {
        // Prepara stock, pedido y confirmación antes de intentar completarlo.
        long product = product("10");
        adjust(product, 9);
        long id = order(customer(), product, 4);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Guarda el contador previo para comprobar que la finalización llamó al transportista.
        int calls = CARRIER_CALLS.get();
        // La respuesta debe contener la referencia y el coste del simulador.
        JsonNode completed = call(post("/api/v1/orders/{id}/complete", id), null, 200);
        assertThat(completed.path("shippingReference").asText()).isEqualTo("TEST-TRACKING");
        assertThat(completed.path("shippingCost").decimalValue()).isEqualByComparingTo("12.50");
        // El contador debe haber aumentado; no se exige un número exacto de llamadas.
        assertThat(CARRIER_CALLS.get()).isGreaterThan(calls);
        // Enviar cuatro de nueve deja cinco físicas y ninguna reservada.
        assertThat(balance(product, "on_hand")).isEqualTo(5);
        assertThat(balance(product, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        // Verifica que el estado persistido sea COMPLETED.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("COMPLETED");
    }

    // Un fallo temporal de shipping deja el pedido confirmado y permite reintentar.
    @Test
    void carrierFailurePreservesConfirmedOrderAndReservationForRetry() throws Exception {
        // Prepara siete unidades físicas y reserva tres para el pedido.
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Configura el simulador para devolver HTTP 503.
        CARRIER_STATUS.set(503);
        // Intenta completar y restablece el simulador incluso si la comprobación falla.
        try {
            call(post("/api/v1/orders/{id}/complete", id), null, 503);
        } finally {
            CARRIER_STATUS.set(200);
        }
        // Tras el fallo, stock físico, reserva, movimiento y estado deben permanecer como estaban.
        assertThat(balance(product, "on_hand")).isEqualTo(7);
        assertThat(balance(product, "reserved")).isEqualTo(3);
        assertThat(count("stock_reservation", id)).isEqualTo(1);
        assertThat(count("stock_movement", id)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("CONFIRMED");
        // Con shipping recuperado reintenta y comprueba que se consumen las tres unidades.
        call(post("/api/v1/orders/{id}/complete", id), null, 200);
        assertThat(balance(product, "on_hand")).isEqualTo(4);
    }

    // Un confirmado no puede editar sus líneas ni volver a confirmarse.
    @Test
    void confirmedOrderCannotBeEditedOrConfirmedTwice() throws Exception {
        // Prepara y confirma un pedido de tres unidades.
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Ambos intentos deben devolver 409 y no duplicar la reserva ni el movimiento.
        call(put("/api/v1/orders/{id}/lines/{product}", id, product), Map.of("quantity", 4), 409);
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(balance(product, "reserved")).isEqualTo(3);
        assertThat(count("stock_movement", id)).isEqualTo(1);
    }

    // Un pedido ya completado no puede cancelarse ni descontar stock por segunda vez.
    @Test
    void completedOrderCannotBeCancelledOrCompletedTwice() throws Exception {
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Completa una vez y después intenta cancelar y completar de nuevo.
        call(post("/api/v1/orders/{id}/complete", id), null, 200);
        call(post("/api/v1/orders/{id}/cancel", id), null, 409);
        call(post("/api/v1/orders/{id}/complete", id), null, 409);
        // Comprueba que solo se descontaron tres de las siete unidades físicas.
        assertThat(balance(product, "on_hand")).isEqualTo(4);
    }

    // Un ajuste negativo no puede consumir unidades que ya están reservadas.
    @Test
    void adjustmentCannotConsumeReservedUnits() throws Exception {
        // Prepara siete físicas y cinco reservadas mediante un pedido confirmado.
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 5);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Restar tres dejaría cuatro físicas para cinco reservadas y debe devolver 409.
        call(
                post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", -3, "reason", "Bad count"),
                409);
        // La base debe conservar los saldos previos al ajuste rechazado.
        assertThat(balance(product, "on_hand")).isEqualTo(7);
        assertThat(balance(product, "reserved")).isEqualTo(5);
    }

    // Un ajuste de cero debe fallar sin registrar movimientos para el producto.
    @Test
    void zeroAdjustmentIsRejectedWithoutMovement() throws Exception {
        long product = product("10");
        call(
                post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", 0, "reason", "Zero"),
                400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM stock_movement WHERE product_id=?",
                                Long.class,
                                product))
                .isZero();
    }

    @Test
    void withdrawalKeepsItsNegativeQuantityInMovementHistory() throws Exception {
        // Partimos de un producto nuevo sin stock y registramos una entrada de 12 unidades.
        long productId = product("10");
        adjust(productId, 12);

        // Enviamos la retirada por el mismo endpoint que en Swagger.
        var adjustmentUrl = "/api/v1/inventory/{id}/adjustments";
        call(post(adjustmentUrl, productId),
                Map.of("quantity", -5, "reason", "Retirada por daños"), 204);

        // Con 7 unidades disponibles, retirar otras 8 debe rechazarse sin crear un movimiento.
        call(post(adjustmentUrl, productId),
                Map.of("quantity", -8, "reason", "Retirada sin stock"), 409);
        assertThat(balance(productId, "on_hand")).isEqualTo(7);

        // Leemos el diario del producto. La API devuelve primero el movimiento más reciente.
        JsonNode movements = call(
                get("/api/v1/inventory/movements").param("productId", Long.toString(productId)),
                null, 200).path("content");
        assertThat(movements.size()).isEqualTo(2);
        assertThat(movements.get(1).path("quantity").asLong()).isEqualTo(12);

        // Esta aserción sobre la cantidad distingue una retirada de una segunda entrada.
        JsonNode withdrawal = movements.get(0);
        assertThat(withdrawal.path("reason").asText()).isEqualTo("Retirada por daños");
        assertThat(withdrawal.path("productId").asLong()).isEqualTo(productId);
        assertThat(withdrawal.path("quantity").asLong()).isEqualTo(-5);
    }

    // La consulta de inventario debe calcular disponible como físico menos reservado.
    @Test
    void inventoryJoinReturnsCorrectAvailableBalance() throws Exception {
        // Prepara siete físicas y confirma un pedido que reserva cinco.
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 5);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Consulta el resumen por productId y comprueba que devuelve 7, 5 y 2.
        JsonNode row =
                call(get("/api/v1/inventory").param("productId", Long.toString(product)), null, 200)
                        .path("content")
                        .get(0);
        assertThat(row.path("onHand").asLong()).isEqualTo(7);
        assertThat(row.path("reserved").asLong()).isEqualTo(5);
        assertThat(row.path("available").asLong()).isEqualTo(2);
    }

    // Repite tres veces el escenario para observar la reserva concurrente.
    @RepeatedTest(3)
    // Dos pedidos compiten por cinco unidades; cada uno pide cuatro y solo uno puede confirmar.
    void competingOrdersCannotReserveSameUnits() throws Exception {
        // Crea un stock común y dos pedidos para el mismo producto.
        long product = product("10");
        adjust(product, 5);
        long customer = customer();
        long first = order(customer, product, 4);
        long second = order(customer, product, 4);
        // La barrera intenta que las dos confirmaciones comiencen a la vez.
        var barrier = new CyclicBarrier(2);
        // Usa hilos virtuales para ejecutar las dos peticiones en paralelo.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responses =
                    // Cada tarea espera en la barrera y después confirma uno de los pedidos.
                    executor.invokeAll(
                            List.of(
                                    () -> {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        return mvc.perform(
                                                        post("/api/v1/orders/{id}/confirm", first)
                                                                .header("Authorization", bearer))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    },
                                    () -> {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        return mvc.perform(
                                                        post("/api/v1/orders/{id}/confirm", second)
                                                                .header("Authorization", bearer))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    }));
            // Uno debe recibir 200 y el otro 409, sin asumir cuál de los dos gana.
            assertThat(List.of(responses.get(0).get(), responses.get(1).get()))
                    .containsExactlyInAnyOrder(200, 409);
        }
        // Comprueba el estado final real: cinco físicas y solo cuatro reservadas.
        assertThat(balance(product, "on_hand")).isEqualTo(5);
        assertThat(balance(product, "reserved")).isEqualTo(4);
        // Contrasta reservas, pedidos confirmados y movimientos RESERVE directamente en la base.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sum(quantity) FROM stock_reservation WHERE product_id=?",
                                Long.class,
                                product))
                .isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sales_order WHERE id IN (?,?) AND"
                                    + " status='CONFIRMED'",
                                Long.class,
                                first,
                                second))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM stock_movement WHERE product_id=? AND"
                                    + " kind='RESERVE'",
                                Long.class,
                                product))
                .isEqualTo(1);
    }

    // El informe de ventas agrupa solo pedidos completados dentro del intervalo de fechas.
    @Test
    void salesReportAggregatesOnlyCompletedOrdersAndHonorsDateWindow() throws Exception {
        // Prepara dos pedidos que se completarán y un tercero que permanece en borrador.
        long customer = customer();
        long product = product("12.35");
        adjust(product, 10);
        long first = order(customer, product, 2);
        long second = order(customer, product, 3);
        order(customer, product, 1); // A draft must not inflate revenue.
        // Confirma y completa los dos pedidos, y fija su fecha para controlar el escenario.
        for (long id : List.of(first, second)) {
            call(post("/api/v1/orders/{id}/confirm", id), null, 200);
            call(post("/api/v1/orders/{id}/complete", id), null, 200);
            jdbc.update("UPDATE sales_order SET updated_at='2020-02-02T12:00:00Z' WHERE id=?", id);
        }
        // Consulta el informe dentro de la ventana que incluye esos pedidos.
        JsonNode page =
                call(
                        get("/api/v1/reports/sales")
                                .param("from", "2020-02-02T00:00:00Z")
                                .param("to", "2020-02-03T00:00:00Z"),
                        null,
                        200);
        // Verifica un cliente, dos pedidos, 61,75 de ventas y 25 de envío.
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        JsonNode row = page.path("content").get(0);
        assertThat(row.path("customerId").asLong()).isEqualTo(customer);
        assertThat(row.path("completedOrders").asLong()).isEqualTo(2);
        assertThat(row.path("netSales").decimalValue()).isEqualByComparingTo("61.75");
        assertThat(row.path("shippingCost").decimalValue()).isEqualByComparingTo("25.00");
        // La fecha final es exclusiva: pedidos justo en ese instante quedan fuera.
        assertThat(
                        call(
                                        get("/api/v1/reports/sales")
                                                .param("to", "2020-02-02T12:00:00Z"),
                                        null,
                                        200)
                                .path("totalElements")
                                .asLong())
                .isZero();
    }

    // El informe de bajo stock incluye un producto sin saldo y cuenta reservas activas.
    @Test
    void lowStockReportIncludesMissingBalanceAndCountsConfirmedOrders() throws Exception {
        // Agota el disponible de un producto reservando todas sus unidades.
        long product = product("10");
        adjust(product, 5);
        long id = order(customer(), product, 5);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Elimina el saldo de otro producto para probar la unión cuando falta esa fila.
        long missing = product("11");
        jdbc.update("DELETE FROM inventory_balance WHERE product_id=?", missing);
        // Pide productos con disponible menor o igual a cero.
        JsonNode rows =
                call(
                                get("/api/v1/reports/low-stock")
                                        .param("threshold", "0")
                                        .param("size", "100"),
                                null,
                                200)
                        .path("content");
        // Busca en la respuesta las dos filas creadas por este caso.
        JsonNode reservedRow = null;
        JsonNode missingRow = null;
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == product) reservedRow = row;
            if (row.path("productId").asLong() == missing) missingRow = row;
        }
        // Comprueba que ambas aparecen y que sus cantidades y conteos son correctos.
        assertThat(reservedRow).isNotNull();
        assertThat(missingRow).isNotNull();
        assertThat(reservedRow.path("available").asLong()).isZero();
        assertThat(reservedRow.path("confirmedOrders").asLong()).isEqualTo(1);
        assertThat(missingRow.path("onHand").asLong()).isZero();
        assertThat(missingRow.path("confirmedOrders").asLong()).isZero();
    }

    // La búsqueda de pedidos debe combinar cliente, estado, orden y paginación.
    @Test
    void orderFiltersAndPaginationReturnMatchingStatusAndCustomer() throws Exception {
        // Crea dos pedidos del cliente y confirma únicamente uno.
        long customer = customer();
        long product = product("10");
        adjust(product, 10);
        long confirmed = order(customer, product, 1);
        order(customer, product, 2);
        call(post("/api/v1/orders/{id}/confirm", confirmed), null, 200);
        // Filtra CONFIRMED para ese cliente y solicita una página de tamaño uno.
        JsonNode page =
                call(
                        get("/api/v1/orders")
                                .param("customerId", Long.toString(customer))
                                .param("status", "CONFIRMED")
                                .param("size", "1")
                                .param("sort", "total,asc"),
                        null,
                        200);
        // Verifica que solo coincide el pedido confirmado.
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(confirmed);
    }

    // INC-002: dos páginas consecutivas no deben repetir el pedido situado en su límite.
    @Test
    void consecutiveOrderPagesDoNotOverlap() throws Exception {
        long customer = customer();
        var ids = new java.util.ArrayList<Long>();
        for (int i = 0; i < 4; i++) {
            ids.add(call(post("/api/v1/orders"), Map.of("customerId", customer), 201)
                    .path("id").asLong());
        }

        for (int page = 0; page < 2; page++) {
            JsonNode result = call(get("/api/v1/orders")
                            .param("customerId", Long.toString(customer))
                            .param("page", Integer.toString(page))
                            .param("size", "2")
                            .param("sort", "createdAt,asc"), null, 200);
            assertThat(result.path("totalElements").asLong()).isEqualTo(4);
            assertThat(result.path("content").size()).isEqualTo(2);
            for (int row = 0; row < 2; row++) {
                assertThat(result.path("content").get(row).path("id").asLong())
                        .isEqualTo(ids.get(page * 2 + row));
            }
        }
    }

    // Un cliente inactivo no debe poder asociarse a un pedido nuevo.
    @Test
    void inactiveCustomerCannotPlaceNewOrder() throws Exception {
        // Crea y desactiva el cliente antes de intentar crear el pedido.
        long customer = customer();
        call(delete("/api/v1/customers/{id}", customer), null, 204);
        call(post("/api/v1/orders"), Map.of("customerId", customer), 409);
        // Comprueba que no se insertó ningún pedido para ese cliente.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sales_order WHERE customer_id=?",
                                Long.class,
                                customer))
                .isZero();
    }

    // La auditoría correlaciona una confirmación exitosa y no duplica el evento al fallar otra.
    @Test
    void successfulOrderOperationHasCorrelatedAuditAndFailedTransitionDoesNot() throws Exception {
        // Prepara stock y pedido; confirma con un identificador de petición conocido.
        long product = product("10");
        adjust(product, 5);
        long id = order(customer(), product, 2);
        call(
                post("/api/v1/orders/{id}/confirm", id).header("X-Request-ID", "qa-confirm-123"),
                null,
                200);
        // Busca en la base el requestId guardado en el evento ORDER_CONFIRMED.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT request_id FROM audit_event WHERE action='ORDER_CONFIRMED'"
                                    + " AND entity_id=?",
                                String.class,
                                Long.toString(id)))
                .isEqualTo("qa-confirm-123");
        // Una segunda confirmación falla con 409 y no debe crear otro evento de confirmación.
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_event WHERE action='ORDER_CONFIRMED'"
                                    + " AND entity_id=?",
                                Long.class,
                                Long.toString(id)))
                .isEqualTo(1);
    }

    // Si se cancela mientras shipping responde, la finalización debe fallar sin salida parcial.
    @Test
    void cancellationDuringCarrierCallWinsWithoutPartialShipment() throws Exception {
        // Prepara y confirma un pedido de tres unidades sobre ocho físicas.
        long product = product("10");
        adjust(product, 8);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        // Los latch detienen el transportista hasta que la prueba termine la cancelación.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        CARRIER_ENTERED.set(entered);
        CARRIER_RELEASE.set(release);
        // Lanza la finalización en otro hilo para poder cancelar mientras espera al transportista.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var completion =
                    executor.submit(
                            () ->
                                    mvc.perform(
                                                    post("/api/v1/orders/{id}/complete", id)
                                                            .header("Authorization", bearer))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus());
            // Espera a que shipping reciba la llamada antes de cancelar el pedido.
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                call(post("/api/v1/orders/{id}/cancel", id), null, 200);
            // Libera la llamada y limpia las referencias incluso si falla una comprobación.
            } finally {
                release.countDown();
                CARRIER_ENTERED.set(null);
                CARRIER_RELEASE.set(null);
            }
            // La finalización debe detectar el cambio y devolver 409.
            assertThat(completion.get(10, TimeUnit.SECONDS)).isEqualTo(409);
        // Repite la limpieza en finally para no dejar bloqueadas otras pruebas.
        } finally {
            release.countDown();
            CARRIER_ENTERED.set(null);
            CARRIER_RELEASE.set(null);
        }
        // Verifica el estado final CANCELLED, saldo íntegro, reserva liberada y ausencia de SHIP.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("CANCELLED");
        assertThat(balance(product, "on_hand")).isEqualTo(8);
        assertThat(balance(product, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM stock_movement WHERE order_id=? AND"
                                    + " kind='SHIP'",
                                Long.class,
                                id))
                .isZero();
        assertThat(count("stock_movement", id)).isEqualTo(2);
    }
}
