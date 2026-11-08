package com.acme.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Activa autoconfiguración y búsqueda de componentes desde este paquete y sus subpaquetes.
@SpringBootApplication
public class BusinessOperationsApplication {
    // Punto de entrada de Java; recibe opciones de arranque desde la línea de comandos.
    public static void main(String[] args) {
        // Spring crea el contexto, conecta sus beans y arranca el servidor web configurado.
        SpringApplication.run(BusinessOperationsApplication.class, args);
    }
}
