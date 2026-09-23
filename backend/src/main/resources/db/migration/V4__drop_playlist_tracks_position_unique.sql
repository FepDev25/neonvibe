-- Drop the unique (playlist_id, position) index.
--
-- The PlaylistService reindexes positions 0..n-1 on every reorder/removal, so a
-- transient duplicate position during a reorder batch could violate this unique
-- index. Ordering is enforced in the application layer instead.
DROP INDEX IF EXISTS uq_playlist_tracks_position;
