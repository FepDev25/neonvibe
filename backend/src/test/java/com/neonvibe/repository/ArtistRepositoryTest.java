package com.neonvibe.repository;

import java.util.Optional;

import com.neonvibe.domain.Artist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ArtistRepository} lookups against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class ArtistRepositoryTest {

    @Autowired
    private ArtistRepository artistRepository;

    @BeforeEach
    void setUp() {
        artistRepository.save(artist("Radiohead"));
        artistRepository.save(artist("Muse"));
    }

    private Artist artist(String name) {
        Artist artist = new Artist();
        artist.setName(name);
        return artist;
    }

    @Test
    void findByName_returnsMatch() {
        Optional<Artist> found = artistRepository.findByName("Radiohead");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Radiohead");
    }

    @Test
    void findByName_unknown_returnsEmpty() {
        assertThat(artistRepository.findByName("Nobody")).isEmpty();
    }

    @Test
    void findByNameContainingIgnoreCase_isCaseInsensitive() {
        Page<Artist> page = artistRepository.findByNameContainingIgnoreCase("radio", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Radiohead");
    }
}
