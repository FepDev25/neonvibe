package com.neonvibe.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.PlayHistory;
import com.neonvibe.domain.Track;
import com.neonvibe.domain.User;
import com.neonvibe.repository.PlayHistoryRepository.StatsTopRow;
import com.neonvibe.repository.PlayHistoryRepository.StatsTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the stats aggregation JPQL against H2 (same queries as Postgres),
 * so the top lists, distinct counts and totals are validated end to end.
 */
@DataJpaTest
@ActiveProfiles("test")
class PlayHistoryStatsQueryTest {

    @Autowired
    private PlayHistoryRepository historyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private ArtistRepository artistRepository;

    private UUID userId;
    private Long songA;
    private Long songB;
    private Long songC;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("stats@example.com");
        user.setName("Stats User");
        userId = userRepository.save(user).getId();

        Artist artistA = artist("Artist A");
        Artist artistB = artist("Artist B");
        Album album1 = album("Album One", "Artist A");
        Album album2 = album("Album Two", "Artist B");

        songA = track("/m/a.mp3", "Song A", "Artist A", "Album One", "Rock", artistA, album1);
        songB = track("/m/b.mp3", "Song B", "Artist A", "Album One", "Rock", artistA, album1);
        songC = track("/m/c.mp3", "Song C", "Artist B", "Album Two", "Pop", artistB, album2);
    }

    private Artist artist(String name) {
        Artist artist = new Artist();
        artist.setName(name);
        return artistRepository.save(artist);
    }

    private Album album(String name, String artist) {
        Album album = new Album();
        album.setName(name);
        album.setArtist(artist);
        return albumRepository.save(album);
    }

    private Long track(String path, String title, String artist, String albumName, String genre,
                       Artist artistEntity, Album albumEntity) {
        Track track = new Track();
        track.setFilePath(path);
        track.setTitle(title);
        track.setArtist(artist);
        track.setAlbum(albumName);
        track.setAlbumArtist(artist);
        track.setGenre(genre);
        track.setHasLyrics(false);
        track.setAvailable(true);
        track.setArtistEntity(artistEntity);
        track.setAlbumEntity(albumEntity);
        return trackRepository.save(track).getId();
    }

    private void play(Long trackId, Instant at, boolean completed, int listened) {
        historyRepository.save(PlayHistory.builder()
                .userId(userId).trackId(trackId).playedAt(at)
                .completed(completed).durationListenedSeconds(listened)
                .build());
    }

    private void seedPlays() {
        Instant now = Instant.now();
        play(songA, now.minusSeconds(3600), true, 150);
        play(songA, now.minusSeconds(7200), false, 100);
        play(songB, now.minusSeconds(10800), true, 200);
        play(songC, now.minusSeconds(14400), true, 50);
    }

    @Test
    void totalsAndDistinctCounts() {
        seedPlays();

        StatsTotals totals = historyRepository.totals(userId, null);

        assertThat(totals.getPlays()).isEqualTo(4);
        assertThat(totals.getListenedSeconds()).isEqualTo(500);
        assertThat(totals.getCompleted()).isEqualTo(3);
        assertThat(historyRepository.countDistinctTracks(userId, null)).isEqualTo(3);
        assertThat(historyRepository.countDistinctArtists(userId, null)).isEqualTo(2);
        assertThat(historyRepository.countDistinctAlbums(userId, null)).isEqualTo(2);
    }

    @Test
    void totalsHonourTheFromWindow() {
        seedPlays();

        // A 30-minute window excludes every seeded play (all >= 1h old).
        StatsTotals none = historyRepository.totals(userId, Instant.now().minusSeconds(1800));
        assertThat(none.getPlays()).isZero();
    }

    @Test
    void topTracksOrdersByPlayCount() {
        seedPlays();

        List<StatsTopRow> top = historyRepository.topTracks(userId, null, PageRequest.of(0, 10));

        assertThat(top).extracting(StatsTopRow::getName)
                .containsExactly("Song A", "Song B", "Song C");
        assertThat(top.get(0).getPlays()).isEqualTo(2);
    }

    @Test
    void topArtistsAlbumsAndGenres() {
        seedPlays();

        List<StatsTopRow> artists = historyRepository.topArtists(userId, null, PageRequest.of(0, 10));
        assertThat(artists).extracting(StatsTopRow::getName).containsExactly("Artist A", "Artist B");
        assertThat(artists.get(0).getPlays()).isEqualTo(3);

        List<StatsTopRow> albums = historyRepository.topAlbums(userId, null, PageRequest.of(0, 10));
        assertThat(albums).extracting(StatsTopRow::getName).containsExactly("Album One", "Album Two");

        List<StatsTopRow> genres = historyRepository.topGenres(userId, null, PageRequest.of(0, 10));
        assertThat(genres).extracting(StatsTopRow::getName).containsExactly("Rock", "Pop");
        assertThat(genres.get(0).getPlays()).isEqualTo(3);
    }
}
