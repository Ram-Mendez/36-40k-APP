package com.acme.operations.common;

import org.springframework.http.HttpStatus;

// Error de negocio no comprobado; por defecto provoca rollback al salir de una transacción Spring.
public class BusinessException extends RuntimeException {
    // Estado HTTP que ApiExceptionHandler utilizará cuando este error llegue al controlador.
    private final HttpStatus status;
    // Código estable que permite al cliente distinguir la causa sin interpretar el mensaje.
    private final String code;

    // Agrupa estado, código y explicación; aquí todavía no se envía ninguna respuesta HTTP.
    public BusinessException(HttpStatus status, String code, String message) {
        // Guarda el texto en RuntimeException para recuperarlo con getMessage().
        super(message);
        // Conserva estado y código para que el handler pueda leerlos.
        this.status = status;
        // Conserva el código de negocio que el frontend podrá distinguir del mensaje.
        this.code = code;
    }

    // Expone el estado elegido por la regla de negocio que lanzó la excepción.
    public HttpStatus getStatus() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return status;
    }

    // Expone el código funcional del error, por ejemplo INVALID_QUANTITY.
    public String getCode() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return code;
    }
}
