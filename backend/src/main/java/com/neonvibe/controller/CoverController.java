package com.neonvibe.controller;

import java.io.IOException;

import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.security.AdminGuard;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.AlbumService;
import com.neonvibe.service.ArtistService;
import com.neonvibe.service.CoverArtService;
import com.neonvibe.service.CoverArtService.CoverResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cover art endpoints for albums, tracks and artists. GET endpoints return raw
 * image bytes (cached/placeholder); POST endpoints accept manual uploads.
 *
 * <p>Uploads are admin-only (they replace library-global artwork) and the real
 * image type is sniffed from the bytes — the client Content-Type is never
 * trusted, and SVG is rejected to avoid stored-XSS via {@code image/svg+xml}.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CoverController {

    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final CoverArtService coverArtService;
    private final AlbumService albumService;
    private final ArtistService artistService;
    private final AdminGuard adminGuard;

    public CoverController(CoverArtService coverArtService, AlbumService albumService,
                           ArtistService artistService, AdminGuard adminGuard) {
        this.coverArtService = coverArtService;
        this.albumService = albumService;
        this.artistService = artistService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/albums/{id}/cover")
    public ResponseEntity<byte[]> albumCover(@PathVariable Long id) {
        return image(coverArtService.getAlbumCover(id));
    }

    @GetMapping("/tracks/{id}/cover")
    public ResponseEntity<byte[]> trackCover(@PathVariable Long id) {
        return image(coverArtService.getTrackCover(id));
    }

    @GetMapping("/artists/{id}/cover")
    public ResponseEntity<byte[]> artistCover(@PathVariable Long id) {
        return image(coverArtService.getArtistCover(id));
    }

    @PostMapping(value = "/albums/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumResponse> uploadAlbumCover(@PathVariable Long id,
                                                          @RequestParam("file") MultipartFile file,
                                                          Authentication authentication) throws IOException {
        adminGuard.requireAdmin(principal(authentication));
        byte[] bytes = validate(file);
        coverArtService.saveManualAlbum(id, bytes, detectContentType(bytes));
        return ResponseEntity.ok(albumService.getById(id));
    }

    @PostMapping(value = "/artists/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArtistResponse> uploadArtistCover(@PathVariable Long id,
                                                            @RequestParam("file") MultipartFile file,
                                                            Authentication authentication) throws IOException {
        adminGuard.requireAdmin(principal(authentication));
        byte[] bytes = validate(file);
        coverArtService.saveManualArtist(id, bytes, detectContentType(bytes));
        return ResponseEntity.ok(artistService.getById(id));
    }

    private ResponseEntity<byte[]> image(CoverResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Cache-Control", "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .body(result.bytes());
    }

    private byte[] validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File too large (max 10MB)");
        }
        return file.getBytes();
    }

    /**
     * Detects the image type from magic bytes. Only PNG/JPEG/WebP are accepted;
     * anything else (notably SVG) is rejected regardless of the declared
     * Content-Type.
     */
    private String detectContentType(byte[] bytes) {
        if (startsWith(bytes, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        throw new IllegalArgumentException("Unsupported image format (allowed: PNG, JPEG, WebP)");
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] d) {
        return d.length >= 12
                && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    private UserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new InvalidTokenException("Authentication required");
    }
}
