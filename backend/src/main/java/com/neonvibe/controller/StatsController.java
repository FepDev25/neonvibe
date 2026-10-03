package com.neonvibe.controller;

import java.time.ZoneId;
import java.util.List;

import com.neonvibe.dto.StatsHourItem;
import com.neonvibe.dto.StatsOverviewResponse;
import com.neonvibe.dto.StatsTimelineResponse;
import com.neonvibe.dto.StatsTopItem;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.StatsService;
import com.neonvibe.stats.StatsBucket;
import com.neonvibe.stats.StatsRange;
import com.neonvibe.stats.StatsTopType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal listening stats, scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<StatsOverviewResponse> overview(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(statsService.overview(user.id(), StatsRange.fromParam(range)));
    }

    @GetMapping("/top")
    public ResponseEntity<List<StatsTopItem>> top(
            @RequestParam(value = "type", required = false, defaultValue = "tracks") String type,
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(statsService.top(user.id(), StatsRange.fromParam(range),
                StatsTopType.fromParam(type), limit));
    }

    @GetMapping("/timeline")
    public ResponseEntity<StatsTimelineResponse> timeline(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @RequestParam(value = "bucket", required = false, defaultValue = "day") String bucket,
            @RequestParam(value = "tz", required = false, defaultValue = "UTC") String tz) {
        UserPrincipal user = SecurityUtils.currentUser();
        ZoneId zone = StatsService.zone(tz);
        return ResponseEntity.ok(statsService.timeline(user.id(), StatsRange.fromParam(range),
                StatsBucket.fromParam(bucket), zone));
    }

    @GetMapping("/hours")
    public ResponseEntity<List<StatsHourItem>> hours(
            @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
            @RequestParam(value = "tz", required = false, defaultValue = "UTC") String tz) {
        UserPrincipal user = SecurityUtils.currentUser();
        ZoneId zone = StatsService.zone(tz);
        return ResponseEntity.ok(statsService.hours(user.id(), StatsRange.fromParam(range), zone));
    }
}
