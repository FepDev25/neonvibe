package com.neonvibe.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A music artist. Denormalized reference from {@link Track}; the collections are
 * optional and mainly informational (the scanner queries by name instead of walking
 * these lazy collections to avoid loading large graphs).
 */
@Entity
@Table(name = "artists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 500)
    private String name;

    @Column(name = "cover_art_path", length = 1000)
    private String coverArtPath;

    @Column(name = "cover_fetched_at")
    private java.time.Instant coverFetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @OneToMany(mappedBy = "artistEntity", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Track> tracks = new ArrayList<>();
}
