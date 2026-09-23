package com.neonvibe.controller;

import java.net.URI;
import java.util.Map;

import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.LastFmAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Last.fm OAuth flow: request auth URL, receive the callback and disconnect.
 */
@RestController
@RequestMapping("/api/v1/lastfm")
public class LastFmController {

    private final LastFmAuthService authService;
    private final String frontendBase;

    public LastFmController(LastFmAuthService authService,
                            @Value("${neonvibe.frontend-base:http://localhost:5173}") String frontendBase) {
        this.authService = authService;
        this.frontendBase = frontendBase;
    }

    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> authUrl() {
        if (!authService.configured()) {
            return ResponseEntity.ok(Map.of("url", "", "configured", false));
        }
        UserPrincipal user = SecurityUtils.currentUser();
        String url = authService.authUrlFor(user.id());
        if (url == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("url", "", "configured", true, "error", "Last.fm request_token failed"));
        }
        return ResponseEntity.ok(Map.of("url", url, "configured", true));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("token") String token) {
        boolean ok = authService.completeCallback(token);
        String path = ok ? "/settings?lastfm=connected" : "/settings?lastfm=error";
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontendBase + path)).build();
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect() {
        UserPrincipal user = SecurityUtils.currentUser();
        authService.disconnect(user.id());
        return ResponseEntity.noContent().build();
    }
}
