package com.acme.operations.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Spring manages this component and injects it wherever needed.
@Component
// It runs with high priority to prepare traceability before the rest of the flow.
@Order(Ordered.HIGHEST_PRECEDENCE)
// OncePerQuestFilter provides integration with the Spring filter chain.
public class RequestLoggingFilter extends OncePerRequestFilter {
    // Logger associated with this class to record diagnosis on the server.
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // The compiler checks that this method overrides the inherited declaration.
    @Override
    // Spring calls this method to wrap the request processing.
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Read the identifier the client could send to correlate the request.
        String candidate = request.getHeader("X-Request-ID");
        // It reuses only a limited format; if it is not valid, it generates a new UUID.
        String id =
                candidate != null && candidate.matches("[A-Za-z0-9_-]{1,64}")
                        ? candidate
                        : UUID.randomUUID().toString();
        // Save the id in the context of the current thread log.
        MDC.put("requestId", id);
        // Return that same go to the client in a header.
        response.setHeader("X-Request-ID", id);
        // It measures duration with monotonic watch, suitable for calculating time elapsed.
        long start = System.nanoTime();
        // Finally, it guarantees the cleaning of the context even if another filter or controller fails.
        try {
            // Deliver the request to the next filter; without this call it would not reach the controller.
            chain.doFilter(request, response);
        // On returning from the rest of the chain data are recorded and the thread is cleaned.
        } finally {
            // Write method, route, status and duration in milliseconds.
            log.info(
                    "http method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            // Clear the requestId so that the next request on this thread cannot inherit it.
            MDC.remove("requestId");
        }
    }
}
