package com.neonvibe.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the security filter chain: public endpoints stay open,
 * protected endpoints reject unauthenticated requests with 401 JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpoint_isNotBlockedBySecurity() throws Exception {
        // The route is public: it must return a non-401 response even without a token.
        // (GET on the POST-only endpoint -> 405 method not allowed, handled by GlobalExceptionHandler.)
        mockMvc.perform(get("/api/v1/auth/refresh"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void protectedApiEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void spaRoute_doesNotRequireAuth() throws Exception {
        // The SPA deep links must be reachable without a token (the frontend is
        // served from classpath:/static and only the API requires auth). If the
        // static build is absent this still must not be a 401.
        mockMvc.perform(get("/library")).andExpect(result -> {
            int status = result.getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                    "SPA routes must not require auth, got " + status);
        });
    }

    @Test
    void actuatorInfo_requiresAuth() throws Exception {
        // Defense in depth: only /actuator/health is public.
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }
}
