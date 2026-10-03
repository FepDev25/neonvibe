-- Optimistic locking for the per-user play queue.
-- Concurrent player actions (multi-device sync) must not silently overwrite
-- each other: Hibernate bumps this column on every update and rejects stale
-- writes, which PlayQueueService retries against fresh state.
ALTER TABLE play_queue ADD COLUMN version bigint NOT NULL DEFAULT 0;
