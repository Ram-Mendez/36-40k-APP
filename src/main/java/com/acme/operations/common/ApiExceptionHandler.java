package com.acme.operations.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;

// Spring dirige aquí excepciones de los controladores para devolver errores JSON uniformes.
@RestControllerAdvice
// Da prioridad a este advice frente a otros manejadores globales.
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    // Registra diagnósticos del servidor; no se envía el log como respuesta HTTP.
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Spring invoca este método cuando llega una BusinessException desde el flujo HTTP.
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException e, HttpServletRequest request) {
        // Registra código, método y ruta para identificar el rechazo sin exponer datos internos.
        log.info(
                "business_rejection code={} method={} path={}",
                e.getCode(),
                request.getMethod(),
                request.getRequestURI());
        // Usa status, code y message de la excepción para construir la respuesta del cliente.
        return response(e.getStatus(), e.getCode(), e.getMessage(), request);
    }

    // Agrupa errores de JSON, tipos, parámetros y validación que impiden interpretar la petición.
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    ResponseEntity<ProblemDetail> malformed(Exception e, HttpServletRequest request) {
        // Devuelve 400 con un mensaje general de entrada inválida.
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request data", request);
    }

    // Recoge los fallos de @Valid sobre el cuerpo de la petición.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        // Prepara la respuesta 400 antes de añadir detalles de los campos inválidos.
        var result =
                response(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        request);
        // Transforma cada error de campo en un texto que el cliente pueda mostrar.
        List<String> violations =
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + ": " + f.getDefaultMessage())
                        .toList();
        // Añade violations al JSON y devuelve la respuesta ampliada.
        result.getBody().setProperty("violations", violations);
        return result;
    }

    // Traduce restricciones SQL incumplidas, como duplicados o referencias inválidas.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(Exception e, HttpServletRequest request) {
        // 409 informa de un conflicto de integridad sin mostrar el SQL interno.
        return response(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                "Duplicate value or database integrity conflict",
                request);
    }

    // Recoge errores de concurrencia que Spring traduce desde la capa de persistencia.
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ProblemDetail> concurrency(Exception e, HttpServletRequest request) {
        // Devuelve 409 para que el cliente recargue antes de intentar otra modificación.
        return response(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Resource changed concurrently; reload and retry",
                request);
    }

    // Maneja denegaciones de autorización que llegan al advice de MVC.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(Exception e, HttpServletRequest request) {
        // 403 indica que no se permite esta acción, aunque el usuario pueda estar autenticado.
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Operation not permitted", request);
    }

    // Transforma un recurso ausente detectado por Spring MVC en 404.
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> missing(Exception e, HttpServletRequest request) {
        // Devuelve un error uniforme de recurso no encontrado.
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    // Maneja una ruta existente llamada con un método HTTP no admitido.
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(
            org.springframework.web.HttpRequestMethodNotSupportedException e,
            HttpServletRequest request) {
        // Prepara el error 405 para distinguirlo de una ruta inexistente.
        var result =
                response(
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "METHOD_NOT_ALLOWED",
                        "HTTP method not supported",
                        request);
        // Obtiene los métodos que sí acepta el recurso.
        var allowed = e.getSupportedHttpMethods();
        // Añade la cabecera Allow con los métodos admitidos y conserva el cuerpo del error.
        return ResponseEntity.status(result.getStatusCode())
                .allow(
                        allowed == null
                                ? new org.springframework.http.HttpMethod[0]
                                : allowed.toArray(org.springframework.http.HttpMethod[]::new))
                .body(result.getBody());
    }

    // Captura un Content-Type de entrada que la aplicación no sabe leer.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> contentType(Exception e, HttpServletRequest request) {
        // Devuelve 415 para indicar el problema con el formato enviado.
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content type not supported",
                request);
    }

    // Captura una petición cuyo Accept exige un formato de respuesta no disponible.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> accept(Exception e, HttpServletRequest request) {
        // Devuelve 406 para indicar que no puede producir ese formato.
        return response(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "Response format not supported",
                request);
    }

    // Es la salida general para excepciones que no tienen un manejador más específico.
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        // Guarda el detalle técnico y la excepción en logs para diagnóstico.
        log.error(
                "unexpected_error method={} path={}",
                request.getMethod(),
                request.getRequestURI(),
                e);
        // Responde 500 con un mensaje genérico, sin revelar la traza interna al cliente.
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error; provide requestId to support",
                request);
    }

    // Construye tanto el cuerpo ProblemDetail como el estado HTTP real.
    private ResponseEntity<ProblemDetail> response(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        // Crea los campos status y detail del JSON a partir del error recibido.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        // Usa el nombre estándar del estado HTTP como título del problema.
        problem.setTitle(status.getReasonPhrase());
        // Genera un identificador estable de tipo de error usando el código de negocio.
        problem.setType(
                URI.create(
                        "urn:business-operations:error:"
                                + code.toLowerCase(java.util.Locale.ROOT)));
        // Incluye la ruta que falló y el código que podrá interpretar el frontend.
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        // Relaciona el error con los logs mediante el requestId del filtro.
        problem.setProperty("requestId", MDC.get("requestId"));
        // Establece el estado HTTP; no basta con poner status dentro del JSON.
        var builder = ResponseEntity.status(status);
        // Para 401 anuncia que la API espera credenciales Bearer.
        if (status == HttpStatus.UNAUTHORIZED) builder.header("WWW-Authenticate", "Bearer");
        // Asocia el JSON al ResponseEntity que Spring escribirá en la respuesta.
        return builder.body(problem);
    }
}
