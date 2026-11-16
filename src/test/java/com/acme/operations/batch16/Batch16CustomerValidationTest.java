package com.acme.operations.batch16;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.operations.common.ApiExceptionHandler;
import com.acme.operations.customer.CustomerController;
import com.acme.operations.customer.CustomerDtos;
import com.acme.operations.customer.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP validation with the real controller and DTO, without persistence or authentication. */
class Batch16CustomerValidationTest {
    private CustomerService customers;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerService.class);
        when(customers.create(any())).thenAnswer(invocation -> {
            CustomerDtos.Input input = invocation.getArgument(0);
            return new CustomerDtos.View(42, input.name(), input.taxId(), input.email(),
                    input.address(), true, 0);
        });
        mvc = MockMvcBuilders.standaloneSetup(new CustomerController(customers))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void malformedEmailIsRejectedBeforeCallingTheService() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(body("ana.example.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.startsWith("email:")));
        verify(customers, never()).create(any());
    }

    @Test
    void validEmailStillCreatesTheCustomer() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(body("ana@example.test")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/42"))
                .andExpect(jsonPath("$.email").value("ana@example.test"));
        verify(customers).create(any());
    }

    @Test
    void emptyEmailIsStillRejected() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());
        verify(customers, never()).create(any());
    }

    private static String body(String email) {
        return """
                {"name":"Batch 16 customer","taxId":"BATCH16-CUSTOMER",
                 "email":"%s","address":"16 Test Street"}
                """.formatted(email);
    }
}
