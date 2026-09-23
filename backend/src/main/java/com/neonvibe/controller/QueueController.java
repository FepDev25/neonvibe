package com.neonvibe.controller;

import com.neonvibe.dto.PlayQueueRequest;
import com.neonvibe.dto.PlayQueueResponse;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.PlayQueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-user play queue endpoints.
 */
@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final PlayQueueService playQueueService;

    public QueueController(PlayQueueService playQueueService) {
        this.playQueueService = playQueueService;
    }

    @GetMapping
    public ResponseEntity<PlayQueueResponse> get() {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(playQueueService.getForUser(user.id()));
    }

    @PutMapping
    public ResponseEntity<PlayQueueResponse> update(@Valid @RequestBody PlayQueueRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.status(HttpStatus.OK).body(playQueueService.saveForUser(user.id(), request));
    }
}
