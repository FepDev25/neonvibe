package com.neonvibe.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.neonvibe.dto.StatsHourItem;
import com.neonvibe.dto.StatsOverviewResponse;
import com.neonvibe.dto.StatsTimelineResponse;
import com.neonvibe.dto.StatsTopItem;
import com.neonvibe.repository.PlayHistoryRepository;
import com.neonvibe.repository.PlayHistoryRepository.PlayRow;
import com.neonvibe.repository.PlayHistoryRepository.StatsTopRow;
import com.neonvibe.repository.PlayHistoryRepository.StatsTotals;
import com.neonvibe.stats.StatsBucket;
import com.neonvibe.stats.StatsRange;
import com.neonvibe.stats.StatsTopType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StatsService}: aggregation mapping and timeline/hour
 * bucketing by timezone.
 */
class StatsServiceTest {

    private final PlayHistoryRepository repository = mock(PlayHistoryRepository.class);
    private final StatsService service = new StatsService(repository);
    private final UUID userId = UUID.randomUUID();

    private static StatsTotals totals(long plays, long listened, long completed) {
        return new StatsTotals() {
            public long getPlays() {
                return plays;
            }

            public long getListenedSeconds() {
                return listened;
            }

            public long getCompleted() {
                return completed;
            }
        };
    }

    private static PlayRow row(String iso, Integer listened) {
        Instant at = ZonedDateTime.parse(iso).toInstant();
        return new PlayRow() {
            public Instant getPlayedAt() {
                return at;
            }

            public Integer getListenedSeconds() {
                return listened;
            }
        };
    }

    private static StatsTopRow topRow(Long id, String name, String subtitle, long plays, long listened) {
        return new StatsTopRow() {
            public Long getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getSubtitle() {
                return subtitle;
            }

            public long getPlays() {
                return plays;
            }

            public long getListenedSeconds() {
                return listened;
            }
        };
    }

    @Test
    void overview_mapsTotalsAndDistinctCounts() {
        when(repository.totals(eq(userId), any())).thenReturn(totals(42, 7200, 30));
        when(repository.countDistinctTracks(eq(userId), any())).thenReturn(10L);
        when(repository.countDistinctArtists(eq(userId), any())).thenReturn(5L);
        when(repository.countDistinctAlbums(eq(userId), any())).thenReturn(7L);

        StatsOverviewResponse overview = service.overview(userId, StatsRange.MONTH);

        assertThat(overview.totalPlays()).isEqualTo(42);
        assertThat(overview.listenedSeconds()).isEqualTo(7200);
        assertThat(overview.completedPlays()).isEqualTo(30);
        assertThat(overview.distinctTracks()).isEqualTo(10);
        assertThat(overview.distinctArtists()).isEqualTo(5);
        assertThat(overview.distinctAlbums()).isEqualTo(7);
    }

    @Test
    void overview_handlesMissingTotals() {
        when(repository.totals(eq(userId), any())).thenReturn(null);

        StatsOverviewResponse overview = service.overview(userId, StatsRange.ALL);

        assertThat(overview.totalPlays()).isZero();
        assertThat(overview.listenedSeconds()).isZero();
    }

    @Test
    void top_mapsRowsAndClampsLimit() {
        when(repository.topArtists(eq(userId), any(), any(Pageable.class)))
                .thenReturn(List.of(topRow(1L, "Radiohead", "", 20, 4000)));

        List<StatsTopItem> items = service.top(userId, StatsRange.MONTH, StatsTopType.ARTISTS, 500);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Radiohead");
            assertThat(item.plays()).isEqualTo(20);
            assertThat(item.listenedSeconds()).isEqualTo(4000);
        });
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).topArtists(eq(userId), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void timeline_dayBucketsInTimezone() {
        // 2026-10-01T23:30Z is 2026-10-02 in Europe/Madrid (UTC+2).
        when(repository.playRows(eq(userId), any())).thenReturn(List.of(
                row("2026-10-01T23:30:00Z", 100),
                row("2026-10-02T00:10:00Z", 200),
                row("2026-10-03T10:00:00Z", 50)));

        StatsTimelineResponse timeline =
                service.timeline(userId, StatsRange.MONTH, StatsBucket.DAY, ZoneId.of("Europe/Madrid"));

        assertThat(timeline.bucket()).isEqualTo("day");
        assertThat(timeline.points()).extracting(StatsTimelineResponse.StatsPoint::period)
                .containsExactly("2026-10-02", "2026-10-03");
        StatsTimelineResponse.StatsPoint oct2 = timeline.points().get(0);
        assertThat(oct2.plays()).isEqualTo(2);
        assertThat(oct2.listenedSeconds()).isEqualTo(300);
    }

    @Test
    void timeline_monthBucketUsesYearMonth() {
        when(repository.playRows(eq(userId), any())).thenReturn(List.of(
                row("2026-09-15T10:00:00Z", 10),
                row("2026-10-01T10:00:00Z", 20)));

        StatsTimelineResponse timeline =
                service.timeline(userId, StatsRange.ALL, StatsBucket.MONTH, ZoneId.of("UTC"));

        assertThat(timeline.points()).extracting(StatsTimelineResponse.StatsPoint::period)
                .containsExactly("2026-09", "2026-10");
    }

    @Test
    void hours_zeroFillsAndUsesTimezone() {
        when(repository.playRows(eq(userId), any())).thenReturn(List.of(
                row("2026-10-01T08:00:00Z", 10),
                row("2026-10-01T08:30:00Z", 10),
                row("2026-10-01T21:00:00Z", 10)));

        List<StatsHourItem> hours = service.hours(userId, StatsRange.MONTH, ZoneId.of("UTC"));

        assertThat(hours).hasSize(24);
        assertThat(hours.get(8).plays()).isEqualTo(2);
        assertThat(hours.get(21).plays()).isEqualTo(1);
        assertThat(hours.get(0).plays()).isZero();
    }

    @Test
    void zone_fallsBackToUtc() {
        assertThat(StatsService.zone("Europe/Madrid")).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(StatsService.zone("not/a-zone")).isEqualTo(ZoneId.of("UTC"));
        assertThat(StatsService.zone(null)).isEqualTo(ZoneId.of("UTC"));
    }
}
