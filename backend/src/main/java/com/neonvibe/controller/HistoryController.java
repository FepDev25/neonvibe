package com.neonvibe.controller;

import com.neonvibe.dto.PlayHistoryRequest;
import com.neonvibe.dto.PlayHistoryResponse;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.PlayHistoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playback history endpoints, scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final PlayHistoryService playHistoryService;

    public HistoryController(PlayHistoryService playHistoryService) {
        this.playHistoryService = playHistoryService;
    }

    @GetMapping
    public ResponseEntity<Page<PlayHistoryResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(playHistoryService.listForUser(user.id(), pageable));
    }

    @PostMapping
    public ResponseEntity<PlayHistoryResponse> record(@Valid @RequestBody PlayHistoryRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(playHistoryService.record(user.id(), request));
    }
}
