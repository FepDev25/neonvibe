package com.neonvibe.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * User entity.
 *
 * <p>Represents an authenticated user. The primary login is Google OAuth2, so
 * {@code googleId} stores the Google {@code sub}. The table is named {@code users}
 * because {@code user} is a reserved word in PostgreSQL.</p>
 *
 * <p>Future phases will reference {@code user_id} from PlayQueue, Playlist,
 * PlayHistory and Favorite.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    @Column(name = "lastfm_username", length = 255)
    private String lastfmUsername;

    @Column(name = "lastfm_session_key", length = 255)
    private String lastfmSessionKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
