-- The reserved Singles album must not keep a cover that was auto-derived from
-- one of its tracks (one single's artwork would look like the collection's).
--
-- Per-track artwork lives in tracks.cover_art_path and is untouched; the album
-- shows its no-cover placeholder until an admin uploads a collection cover
-- explicitly (CoverArtService ignores auto-derived/online covers for Singles).
UPDATE albums
SET cover_art_path = NULL,
    cover_fetched_at = NULL
WHERE name = 'Singles'
  AND artist IS NULL;
