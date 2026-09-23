package com.neonvibe.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration.
 *
 * <p>Enforces snake_case for JSON property naming (matches the database
 * naming convention and the API contract in AGENTS.md).</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer snakeCaseNaming() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
