package ca.carletonu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Mini-Shopify application.
 *
 * @SpringBootApplication turns on component scanning from this package down,
 * auto-configuration, and the embedded web server. The Spring Boot Maven
 * plugin needs exactly one class like this to build a runnable JAR.
 */
@SpringBootApplication
public class Sysc4806Application {

    public static void main(String[] args) {
        SpringApplication.run(Sysc4806Application.class, args);
    }
}
