package com.neonvibe.controller;

import java.util.Map;

import com.neonvibe.dto.SettingsRequest;
import com.neonvibe.dto.SettingsResponse;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.UserSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persisted user settings + cache management.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final UserSettingsService settingsService;

    public SettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<SettingsResponse> get() {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(settingsService.getForUser(user.id()));
    }

    @PutMapping
    public ResponseEntity<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(settingsService.update(user.id(), request));
    }

    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Integer>> clearCache() {
        int cleared = settingsService.clearCache();
        return ResponseEntity.ok(Map.of("cleared", cleared));
    }
}
