-- Phase v0.3: accelerate substring search with pg_trgm.
--
-- Library search is case-insensitive "contains" (LOWER(col) LIKE LOWER('%q%')),
-- which a B-tree index cannot serve because of the leading wildcard. A GIN index
-- using the pg_trgm operator class can, so these indexes are built on the exact
-- expression the queries use. pg_trgm is a trusted extension (PostgreSQL 13+),
-- so the database owner can create it without superuser rights.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_tracks_title_trgm  ON tracks  USING gin (lower(title) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_tracks_artist_trgm ON tracks  USING gin (lower(artist) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_tracks_album_trgm  ON tracks  USING gin (lower(album) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_albums_name_trgm   ON albums  USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_albums_artist_trgm ON albums  USING gin (lower(artist) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_artists_name_trgm  ON artists USING gin (lower(name) gin_trgm_ops);
