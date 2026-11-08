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

// Spring crea y gestiona este componente para poder inyectarlo donde se necesita.
@Component
// Se ejecuta con alta prioridad para preparar la trazabilidad antes del resto del flujo.
@Order(Ordered.HIGHEST_PRECEDENCE)
// OncePerRequestFilter proporciona la integración con la cadena de filtros de Spring.
public class RequestLoggingFilter extends OncePerRequestFilter {
    // Logger asociado a esta clase para registrar diagnóstico en el servidor.
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // El compilador comprueba que este método implementa o sobrescribe el contrato heredado.
    @Override
    // Spring llama a este método para envolver el procesamiento de la petición.
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Lee el identificador que el cliente pudo enviar para correlacionar la petición.
        String candidate = request.getHeader("X-Request-ID");
        // Reutiliza solo un formato limitado; si no es válido, genera un UUID nuevo.
        String id =
                candidate != null && candidate.matches("[A-Za-z0-9_-]{1,64}")
                        ? candidate
                        : UUID.randomUUID().toString();
        // Guarda el id en el contexto de logs del hilo actual.
        MDC.put("requestId", id);
        // Devuelve ese mismo id al cliente en una cabecera.
        response.setHeader("X-Request-ID", id);
        // Mide duración con reloj monotónico, adecuado para calcular tiempo transcurrido.
        long start = System.nanoTime();
        // finally garantiza la limpieza del contexto aunque otro filtro o controlador falle.
        try {
            // Entrega la petición al siguiente filtro; sin esta llamada no llegaría al controlador.
            chain.doFilter(request, response);
        // Al regresar del resto de la cadena se registran datos y se limpia el hilo.
        } finally {
            // Escribe método, ruta, estado y duración en milisegundos.
            log.info(
                    "http method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            // Evita que otra petición que reutilice este hilo herede el requestId anterior.
            MDC.remove("requestId");
        }
    }
}
