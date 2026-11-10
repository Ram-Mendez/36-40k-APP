package com.acme.operations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.operations.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

// Comprueba permisos y respuestas HTTP con la aplicación y PostgreSQL de integración.
class PermissionAndErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    void readonlyCanOpenAnOrderFromTheList() throws Exception {
        // Como en Swagger: iniciar sesión y listar los pedidos con el token de solo lectura.
        String readonly = "Bearer " + token("readonly");
        var list = mvc.perform(get("/api/v1/orders").param("size", "1").header("Authorization", readonly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andReturn().getResponse();
        long orderId = json(list.getContentAsString()).get("content").get(0).get("id").asLong();

        // Abrir ese pedido con el mismo token. El bug devolvía 403 en este GET.
        mvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", readonly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        // Sin token, la misma petición debe seguir requiriendo autenticación.
        mvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isUnauthorized());
    }

    // Un método HTTP no soportado debe producir 405, no un error interno.
    @Test
    void unsupportedMethodReturns405InsteadOfServerError() throws Exception {
        // PUT en la ruta de colección de clientes no está definido; se llama con un admin válido.
        mvc.perform(put("/api/v1/customers").header("Authorization", "Bearer " + token("admin")))
                // Comprueba estado, código de error y presencia de requestId.
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    // Un Content-Type no admitido debe devolver 415.
    @Test
    void unsupportedContentTypeReturns415InsteadOfServerError() throws Exception {
        // Envía texto plano a una ruta que espera JSON.
        mvc.perform(
                        post("/api/v1/customers")
                                .header("Authorization", "Bearer " + token("admin"))
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("customer"))
                // Verifica tanto el estado como el código UNSUPPORTED_MEDIA_TYPE.
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // JSON incompleto y parámetros numéricos inválidos deben devolver 400 consistente.
    @Test
    void malformedJsonAndNumericParametersReturnConsistent400() throws Exception {
        // Reutiliza el token de admin para distinguir validación de errores de autenticación.
        String authorization = "Bearer " + token("admin");
        // Envía un cuerpo JSON incompleto al endpoint de usuarios.
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // Envía texto donde page debe ser numérico y comprueba el mismo código de entrada inválida.
        mvc.perform(
                        get("/api/v1/users")
                                .param("page", "not-a-number")
                                .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // READONLY no puede modificar recursos ni consultar auditoría, y no debe alterar la base.
    @Test
    void readonlyCannotMutateAnyBusinessResourceOrReadAuditAndDatabaseIsUnchanged()
            throws Exception {
        // Prepara credenciales de solo lectura y selecciona un cliente y producto existentes.
        String authorization = "Bearer " + token("readonly");
        Long customerId = jdbc.queryForObject("SELECT min(id) FROM customer", Long.class);
        Long productId = jdbc.queryForObject("SELECT min(id) FROM product", Long.class);
        // Guarda una foto de las tablas para compararla después de todos los intentos.
        var before = businessSnapshot();
        // Construye cuerpos de ejemplo para los intentos de crear o editar recursos.
        String customer =
                "{\"name\":\"Denied"
                    + " Customer\",\"taxId\":\"DENIED123\",\"email\":\"test@example.com\",\"address\":\"Madrid\",\"version\":0}";
        String product =
                "{\"sku\":\"DENIED123\",\"name\":\"Denied"
                    + " Product\",\"unitPrice\":12.50,\"version\":0}";
        String user =
                "{\"username\":\"denieduser\",\"password\":\"DeniedPassword123!\",\"role\":\"ADMIN\"}";
        // Reúne las operaciones que deben denegarse a READONLY.
        List<MockHttpServletRequestBuilder> requests =
                List.of(
                        post("/api/v1/customers").content(customer),
                        put("/api/v1/customers/" + customerId).content(customer),
                        delete("/api/v1/customers/" + customerId),
                        post("/api/v1/products").content(product),
                        put("/api/v1/products/" + productId).content(product),
                        delete("/api/v1/products/" + productId),
                        post("/api/v1/inventory/" + productId + "/adjustments")
                                .content("{\"quantity\":7,\"reason\":\"Denied adjustment\"}"),
                        post("/api/v1/orders").content("{\"customerId\":" + customerId + "}"),
                        put("/api/v1/orders/1").content("{\"customerId\":" + customerId + "}"),
                        put("/api/v1/orders/1/lines/" + productId).content("{\"quantity\":1}"),
                        delete("/api/v1/orders/1/lines/" + productId),
                        post("/api/v1/orders/1/confirm"),
                        post("/api/v1/orders/1/cancel"),
                        post("/api/v1/orders/1/complete"),
                        post("/api/v1/users").content(user),
                        put("/api/v1/users/1").content("{\"role\":\"READONLY\",\"active\":false}"),
                        delete("/api/v1/users/1"),
                        get("/api/v1/audit"));
        // Ejecuta cada petición y exige 403 ACCESS_DENIED.
        for (var request : requests) {
            mvc.perform(
                            request.header("Authorization", authorization)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        // Compara el estado posterior con la foto inicial para detectar escrituras inesperadas.
        assertThat(businessSnapshot()).isEqualTo(before);
    }

    // Comprueba que la lista de usuarios combine filtros y entregue metadatos de página.
    @Test
    void usersUseSharedPaginationContractAndCombineFilters() throws Exception {
        // Busca el usuario readonly activo con una página de tamaño uno.
        mvc.perform(
                        get("/api/v1/users")
                                .header("Authorization", "Bearer " + token("admin"))
                                .param("query", "readonly")
                                .param("active", "true")
                                .param("size", "1"))
                // Verifica el usuario devuelto, total de resultados, total de páginas y tamaño.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("readonly"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    // Obtiene filas de tablas de negocio en orden estable para poder comparar antes y después.
    private List<?> businessSnapshot() {
        return List.of(
                jdbc.queryForList("SELECT * FROM customer ORDER BY id"),
                jdbc.queryForList("SELECT * FROM product ORDER BY id"),
                jdbc.queryForList("SELECT * FROM inventory_balance ORDER BY product_id"),
                jdbc.queryForList("SELECT * FROM sales_order ORDER BY id"),
                jdbc.queryForList("SELECT * FROM order_line ORDER BY id"),
                jdbc.queryForList("SELECT * FROM stock_movement ORDER BY id"),
                jdbc.queryForList("SELECT * FROM stock_reservation ORDER BY order_id,product_id"),
                jdbc.queryForList("SELECT * FROM app_user ORDER BY id"),
                jdbc.queryForList("SELECT * FROM audit_event ORDER BY id"));
    }
}
