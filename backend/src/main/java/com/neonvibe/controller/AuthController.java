package com.neonvibe.controller;

import com.neonvibe.dto.AuthResponse;
import com.neonvibe.dto.GoogleTokenRequest;
import com.neonvibe.dto.RefreshTokenRequest;
import com.neonvibe.dto.UserResponse;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints: Google login, token refresh and current user.
 *
 * <p>Base path {@code /api/v1/auth} is public in {@code SecurityConfig}; the
 * {@code /me} endpoint resolves the identity from the Bearer token itself and
 * returns 401 (via {@link InvalidTokenException}) when no valid token is present.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleTokenRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        UserPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok(authService.me(principal.id()));
    }

    private UserPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal p)) {
            throw new InvalidTokenException("Authentication required");
        }
        return p;
    }
}
