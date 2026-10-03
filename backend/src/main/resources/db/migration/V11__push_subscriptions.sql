-- Phase v0.3: Web Push subscriptions for native notifications.
-- One row per browser/device endpoint. Endpoints are capped at 1000 chars
-- (below the B-tree index limit) since that comfortably fits FCM/Mozilla URLs.

CREATE TABLE push_subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     uuid NOT NULL,
    endpoint    varchar(1000) NOT NULL,
    p256dh      varchar(255) NOT NULL,
    auth        varchar(255) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_push_subscriptions_endpoint ON push_subscriptions (endpoint);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
