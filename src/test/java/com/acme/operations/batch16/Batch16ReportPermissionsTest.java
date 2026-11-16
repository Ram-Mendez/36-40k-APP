package com.acme.operations.batch16;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.operations.common.ApiExceptionHandler;
import com.acme.operations.common.PageResponse;
import com.acme.operations.common.RequestLoggingFilter;
import com.acme.operations.report.ReportController;
import com.acme.operations.report.ReportService;
import com.acme.operations.security.SecurityConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real security filters and method permissions; identities and report data are test fixtures. */
@SpringJUnitWebConfig(Batch16ReportPermissionsTest.Config.class)
@TestPropertySource(properties = "security.jwt.secret=batch16-test-only-secret-at-least-32-bytes")
class Batch16ReportPermissionsTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ReportService reports;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(reports);
        when(reports.sales(null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestLoggingFilter()).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATIONS", "READONLY"})
    void businessRolesCanReadSales(String role) throws Exception {
        mvc.perform(get("/api/v1/reports/sales").with(user("batch-user").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(reports).sales(null, null, 0, 20);
    }

    @Test
    void unknownRoleCannotReadSales() throws Exception {
        mvc.perform(get("/api/v1/reports/sales").with(user("outside-user").roles("OUTSIDE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(reports, never()).sales(null, null, 0, 20);
    }

    @Test
    void anonymousRequestStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/reports/sales"))
                .andExpect(status().isUnauthorized());
        verify(reports, never()).sales(null, null, 0, 20);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, ReportController.class, ApiExceptionHandler.class})
    static class Config {
        @Bean ReportService reports() { return mock(ReportService.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean PlatformTransactionManager transactions() { return mock(PlatformTransactionManager.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
    }
}
