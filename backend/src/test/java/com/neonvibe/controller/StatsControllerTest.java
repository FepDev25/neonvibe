package com.neonvibe.controller;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.neonvibe.config.SecurityConfig;
import com.neonvibe.dto.StatsHourItem;
import com.neonvibe.dto.StatsOverviewResponse;
import com.neonvibe.dto.StatsTimelineResponse;
import com.neonvibe.dto.StatsTopItem;
import com.neonvibe.security.JwtAuthenticationFilter;
import com.neonvibe.security.JwtTokenProvider;
import com.neonvibe.service.StatsService;
import com.neonvibe.stats.StatsBucket;
import com.neonvibe.stats.StatsRange;
import com.neonvibe.stats.StatsTopType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link StatsController}.
 */
@WebMvcTest(StatsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private StatsService statsService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + tokenProvider.generateAccessToken(
                UUID.randomUUID(), "u@example.com", "User");
    }

    @Test
    void overview_returnsAggregates() throws Exception {
        when(statsService.overview(any(), any()))
                .thenReturn(new StatsOverviewResponse(10, 3600, 8, 5, 3, 4));

        mockMvc.perform(get("/api/v1/stats/overview").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_plays").value(10))
                .andExpect(jsonPath("$.listened_seconds").value(3600))
                .andExpect(jsonPath("$.distinct_artists").value(3));
    }

    @Test
    void top_forwardsTypeRangeAndLimit() throws Exception {
        when(statsService.top(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new StatsTopItem(1L, "Radiohead", "", 20, 4000)));

        mockMvc.perform(get("/api/v1/stats/top")
                        .header("Authorization", bearerToken)
                        .param("type", "artists")
                        .param("range", "7d")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Radiohead"));

        verify(statsService).top(any(), eq(StatsRange.WEEK), eq(StatsTopType.ARTISTS), eq(5));
    }

    @Test
    void timeline_returnsPointsAndParsesTimezone() throws Exception {
        when(statsService.timeline(any(), any(), any(), any()))
                .thenReturn(new StatsTimelineResponse("day",
                        List.of(new StatsTimelineResponse.StatsPoint("2026-10-03", 3, 600))));

        mockMvc.perform(get("/api/v1/stats/timeline")
                        .header("Authorization", bearerToken)
                        .param("range", "30d")
                        .param("bucket", "day")
                        .param("tz", "Europe/Madrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("day"))
                .andExpect(jsonPath("$.points[0].period").value("2026-10-03"));

        verify(statsService).timeline(any(), eq(StatsRange.MONTH), eq(StatsBucket.DAY),
                eq(ZoneId.of("Europe/Madrid")));
    }

    @Test
    void hours_returns24Entries() throws Exception {
        when(statsService.hours(any(), any(), any()))
                .thenReturn(List.of(new StatsHourItem(0, 1), new StatsHourItem(1, 2)));

        mockMvc.perform(get("/api/v1/stats/hours").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hour").value(0))
                .andExpect(jsonPath("$[0].plays").value(1));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stats/overview"))
                .andExpect(status().isUnauthorized());
    }
}
