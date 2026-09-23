package com.neonvibe.domain;

/**
 * Repeat mode for the play queue.
 *
 * <p>Persisted as VARCHAR (not a PostgreSQL ENUM) for H2/PostgreSQL
 * compatibility in tests (see spec section 3.9).</p>
 */
public enum RepeatMode {
    NONE,
    ALL,
    ONE
}
