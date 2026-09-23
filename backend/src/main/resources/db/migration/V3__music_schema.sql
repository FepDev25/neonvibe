-- Phase 2: music domain schema (tracks, albums, artists, playlists, queue, history, favorites).
-- PostgreSQL-compatible. Enums are stored as VARCHAR (see spec section 3.9).

-- ---------------------------------------------------------------------------
-- artists
-- ---------------------------------------------------------------------------
CREATE TABLE artists (
    id          BIGSERIAL PRIMARY KEY,
    name        varchar(500) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_artists_name ON artists (name);

-- ---------------------------------------------------------------------------
-- albums
-- ---------------------------------------------------------------------------
CREATE TABLE albums (
    id              BIGSERIAL PRIMARY KEY,
    name            varchar(500) NOT NULL,
    artist          varchar(500),
    year            integer,
    genre           varchar(255),
    cover_art_path  varchar(1000),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_albums_name_artist ON albums (name, artist);
CREATE INDEX idx_albums_artist ON albums (artist);
CREATE INDEX idx_albums_name ON albums (name);

-- ---------------------------------------------------------------------------
-- tracks
-- ---------------------------------------------------------------------------
CREATE TABLE tracks (
    id              BIGSERIAL PRIMARY KEY,
    file_path       varchar(1000) NOT NULL,
    title           varchar(500) NOT NULL,
    artist          varchar(500),
    album           varchar(500),
    album_artist    varchar(500),
    year            integer,
    genre           varchar(255),
    track_number    integer,
    disc_number     integer,
    duration_seconds integer,
    bitrate         integer,
    format          varchar(20),
    mime_type       varchar(100),
    has_lyrics      boolean NOT NULL DEFAULT false,
    cover_art_path  varchar(1000),
    is_available    boolean NOT NULL DEFAULT true,
    album_id        bigint,
    artist_id       bigint,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_tracks_album FOREIGN KEY (album_id) REFERENCES albums (id),
    CONSTRAINT fk_tracks_artist FOREIGN KEY (artist_id) REFERENCES artists (id)
);

CREATE UNIQUE INDEX uq_tracks_file_path ON tracks (file_path);
CREATE INDEX idx_tracks_artist ON tracks (artist);
CREATE INDEX idx_tracks_album ON tracks (album);
CREATE INDEX idx_tracks_genre ON tracks (genre);
CREATE INDEX idx_tracks_title ON tracks (title);
CREATE INDEX idx_tracks_year ON tracks (year);
CREATE INDEX idx_tracks_album_id ON tracks (album_id);
CREATE INDEX idx_tracks_artist_id ON tracks (artist_id);

-- ---------------------------------------------------------------------------
-- playlists
-- ---------------------------------------------------------------------------
CREATE TABLE playlists (
    id              BIGSERIAL PRIMARY KEY,
    user_id         uuid NOT NULL,
    name            varchar(200) NOT NULL,
    description     varchar(2000),
    is_public       boolean NOT NULL DEFAULT false,
    cover_art_path  varchar(1000),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_playlists_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_playlists_user ON playlists (user_id);
CREATE INDEX idx_playlists_is_public ON playlists (is_public);

-- ---------------------------------------------------------------------------
-- playlist_tracks (join with ordering)
-- ---------------------------------------------------------------------------
CREATE TABLE playlist_tracks (
    id          BIGSERIAL PRIMARY KEY,
    playlist_id bigint NOT NULL,
    track_id    bigint NOT NULL,
    position    integer NOT NULL,
    CONSTRAINT fk_playlist_tracks_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_tracks_track FOREIGN KEY (track_id) REFERENCES tracks (id)
);

CREATE UNIQUE INDEX uq_playlist_tracks_position ON playlist_tracks (playlist_id, position);
CREATE UNIQUE INDEX uq_playlist_tracks_track ON playlist_tracks (playlist_id, track_id);
CREATE INDEX idx_playlist_tracks_playlist ON playlist_tracks (playlist_id);

-- ---------------------------------------------------------------------------
-- play_queue (one per user)
-- ---------------------------------------------------------------------------
CREATE TABLE play_queue (
    id              BIGSERIAL PRIMARY KEY,
    user_id         uuid NOT NULL,
    current_track_id bigint,
    position_seconds integer NOT NULL DEFAULT 0,
    shuffle_enabled boolean NOT NULL DEFAULT false,
    repeat_mode     varchar(10) NOT NULL DEFAULT 'NONE',
    tracks_order    text,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_play_queue_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_play_queue_current_track FOREIGN KEY (current_track_id) REFERENCES tracks (id)
);

CREATE UNIQUE INDEX uq_play_queue_user ON play_queue (user_id);

-- ---------------------------------------------------------------------------
-- play_history
-- ---------------------------------------------------------------------------
CREATE TABLE play_history (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   uuid NOT NULL,
    track_id                  bigint NOT NULL,
    played_at                 timestamptz NOT NULL,
    completed                 boolean NOT NULL DEFAULT false,
    duration_listened_seconds integer,
    CONSTRAINT fk_play_history_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_play_history_track FOREIGN KEY (track_id) REFERENCES tracks (id)
);

CREATE INDEX idx_play_history_user_played ON play_history (user_id, played_at DESC);

-- ---------------------------------------------------------------------------
-- favorites
-- ---------------------------------------------------------------------------
CREATE TABLE favorites (
    id           BIGSERIAL PRIMARY KEY,
    user_id      uuid NOT NULL,
    entity_type  varchar(10) NOT NULL,
    entity_id    bigint NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_favorites_user_entity ON favorites (user_id, entity_type, entity_id);
CREATE INDEX idx_favorites_user ON favorites (user_id);
