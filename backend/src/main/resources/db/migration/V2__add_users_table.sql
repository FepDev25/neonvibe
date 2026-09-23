-- Phase 1: users table.
-- Note: table name is "users" because "user" is a reserved word in PostgreSQL.

CREATE TABLE users (
    id          uuid PRIMARY KEY,
    email       varchar(320) NOT NULL,
    name        varchar(200) NOT NULL,
    avatar_url  varchar(1000),
    google_id   varchar(100),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_email ON users (email);
CREATE UNIQUE INDEX uq_users_google_id ON users (google_id);
