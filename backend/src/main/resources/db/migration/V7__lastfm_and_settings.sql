-- Last.fm integration + per-user settings (Fase 10).
ALTER TABLE users ADD COLUMN lastfm_username varchar(255);
ALTER TABLE users ADD COLUMN lastfm_session_key varchar(255);

CREATE TABLE user_settings (
    user_id              uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    theme                varchar(20) NOT NULL DEFAULT 'dark',
    notifications_enabled boolean NOT NULL DEFAULT true,
    scrobble_enabled     boolean NOT NULL DEFAULT true,
    cover_sources        varchar(500) NOT NULL DEFAULT '{"iTunes":true,"MusicBrainz":true,"LastFm":true}',
    updated_at           timestamptz NOT NULL DEFAULT now()
);
