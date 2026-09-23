package com.neonvibe.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user favorite pointing to a TRACK, ALBUM or ARTIST (polymorphic via
 * {@code (entityType, entityId)}).
 */
@Entity
@Table(name = "favorites",
        uniqueConstraints = @UniqueConstraint(name = "uq_favorites_user_entity",
                columnNames = {"user_id", "entity_type", "entity_id"}),
        indexes = @Index(name = "idx_favorites_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "entity_type", nullable = false, length = 10, updatable = false)
    @Enumerated(EnumType.STRING)
    private FavoriteEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
}
