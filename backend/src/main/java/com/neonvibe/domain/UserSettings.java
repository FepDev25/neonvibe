package com.neonvibe.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user preferences persisted in the database (Fase 10): theme, notifications,
 * scrobbling toggle and enabled cover sources.
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "theme", nullable = false, length = 20)
    @Builder.Default
    private String theme = "dark";

    @Column(name = "notifications_enabled", nullable = false)
    @Builder.Default
    private boolean notificationsEnabled = true;

    @Column(name = "scrobble_enabled", nullable = false)
    @Builder.Default
    private boolean scrobbleEnabled = true;

    /** JSON: {"iTunes":true,"MusicBrainz":true,"LastFm":true} */
    @Column(name = "cover_sources", nullable = false, length = 500)
    @Builder.Default
    private String coverSources = "{\"iTunes\":true,\"MusicBrainz\":true,\"LastFm\":true}";

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
