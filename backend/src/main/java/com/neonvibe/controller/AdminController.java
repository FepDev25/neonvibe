package com.neonvibe.controller;

import com.neonvibe.dto.ScanStatusResponse;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.ScanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scanner administration endpoints. Besides authentication, they require admin
 * privileges (see {@link AdminGuard} / {@code ADMIN_EMAILS}).
 */
@RestController
@RequestMapping("/api/v1/admin/scan")
public class AdminController {

    private final ScanService scanService;
    private final AdminGuard adminGuard;

    public AdminController(ScanService scanService, AdminGuard adminGuard) {
        this.scanService = scanService;
        this.adminGuard = adminGuard;
    }

    @PostMapping
    public ResponseEntity<Void> triggerScan(Authentication authentication) {
        adminGuard.requireAdmin(principal(authentication));
        scanService.triggerScan();
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/status")
    public ResponseEntity<ScanStatusResponse> status(Authentication authentication) {
        adminGuard.requireAdmin(principal(authentication));
        return ResponseEntity.ok(scanService.status());
    }

    private UserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new InvalidTokenException("Authentication required");
    }
}
