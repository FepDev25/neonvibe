package com.neonvibe.config;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for NeonVibe.
 *
 * <p>Stateless API: CSRF disabled, sessions disabled, JWT bearer authentication via
 * {@link JwtAuthenticationFilter}. Public endpoints are limited to the health check,
 * auth flow and error handling; everything under {@code /api/**} requires a valid
 * access token. CORS reuses the {@link CorsConfig} bean.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { /* reuses CorsConfigurationSource bean from CorsConfig */ })
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/auth/**", "/api/v1/public/**",
                                "/api/v1/lastfm/callback", "/ws/**", "/error").permitAll()
                        // Keep the rest of actuator behind auth (defense in depth
                        // for future exposed endpoints like env/metrics).
                        .requestMatchers("/actuator/**").authenticated()
                        // The API is the only protected surface. Static assets and
                        // SPA routes (which the app serves from classpath:/static)
                        // are public; authentication is enforced at the API layer.
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(jsonAuthenticationEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Returns 401 with the standard error JSON contract instead of the default
     * Spring Security page.
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "unauthorized");
            body.put("message", "Authentication required or token is invalid/expired");
            body.put("timestamp", Instant.now().toString());
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }

    /**
     * Password encoder for potential local (email) authentication in a future
     * phase. Not used by the OAuth2-only flow of this phase.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
