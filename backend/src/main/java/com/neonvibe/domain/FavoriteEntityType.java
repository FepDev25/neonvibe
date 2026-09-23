package com.neonvibe.domain;

/**
 * The type of entity a favorite can point to.
 *
 * <p>{@code Favorite} is polymorphic via {@code (entityType, entityId)}.
 * Persisted as VARCHAR (see spec section 3.9).</p>
 */
public enum FavoriteEntityType {
    TRACK,
    ALBUM,
    ARTIST
}
