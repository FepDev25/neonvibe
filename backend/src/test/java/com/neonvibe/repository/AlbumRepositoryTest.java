package com.neonvibe.repository;

import java.util.Optional;

import com.neonvibe.domain.Album;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AlbumRepository} derived queries (unique name+artist, case
 * insensitive contains and exact artist lookups) against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class AlbumRepositoryTest {

    @Autowired
    private AlbumRepository albumRepository;

    @BeforeEach
    void setUp() {
        albumRepository.save(album("OK Computer", "Radiohead", 1997));
        albumRepository.save(album("Kid A", "Radiohead", 2000));
        albumRepository.save(album("Origin of Symmetry", "Muse", 2001));
    }

    private Album album(String name, String artist, Integer year) {
        Album album = new Album();
        album.setName(name);
        album.setArtist(artist);
        album.setYear(year);
        return album;
    }

    @Test
    void findByNameAndArtist_returnsMatch() {
        Optional<Album> found = albumRepository.findByNameAndArtist("Kid A", "Radiohead");

        assertThat(found).isPresent();
        assertThat(found.get().getYear()).isEqualTo(2000);
    }

    @Test
    void findByNameAndArtist_noMatch_returnsEmpty() {
        assertThat(albumRepository.findByNameAndArtist("Kid A", "Muse")).isEmpty();
    }

    @Test
    void findByNameContainingIgnoreCase_paged() {
        Page<Album> page = albumRepository.findByNameContainingIgnoreCase("o", PageRequest.of(0, 20));

        // "OK Computer" and "Origin of Symmetry"
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByNameContainingIgnoreCase_list() {
        assertThat(albumRepository.findByNameContainingIgnoreCase("kid")).hasSize(1);
    }

    @Test
    void findByArtistContainingIgnoreCase() {
        Page<Album> page = albumRepository.findByArtistContainingIgnoreCase("radio", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByArtistIgnoreCase_isExactNotPartial() {
        assertThat(albumRepository.findByArtistIgnoreCase("radiohead")).hasSize(2);
        // Partial name must not match: this is an exact (case-insensitive) lookup.
        assertThat(albumRepository.findByArtistIgnoreCase("radio")).isEmpty();
    }

    @Test
    void findByNameAndArtistNullSafe_matchesAlbumWithoutArtist() {
        Album noArtist = album("Unknown Album", null, 2024);
        albumRepository.save(noArtist);

        // A NULL artist must match the NULL row (plain `artist = NULL` never does),
        // otherwise the scanner would recreate the album on every scan.
        Optional<Album> found = albumRepository.findByNameAndArtistNullSafe("Unknown Album", null);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(noArtist.getId());
    }

    @Test
    void findByNameAndArtistNullSafe_withArtist_doesNotMatchNullRow() {
        albumRepository.save(album("Unknown Album", null, 2024));

        assertThat(albumRepository.findByNameAndArtistNullSafe("Unknown Album", "Someone")).isEmpty();
    }

    @Test
    void searchByNameAndArtist_appliesBothFilters() {
        albumRepository.save(album("OK Computer", "Muse", 1999)); // same name, other artist

        Page<Album> page = albumRepository.searchByNameAndArtist("ok", "radio", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getArtist()).isEqualTo("Radiohead");
    }

    @Test
    void clearCoverArt_nullsPathAndFetchedAt() {
        Album album = album("Amnesiac", "Radiohead", 2001);
        album.setCoverArtPath("/covers/album/1.jpg");
        album.setCoverFetchedAt(java.time.Instant.now());
        album = albumRepository.save(album);

        albumRepository.clearCoverArt();

        Album reloaded = albumRepository.findById(album.getId()).orElseThrow();
        assertThat(reloaded.getCoverArtPath()).isNull();
        assertThat(reloaded.getCoverFetchedAt()).isNull();
    }
}
