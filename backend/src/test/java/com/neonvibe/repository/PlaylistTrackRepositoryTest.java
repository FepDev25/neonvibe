package com.neonvibe.repository;

import java.util.UUID;

import com.neonvibe.domain.Playlist;
import com.neonvibe.domain.PlaylistTrack;
import com.neonvibe.domain.Track;
import com.neonvibe.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link PlaylistTrackRepository} ordered lookup, membership and count
 * against H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class PlaylistTrackRepositoryTest {

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserRepository userRepository;

    private Playlist playlist;
    private Long firstTrackId;
    private Long secondTrackId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("pt@example.com");
        user.setName("PT User");
        UUID userId = userRepository.save(user).getId();

        playlist = new Playlist();
        playlist.setUserId(userId);
        playlist.setName("Ordered");
        playlist = playlistRepository.save(playlist);

        firstTrackId = trackRepository.save(track("/m/1.mp3", "One")).getId();
        secondTrackId = trackRepository.save(track("/m/2.mp3", "Two")).getId();

        // Insert out of order to prove the ORDER BY position works.
        playlistTrackRepository.save(playlistTrack(secondTrackId, 0));
        playlistTrackRepository.save(playlistTrack(firstTrackId, 1));
    }

    private Track track(String path, String title) {
        Track track = new Track();
        track.setFilePath(path);
        track.setTitle(title);
        track.setHasLyrics(false);
        track.setAvailable(true);
        return track;
    }

    private PlaylistTrack playlistTrack(Long trackId, int position) {
        return PlaylistTrack.builder().playlist(playlist).trackId(trackId).position(position).build();
    }

    @Test
    void findByPlaylistIdOrderByPosition_returnsOrdered() {
        assertThat(playlistTrackRepository.findByPlaylistIdOrderByPosition(playlist.getId()))
                .extracting(PlaylistTrack::getTrackId)
                .containsExactly(secondTrackId, firstTrackId);
    }

    @Test
    void existsByPlaylistIdAndTrackId() {
        assertThat(playlistTrackRepository.existsByPlaylistIdAndTrackId(playlist.getId(), firstTrackId)).isTrue();
        assertThat(playlistTrackRepository.existsByPlaylistIdAndTrackId(playlist.getId(), 999L)).isFalse();
    }

    @Test
    void countByPlaylistId() {
        assertThat(playlistTrackRepository.countByPlaylistId(playlist.getId())).isEqualTo(2);
    }
}
