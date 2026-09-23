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
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A music album. Artist is denormalized as a string (the owning {@link Artist}
 * entity is optional and managed by the scanner on a best-effort basis).
 */
@Entity
@Table(name = "albums",
        uniqueConstraints = @UniqueConstraint(name = "uq_albums_name_artist", columnNames = {"name", "artist"}),
        indexes = {
                @Index(name = "idx_albums_artist", columnList = "artist"),
                @Index(name = "idx_albums_name", columnList = "name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "artist", length = 500)
    private String artist;

    @Column(name = "year")
    private Integer year;

    @Column(name = "genre", length = 255)
    private String genre;

    @Column(name = "cover_art_path", length = 1000)
    private String coverArtPath;

    @Column(name = "cover_fetched_at")
    private java.time.Instant coverFetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @OneToMany(mappedBy = "albumEntity", fetch = FetchType.LAZY, orphanRemoval = false)
    @Builder.Default
    private List<Track> tracks = new ArrayList<>();
}
