package com.neonvibe.security;

import java.io.IOException;
import java.util.UUID;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads {@code Authorization: Bearer <token>}, validates the access token and,
 * when valid, populates the Spring SecurityContext with a {@link UserPrincipal}.
 *
 * <p>When no Authorization header is present, the token is also accepted from the
 * {@code ?token=} query parameter. This lets media elements ({@code <audio>}),
 * which cannot send HTTP headers, authenticate against the streaming endpoint
 * (e.g. {@code /api/v1/tracks/{id}/stream?token=...}).</p>
 *
 * <p>When the token is missing or invalid the request simply continues down the
 * chain unauthenticated; the authorization rules in {@code SecurityConfig}
 * decide whether a 401 is returned.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String subject = tokenProvider.validateAccessToken(token);
                UUID userId = UUID.fromString(subject);
                var principal = new UserPrincipal(userId, null, null);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, java.util.List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejecting invalid access token: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        // Query-param tokens are only accepted for streaming endpoints (<audio>
        // cannot send headers). Keeping the surface minimal avoids JWT leakage
        // into URLs/logs for other API routes.
        // Query-param tokens are only accepted for media endpoints (<audio> and
        // <img> cannot send headers): stream + cover. Keeping the surface minimal
        // avoids JWT leakage into URLs/logs for other API routes.
        String uri = request.getRequestURI();
        boolean mediaPath = uri.endsWith("/stream") || uri.endsWith("/cover");
        if (mediaPath && (uri.startsWith("/api/v1/tracks/") || uri.startsWith("/api/v1/albums/")
                || uri.startsWith("/api/v1/artists/"))) {
            String param = request.getParameter("token");
            return param == null || param.isBlank() ? null : param;
        }
        return null;
    }
}
