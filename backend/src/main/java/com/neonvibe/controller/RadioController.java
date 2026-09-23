package com.neonvibe.controller;

import java.util.List;

import com.neonvibe.dto.TrackResponse;
import com.neonvibe.service.RadioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Similarity radio: generates a queue of tracks related to a seed track.
 */
@RestController
@RequestMapping("/api/v1/radio")
public class RadioController {

    private final RadioService radioService;

    public RadioController(RadioService radioService) {
        this.radioService = radioService;
    }

    @GetMapping("/seed")
    public ResponseEntity<List<TrackResponse>> seed(@RequestParam("track_id") Long trackId,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(radioService.radioForSeed(trackId, size));
    }
}
