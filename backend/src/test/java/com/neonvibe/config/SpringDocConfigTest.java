package com.neonvibe.config;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the springdoc configuration. springdoc reads its settings
 * from the root-level {@code springdoc.*} namespace; nesting them under
 * {@code spring:} (as happened before) makes the settings silently ignored and
 * leaves Swagger UI exposed in production.
 */
class SpringDocConfigTest {

    private List<PropertySource<?>> load(String resource) throws IOException {
        return new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
    }

    @Test
    void baseConfig_declaresSpringdocAtRootLevel() throws IOException {
        List<PropertySource<?>> sources = load("application.yml");

        assertThat(sources)
                .anySatisfy(source -> {
                    assertThat(source.getProperty("springdoc.api-docs.path")).isEqualTo("/v3/api-docs");
                    assertThat(source.getProperty("springdoc.swagger-ui.path")).isEqualTo("/swagger-ui");
                });
        assertThat(sources)
                .noneSatisfy(source -> assertThat(source.getProperty("spring.springdoc.api-docs.path")).isNotNull());
        assertThat(sources)
                .noneSatisfy(source -> assertThat(source.getProperty("spring.springdoc.swagger-ui.path")).isNotNull());
    }

    @Test
    void prodConfig_disablesSpringdocAtRootLevel() throws IOException {
        List<PropertySource<?>> sources = load("application-prod.yml");

        assertThat(sources)
                .anySatisfy(source -> {
                    assertThat(source.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
                    assertThat(source.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
                });
        assertThat(sources)
                .noneSatisfy(source -> assertThat(source.getProperty("spring.springdoc.api-docs.enabled")).isNotNull());
        assertThat(sources)
                .noneSatisfy(source -> assertThat(source.getProperty("spring.springdoc.swagger-ui.enabled")).isNotNull());
    }

    @Test
    void springdocConfigClass_isPresent() {
        assertThat(OpenApiConfig.class).isNotNull();
    }
}
