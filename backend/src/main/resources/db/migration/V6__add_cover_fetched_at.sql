-- Track when a cover lookup last ran, so albums/artists without a cover are
-- not re-fetched online on every request (Fase 8). Null = never fetched.
ALTER TABLE albums ADD COLUMN cover_fetched_at timestamptz;
ALTER TABLE artists ADD COLUMN cover_fetched_at timestamptz;
