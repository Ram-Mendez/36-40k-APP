package com.acme.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// It activates self-configuration and search for components from this package and its subpackages.
@SpringBootApplication
public class BusinessOperationsApplication {
    // Java input point; gets boot options from the command line.
    public static void main(String[] args) {
        // Spring creates the context, connects your beans and starts the configured web server.
        SpringApplication.run(BusinessOperationsApplication.class, args);
    }
}
