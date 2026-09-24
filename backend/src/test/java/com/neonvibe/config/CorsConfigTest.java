package com.neonvibe.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorsConfig}: empty origins fall back to a wildcard while
 * credentials stay disabled, and configured origins are used verbatim.
 */
class CorsConfigTest {

    private CorsConfig config(List<String> origins) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config;
    }

    private CorsConfiguration cors(CorsConfig config) {
        return config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/tracks"));
    }

    @Test
    void emptyOrigins_fallBackToWildcard() {
        CorsConfiguration cors = cors(config(List.of()));

        assertThat(cors.getAllowedOrigins()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.getAllowedMethods())
                .contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    @Test
    void configuredOrigins_areUsedVerbatim() {
        CorsConfiguration cors = cors(config(List.of("https://neonvibe.example")));

        assertThat(cors.getAllowedOrigins()).containsExactly("https://neonvibe.example");
    }
}
