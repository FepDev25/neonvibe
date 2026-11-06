package com.neonvibe.controller;

import com.neonvibe.dto.YouTubeImportJobResponse;
import com.neonvibe.dto.YouTubeImportRequest;
import com.neonvibe.dto.YouTubeStatusResponse;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.ytdlp.YouTubeImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only YouTube import used by the upload page: availability check,
 * asynchronous job start and polling.
 */
@RestController
@RequestMapping("/api/v1/admin/upload/youtube")
public class YouTubeImportController {

    private final YouTubeImportService importService;
    private final AdminGuard adminGuard;

    public YouTubeImportController(YouTubeImportService importService, AdminGuard adminGuard) {
        this.importService = importService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/status")
    public ResponseEntity<YouTubeStatusResponse> status(Authentication authentication) {
        adminGuard.requireAdmin(principal(authentication));
        return ResponseEntity.ok(importService.status());
    }

    @PostMapping
    public ResponseEntity<YouTubeImportJobResponse> importVideo(
            @Valid @RequestBody YouTubeImportRequest request,
            Authentication authentication) {
        adminGuard.requireAdmin(principal(authentication));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(importService.start(request.url()));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<YouTubeImportJobResponse> job(@PathVariable String jobId,
                                                        Authentication authentication) {
        adminGuard.requireAdmin(principal(authentication));
        return ResponseEntity.ok(importService.job(jobId));
    }

    private UserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new InvalidTokenException("Authentication required");
    }
}
