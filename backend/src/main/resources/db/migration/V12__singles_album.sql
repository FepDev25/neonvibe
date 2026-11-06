-- v0.4: group loose tracks under the reserved "Singles" album.
--
-- A track is "loose" when it has no album tag. Every track belongs to an album,
-- so loose tracks are grouped in a single global album called "Singles" that
-- has no artist: each track keeps its own artist.
--
-- The (name, artist) unique index treats NULL artists as distinct, so a partial
-- unique index is added to guarantee a single global Singles row.

-- ---------------------------------------------------------------------------
-- 1) Reuse or create the global Singles album.
-- ---------------------------------------------------------------------------
INSERT INTO albums (name, artist, created_at)
SELECT 'Singles', NULL, now()
WHERE NOT EXISTS (
    SELECT 1 FROM albums WHERE name = 'Singles' AND artist IS NULL
);

-- ---------------------------------------------------------------------------
-- 2) Consolidate accidental duplicates before creating the unique guard.
-- ---------------------------------------------------------------------------
WITH canonical AS (
    SELECT min(id) AS id FROM albums WHERE name = 'Singles' AND artist IS NULL
)
UPDATE tracks
SET album_id = (SELECT id FROM canonical),
    updated_at = now()
WHERE album_id IN (SELECT id FROM albums WHERE name = 'Singles' AND artist IS NULL)
  AND album_id <> (SELECT id FROM canonical);

DELETE FROM albums
WHERE name = 'Singles'
  AND artist IS NULL
  AND id <> (SELECT min(id) FROM albums WHERE name = 'Singles' AND artist IS NULL);

CREATE UNIQUE INDEX IF NOT EXISTS uq_albums_singles
    ON albums (name)
    WHERE name = 'Singles' AND artist IS NULL;

-- ---------------------------------------------------------------------------
-- 3) Loose tracks (no album tag) -> Singles.
-- ---------------------------------------------------------------------------
UPDATE tracks
SET album = 'Singles',
    album_id = (SELECT id FROM albums WHERE name = 'Singles' AND artist IS NULL ORDER BY id LIMIT 1),
    updated_at = now()
WHERE album_id IS NULL
  AND (album IS NULL OR btrim(album) = '');

-- ---------------------------------------------------------------------------
-- 4) Legacy "Single"/"Singles" tags -> canonical Singles album.
-- ---------------------------------------------------------------------------
UPDATE tracks
SET album = 'Singles',
    album_id = (SELECT id FROM albums WHERE name = 'Singles' AND artist IS NULL ORDER BY id LIMIT 1),
    updated_at = now()
WHERE lower(btrim(album)) IN ('single', 'singles')
  AND album_id IS DISTINCT FROM
      (SELECT id FROM albums WHERE name = 'Singles' AND artist IS NULL ORDER BY id LIMIT 1);

-- Remove the now-empty "Single"/"Singles" albums (never the canonical one).
DELETE FROM albums a
WHERE lower(btrim(a.name)) IN ('single', 'singles')
  AND a.id <> (SELECT id FROM albums
               WHERE name = 'Singles' AND artist IS NULL
               ORDER BY id LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM tracks t WHERE t.album_id = a.id);

-- ---------------------------------------------------------------------------
-- 5) Legacy rows with an album text but no album entity: rebuild the entity.
--    (Not expected on current data, but keeps the FK and the text consistent.)
-- ---------------------------------------------------------------------------
INSERT INTO albums (name, artist, year, genre, created_at)
SELECT t.album, t.artist, min(t.year), min(t.genre), now()
FROM tracks t
WHERE t.album_id IS NULL
  AND t.album IS NOT NULL
  AND btrim(t.album) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM albums a
      WHERE a.name = t.album
        AND a.artist IS NOT DISTINCT FROM t.artist
  )
GROUP BY t.album, t.artist;

UPDATE tracks t
SET album_id = a.id,
    updated_at = now()
FROM albums a
WHERE t.album_id IS NULL
  AND t.album IS NOT NULL
  AND btrim(t.album) <> ''
  AND a.name = t.album
  AND a.artist IS NOT DISTINCT FROM t.artist;
