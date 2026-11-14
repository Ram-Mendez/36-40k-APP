package com.acme.operations.common;

import org.springframework.http.HttpStatus;

// Unproven business error; by default it causes rollback when you leave a Spring transaction.
public class BusinessException extends RuntimeException {
    // HTTP status that ApiExceptionHandler will use when this error reaches the controller.
    private final HttpStatus status;
    // A stable code that allows the customer to distinguish the cause without interpreting the message.
    private final String code;

    // Group status, code and explanation; here no HTTP response is yet sent.
    public BusinessException(HttpStatus status, String code, String message) {
        // Save the text in RuntimeException to recover it with getMessage ().
        super(message);
        // Keep status and code so the handler can read them.
        this.status = status;
        // It retains the business code that the front will be able to distinguish from the message.
        this.code = code;
    }

    // It exposes the state chosen by the business rule that released the exception.
    public HttpStatus getStatus() {
        // Return the current value.
        return status;
    }

    // It exposes the functional code of the error, for example INVALID_QUANTITY.
    public String getCode() {
        // Return the current value.
        return code;
    }
}
