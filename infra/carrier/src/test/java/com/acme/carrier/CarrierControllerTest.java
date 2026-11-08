package com.acme.carrier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CarrierControllerTest {
    @Test
    void normalQuoteAndHealthUseJson() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CarrierController("normal")).build();
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/quotes").param("orderId", "123").param("amount", "200"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reference").value("QUOTE-123"))
                .andExpect(jsonPath("$.cost").value(7.50));
        mvc.perform(get("/quotes").param("orderId", "bad"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reference").value("QUOTE-unknown"));
    }

    @Test
    void unavailableReturns503() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CarrierController("unavailable")).build();
        mvc.perform(get("/quotes")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("temporarily unavailable"));
    }

    @Test
    void malformedReturnsInvalidJson() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CarrierController("malformed")).build();
        mvc.perform(get("/quotes")).andExpect(status().isOk()).andExpect(content().string("{invalid-json"));
    }
}
