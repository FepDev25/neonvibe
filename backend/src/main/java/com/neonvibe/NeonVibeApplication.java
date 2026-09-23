package com.neonvibe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * NeonVibe - self-hosted personal music server.
 *
 * <p>Fase 0: bootstrap. Aplicacion principal que levanta el contexto de Spring Boot.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NeonVibeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeonVibeApplication.class, args);
    }
}
