package com.neonvibe.repository;

import java.util.List;
import java.util.Optional;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies TrackRepository persistence, unique file path and derived lookups
 * against the embedded H2 DB (PostgreSQL mode).
 */
@DataJpaTest
@ActiveProfiles("test")
class TrackRepositoryTest {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @BeforeEach
    void setUp() {
        trackRepository.save(track("/music/a.mp3", "Alpha", "Artist A", "Album One", "Rock", 2020));
        trackRepository.save(track("/music/b.mp3", "Beta", "Artist B", "Album One", "Pop", 2021));
        trackRepository.save(track("/music/c.flac", "Gamma", "Artist A", "Album Two", "Rock", 2022));
    }

    private Track track(String path, String title, String artist, String album,
                        String genre, Integer year) {
        Track t = new Track();
        t.setFilePath(path);
        t.setTitle(title);
        t.setArtist(artist);
        t.setAlbum(album);
        t.setGenre(genre);
        t.setYear(year);
        t.setHasLyrics(false);
        t.setAvailable(true);
        return t;
    }

    @Test
    void findByFilePath_returnsTrack() {
        Optional<Track> found = trackRepository.findByFilePath("/music/a.mp3");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Alpha");
    }

    @Test
    void search_byArtist_returnsOnlyMatches() {
        Page<Track> page = trackRepository.search(null, "Artist B", null, null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Beta");
    }

    @Test
    void search_byGenreAndYear_returnsMatches() {
        Page<Track> page = trackRepository.search(null, null, null, "Rock", 2020, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Alpha");
    }

    @Test
    void search_byQ_titleIgnoreCase() {
        Page<Track> page = trackRepository.search("gam", null, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Gamma");
    }

    @Test
    void findByFilePathIsUniqueLookup() {
        // Unique on file_path: a lookup always returns the single canonical row.
        assertThat(trackRepository.findByFilePath("/music/a.mp3")).isPresent();
        assertThat(trackRepository.findAll()).hasSize(3);
    }

    @Test
    void countAvailableByAlbumId_countsOnlyAvailable() {
        Album album = new Album();
        album.setName("Counted");
        album.setArtist("A");
        album = albumRepository.save(album);

        Track available = track("/music/x.mp3", "X", "A", "Counted", "Rock", 2020);
        available.setAlbumEntity(album);
        Track unavailable = track("/music/y.mp3", "Y", "A", "Counted", "Rock", 2020);
        unavailable.setAlbumEntity(album);
        unavailable.setAvailable(false);
        trackRepository.save(available);
        trackRepository.save(unavailable);

        assertThat(trackRepository.countAvailableByAlbumId(album.getId())).isEqualTo(1);

        List<TrackRepository.AlbumTrackCount> counts =
                trackRepository.countAvailableByAlbumIds(List.of(album.getId()));
        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getAlbumId()).isEqualTo(album.getId());
        assertThat(counts.get(0).getTrackCount()).isEqualTo(1);
    }

    @Test
    void findSimilarByArtist_excludesSeedAndMatchesArtist() {
        Long seedId = trackRepository.findByFilePath("/music/a.mp3").orElseThrow().getId();

        List<Track> result = trackRepository.findSimilarByArtist("Artist A", seedId, PageRequest.of(0, 10));

        assertThat(result).extracting(Track::getFilePath).containsExactly("/music/c.flac");
    }

    @Test
    void findSimilarByGenre_excludesSeedAndMatchesGenre() {
        Long seedId = trackRepository.findByFilePath("/music/a.mp3").orElseThrow().getId();

        List<Track> result = trackRepository.findSimilarByGenre("Rock", seedId, PageRequest.of(0, 10));

        assertThat(result).extracting(Track::getFilePath).containsExactly("/music/c.flac");
    }

    @Test
    void clearCoverArt_nullsPaths() {
        Track t = trackRepository.findByFilePath("/music/a.mp3").orElseThrow();
        t.setCoverArtPath("/covers/embedded/1.jpg");
        trackRepository.save(t);

        trackRepository.clearCoverArt();

        assertThat(trackRepository.findById(t.getId()).orElseThrow().getCoverArtPath()).isNull();
    }
}
