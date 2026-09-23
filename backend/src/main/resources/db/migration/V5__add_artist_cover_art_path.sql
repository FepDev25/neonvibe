-- Add cover art cache path to artists (Fase 8).
ALTER TABLE artists ADD COLUMN cover_art_path varchar(1000);
