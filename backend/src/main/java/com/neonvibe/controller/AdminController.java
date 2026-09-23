package com.neonvibe.controller;

import com.neonvibe.dto.ScanStatusResponse;
import com.neonvibe.service.ScanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scanner administration endpoints. These require authentication via the global
 * {@code /api/**} rule; role-based (ADMIN only) restriction can be added later.
 */
@RestController
@RequestMapping("/api/v1/admin/scan")
public class AdminController {

    private final ScanService scanService;

    public AdminController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    public ResponseEntity<Void> triggerScan() {
        scanService.triggerScan();
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/status")
    public ResponseEntity<ScanStatusResponse> status() {
        return ResponseEntity.ok(scanService.status());
    }
}
