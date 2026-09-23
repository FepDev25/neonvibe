package com.neonvibe.controller;

import java.io.IOException;

import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.service.AlbumService;
import com.neonvibe.service.ArtistService;
import com.neonvibe.service.CoverArtService;
import com.neonvibe.service.CoverArtService.CoverResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 */
@RestController
@RequestMapping("/api/v1")
public class CoverController {

    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    private final CoverArtService coverArtService;
    private final AlbumService albumService;
    private final ArtistService artistService;

    public CoverController(CoverArtService coverArtService, AlbumService albumService, ArtistService artistService) {
        this.coverArtService = coverArtService;
        this.albumService = albumService;
        this.artistService = artistService;
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
                                                          @RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = validate(file);
        coverArtService.saveManualAlbum(id, bytes, contentType(file));
        return ResponseEntity.ok(albumService.getById(id));
    }

    @PostMapping(value = "/artists/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArtistResponse> uploadArtistCover(@PathVariable Long id,
                                                            @RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = validate(file);
        coverArtService.saveManualArtist(id, bytes, contentType(file));
        return ResponseEntity.ok(artistService.getById(id));
    }

    private ResponseEntity<byte[]> image(CoverResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Cache-Control", "private, max-age=86400")
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

    private String contentType(MultipartFile file) {
        String type = file.getContentType();
        return (type != null && (type.contains("png") || type.contains("svg") || type.contains("webp")))
                ? type : "image/jpeg";
    }
}
