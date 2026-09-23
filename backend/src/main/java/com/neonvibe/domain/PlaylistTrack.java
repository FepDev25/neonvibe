package com.neonvibe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join entity between a {@link Playlist} and a {@link Track} with an explicit
 * zero-based {@code position} for ordering. Managed (cascade ALL) from
 * Playlist; removal of a row is handled by the playlist owner's collection.
 */
@Entity
@Table(name = "playlist_tracks", uniqueConstraints = {
        // NOTE: (playlist_id, position) is intentionally NOT unique. The unique
        // index was dropped in V4: reorders can transiently duplicate positions
        // and ordering is enforced by PlaylistService.reindex.
        @UniqueConstraint(name = "uq_playlist_tracks_track", columnNames = {"playlist_id", "track_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "playlist_id", nullable = false, insertable = false, updatable = false)
    private Long playlistId;

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id")
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", insertable = false, updatable = false)
    private Track track;
}
