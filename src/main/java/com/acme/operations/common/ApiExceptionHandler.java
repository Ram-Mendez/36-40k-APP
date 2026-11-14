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

// Spring directs here exceptions from the drivers to return uniform JSON errors.
@RestControllerAdvice
// Give this advice priority over other global exception handlers.
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    // It records server diagnoses; the log is not sent as HTTP response.
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Spring invokes this method when a BusinessException comes from the HTTP flow.
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException e, HttpServletRequest request) {
        // It records code, method and route to identify the rejection without exposing internal data.
        log.info(
                "business_rejection code={} method={} path={}",
                e.getCode(),
                request.getMethod(),
                request.getRequestURI());
        // Use status, code and message of the exception to build the customer response.
        return response(e.getStatus(), e.getCode(), e.getMessage(), request);
    }

    // Group JSON errors, types, parameters and validation that prevent you from interpreting the request.
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    ResponseEntity<ProblemDetail> malformed(Exception e, HttpServletRequest request) {
        // Returns 400 with an invalid general input message.
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request data", request);
    }

    // Collect @Valid's faults on the body of the request.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        // Prepare the answer 400 before adding details of the invalid fields.
        var result =
                response(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        request);
        // Transforms each field error into a text that the client can show.
        List<String> violations =
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + ": " + f.getDefaultMessage())
                        .toList();
        // Add violations to the JSON and return the extended response.
        result.getBody().setProperty("violations", violations);
        return result;
    }

    // Translate unfulfilled SQL restrictions, such as duplicates or invalid references.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(Exception e, HttpServletRequest request) {
        // 409 reports a conflict of integrity without showing the internal SQL.
        return response(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                "Duplicate value or database integrity conflict",
                request);
    }

    // It collects errors of concurrence that Spring translates from the layer of persistence.
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ProblemDetail> concurrency(Exception e, HttpServletRequest request) {
        // Returns 409 for the customer to recharge before trying another modification.
        return response(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Resource changed concurrently; reload and retry",
                request);
    }

    // It manages refusal of authorisation that reaches the MVC's advice.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(Exception e, HttpServletRequest request) {
        // 403 indicates that this action is not allowed, even if the user can be authenticated.
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Operation not permitted", request);
    }

    // Transforms an absent resource detected by Spring MVC in 404.
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> missing(Exception e, HttpServletRequest request) {
        // Returns a uniform error of appeal not found.
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    // It drives an existing route called an unadmitted HTTP method.
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(
            org.springframework.web.HttpRequestMethodNotSupportedException e,
            HttpServletRequest request) {
        // Prepare error 405 to distinguish it from a non-existing route.
        var result =
                response(
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "METHOD_NOT_ALLOWED",
                        "HTTP method not supported",
                        request);
        // He gets the methods that he does accept the appeal.
        var allowed = e.getSupportedHttpMethods();
        // Add the Allow header with the accepted methods and keep the body of error.
        return ResponseEntity.status(result.getStatusCode())
                .allow(
                        allowed == null
                                ? new org.springframework.http.HttpMethod[0]
                                : allowed.toArray(org.springframework.http.HttpMethod[]::new))
                .body(result.getBody());
    }

    // Captures a Content-Type input that the application can't read.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> contentType(Exception e, HttpServletRequest request) {
        // Returns 415 to indicate the problem with the sent format.
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content type not supported",
                request);
    }

    // Captures a request whose Accept requires a response format not available.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> accept(Exception e, HttpServletRequest request) {
        // Returns 406 to indicate that it cannot produce that format.
        return response(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "Response format not supported",
                request);
    }

    // It is the general output for exceptions that do not have a more specific handler.
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        // Save the technical detail and exception in diagnostic logs.
        log.error(
                "unexpected_error method={} path={}",
                request.getMethod(),
                request.getRequestURI(),
                e);
        // It responds 500 with a generic message, without revealing the internal trace to the client.
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error; provide requestId to support",
                request);
    }

    // It builds both the Problem Detail body and the real HTTP state.
    private ResponseEntity<ProblemDetail> response(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        // It creates the status and detail fields of the JSON from the error received.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        // Use the standard name of the HTTP state as the title of the problem.
        problem.setTitle(status.getReasonPhrase());
        // It generates a stable error type identifier using the business code.
        problem.setType(
                URI.create(
                        "urn:business-operations:error:"
                                + code.toLowerCase(java.util.Locale.ROOT)));
        // It includes the failed route and the code that the front can interpret.
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        // Use the requestId from the filter to find the corresponding log entries.
        problem.setProperty("requestId", MDC.get("requestId"));
        // It establishes the HTTP status; it is not enough to put status within the JSON.
        var builder = ResponseEntity.status(status);
        // For 401 it announces that the API is waiting for Bearer credentials.
        if (status == HttpStatus.UNAUTHORIZED) builder.header("WWW-Authenticate", "Bearer");
        // He associates the JSON to the ResponseEntity that Spring will write in the answer.
        return builder.body(problem);
    }
}
