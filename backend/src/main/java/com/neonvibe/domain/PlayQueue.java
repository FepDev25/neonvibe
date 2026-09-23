package com.neonvibe.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The persisted play queue for a single user (one row per user).
 *
 * <p>{@code tracksOrder} stores the ordered list of track ids as a JSON string
 * array (not CSV) because JSON is the natural representation for the client and
 * it serializes with Jackson cleanly.</p>
 */
@Entity
@Table(name = "play_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "current_track_id")
    private Long currentTrackId;

    @Column(name = "position_seconds", nullable = false)
    private Integer positionSeconds;

    @Column(name = "shuffle_enabled", nullable = false)
    private boolean shuffleEnabled;

    @Column(name = "repeat_mode", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private RepeatMode repeatMode;

    @Column(name = "tracks_order", columnDefinition = "text")
    private String tracksOrder;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_track_id", insertable = false, updatable = false)
    private Track currentTrack;
}
