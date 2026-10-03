package com.neonvibe.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.neonvibe.dto.StatsHourItem;
import com.neonvibe.dto.StatsOverviewResponse;
import com.neonvibe.dto.StatsTimelineResponse;
import com.neonvibe.dto.StatsTimelineResponse.StatsPoint;
import com.neonvibe.dto.StatsTopItem;
import com.neonvibe.repository.PlayHistoryRepository;
import com.neonvibe.repository.PlayHistoryRepository.PlayRow;
import com.neonvibe.repository.PlayHistoryRepository.StatsTopRow;
import com.neonvibe.repository.PlayHistoryRepository.StatsTotals;
import com.neonvibe.stats.StatsBucket;
import com.neonvibe.stats.StatsRange;
import com.neonvibe.stats.StatsTopType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal listening statistics, computed from {@code play_history}.
 *
 * <p>Aggregations use portable JPQL; day/week/month and hour-of-day bucketing is
 * done in Java with the caller's timezone so the queries stay H2/Postgres-safe.</p>
 */
@Service
public class StatsService {

    private static final int MAX_LIMIT = 100;

    private final PlayHistoryRepository playHistoryRepository;

    public StatsService(PlayHistoryRepository playHistoryRepository) {
        this.playHistoryRepository = playHistoryRepository;
    }

    @Transactional(readOnly = true)
    public StatsOverviewResponse overview(UUID userId, StatsRange range) {
        Instant from = range.from();
        StatsTotals totals = playHistoryRepository.totals(userId, from);
        return new StatsOverviewResponse(
                totals != null ? totals.getPlays() : 0,
                totals != null ? totals.getListenedSeconds() : 0,
                totals != null ? totals.getCompleted() : 0,
                playHistoryRepository.countDistinctTracks(userId, from),
                playHistoryRepository.countDistinctArtists(userId, from),
                playHistoryRepository.countDistinctAlbums(userId, from));
    }

    @Transactional(readOnly = true)
    public List<StatsTopItem> top(UUID userId, StatsRange range, StatsTopType type, int limit) {
        Instant from = range.from();
        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT)));
        List<StatsTopRow> rows = switch (type) {
            case TRACKS -> playHistoryRepository.topTracks(userId, from, page);
            case ALBUMS -> playHistoryRepository.topAlbums(userId, from, page);
            case ARTISTS -> playHistoryRepository.topArtists(userId, from, page);
            case GENRES -> playHistoryRepository.topGenres(userId, from, page);
        };
        return rows.stream().map(StatsService::toItem).toList();
    }

    @Transactional(readOnly = true)
    public StatsTimelineResponse timeline(UUID userId, StatsRange range, StatsBucket bucket, ZoneId zone) {
        Map<String, long[]> byPeriod = new TreeMap<>();
        for (PlayRow row : playHistoryRepository.playRows(userId, range.from())) {
            String period = period(bucket, row.getPlayedAt().atZone(zone));
            long[] acc = byPeriod.computeIfAbsent(period, k -> new long[2]);
            acc[0]++;
            acc[1] += row.getListenedSeconds() != null ? row.getListenedSeconds() : 0;
        }
        List<StatsPoint> points = byPeriod.entrySet().stream()
                .map(e -> new StatsPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new StatsTimelineResponse(bucket.name().toLowerCase(), points);
    }

    @Transactional(readOnly = true)
    public List<StatsHourItem> hours(UUID userId, StatsRange range, ZoneId zone) {
        long[] plays = new long[24];
        for (PlayRow row : playHistoryRepository.playRows(userId, range.from())) {
            plays[row.getPlayedAt().atZone(zone).getHour()]++;
        }
        List<StatsHourItem> items = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            items.add(new StatsHourItem(hour, plays[hour]));
        }
        return items;
    }

    /** Resolves an IANA timezone id, falling back to UTC on anything invalid. */
    public static ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }

    static String period(StatsBucket bucket, ZonedDateTime zoned) {
        return switch (bucket) {
            case DAY -> zoned.toLocalDate().toString();
            case WEEK -> zoned.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case MONTH -> YearMonth.from(zoned).toString();
        };
    }

    private static StatsTopItem toItem(StatsTopRow row) {
        return new StatsTopItem(row.getId(), row.getName(), row.getSubtitle(),
                row.getPlays(), row.getListenedSeconds());
    }
}
