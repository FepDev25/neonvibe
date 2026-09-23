package com.neonvibe.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user-owned playlist. Children {@link PlaylistTrack} rows are managed
 * with {@code cascade = ALL, orphanRemoval = true} so removing positions keeps
 * the ordering consistent.
 */
@Entity
@Table(name = "playlists", indexes = {
        @Index(name = "idx_playlists_user", columnList = "user_id"),
        @Index(name = "idx_playlists_is_public", columnList = "is_public")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "cover_art_path", length = 1000)
    private String coverArtPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlaylistTrack> tracks = new ArrayList<>();
}
