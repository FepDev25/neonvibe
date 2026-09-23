package com.neonvibe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context load test: verifies the Spring application context starts without errors.
 */
@SpringBootTest
@ActiveProfiles("test")
class NeonVibeApplicationTests {

    @Test
    void contextLoads() {
        // Empty: if the context fails to load, the test fails.
    }
}
