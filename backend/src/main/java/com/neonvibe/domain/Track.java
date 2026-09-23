package com.neonvibe.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single audio track on disk. Metadata is extracted by the scanner and
 * materialized here (artist/album are denormalized for fast display).
 *
 * <p>References an optional {@link Album} and {@link Artist} via FK. Deleted
 * files are soft-deleted by flipping {@code isAvailable} to false.</p>
 */
@Entity
@Table(name = "tracks", indexes = {
        @Index(name = "idx_tracks_artist", columnList = "artist"),
        @Index(name = "idx_tracks_album", columnList = "album"),
        @Index(name = "idx_tracks_genre", columnList = "genre"),
        @Index(name = "idx_tracks_title", columnList = "title")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "file_path", nullable = false, unique = true, length = 1000)
    private String filePath;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "artist", length = 500)
    private String artist;

    @Column(name = "album", length = 500)
    private String album;

    @Column(name = "album_artist", length = 500)
    private String albumArtist;

    @Column(name = "year")
    private Integer year;

    @Column(name = "genre", length = 255)
    private String genre;

    @Column(name = "track_number")
    private Integer trackNumber;

    @Column(name = "disc_number")
    private Integer discNumber;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "bitrate")
    private Integer bitrate;

    @Column(name = "format", length = 20)
    private String format;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "has_lyrics", nullable = false)
    private boolean hasLyrics;

    @Column(name = "cover_art_path", length = 1000)
    private String coverArtPath;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album albumEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artistEntity;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
