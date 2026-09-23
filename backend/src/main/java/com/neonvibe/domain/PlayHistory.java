package com.neonvibe.domain;

import java.time.Instant;
import java.util.UUID;

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
 * A record of a played track for a user (scrobble-style history).
 */
@Entity
@Table(name = "play_history", indexes = {
        @Index(name = "idx_play_history_user_played", columnList = "user_id, played_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "track_id", nullable = false, updatable = false)
    private Long trackId;

    @Column(name = "played_at", nullable = false, updatable = false)
    private Instant playedAt;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "duration_listened_seconds")
    private Integer durationListenedSeconds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", insertable = false, updatable = false)
    private Track track;
}
