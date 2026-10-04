package com.neonvibe.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.neonvibe.dto.UploadResultResponse;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.scanner.IngestService;
import com.neonvibe.scanner.ScannerConfig;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.UserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-only upload of new albums/tracks. Files (or a ZIP) are written to a
 * staging directory outside the watched library, then organized into
 * {@code <Artist>/<Album>/} and ingested.
 */
@RestController
@RequestMapping("/api/v1/admin/upload")
public class IngestController {

    /** Upper bound per request; large batches should use the incoming folder. */
    private static final int MAX_FILES = 200;

    private final IngestService ingestService;
    private final ScannerConfig scannerConfig;
    private final AdminGuard adminGuard;

    public IngestController(IngestService ingestService,
                            ScannerConfig scannerConfig,
                            AdminGuard adminGuard) {
        this.ingestService = ingestService;
        this.scannerConfig = scannerConfig;
        this.adminGuard = adminGuard;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResultResponse> upload(
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) throws IOException {
        adminGuard.requireAdmin(principal(authentication));

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files were uploaded");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Too many files in one upload (max " + MAX_FILES + ")");
        }

        Path staging = scannerConfig.resolveStagingPath().toAbsolutePath()
                .resolve("upload-" + UUID.randomUUID());
        Files.createDirectories(staging);
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                Path target = staging.resolve(IngestService.safeUploadName(file.getOriginalFilename()));
                file.transferTo(target);
            }
            IngestService.IngestResult result = ingestService.ingestUpload(staging);
            return ResponseEntity.ok(
                    new UploadResultResponse(result.processed(), result.failed(), result.errors()));
        } finally {
            // ingestUpload wipes staging on success; this covers earlier failures.
            IngestService.deleteQuietly(staging);
        }
    }

    private UserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new InvalidTokenException("Authentication required");
    }
}
