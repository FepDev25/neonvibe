import type { RepeatMode } from '@/types';

export type AdvanceReason = 'manual' | 'ended' | 'error';

export type AdvanceResult =
  | { kind: 'play'; index: number }
  | { kind: 'restart'; index: number }
  | { kind: 'end' };

export interface NextOptions {
  length: number;
  currentIndex: number;
  repeat: RepeatMode;
  shuffle: boolean;
  reason: AdvanceReason;
  /** Injectable RNG for deterministic shuffle tests. */
  random?: () => number;
}

/**
 * Pure "what comes next" decision, shared by manual next, automatic end and
 * error-driven skipping.
 *
 * Rules (documented product decisions):
 * - `repeat: ONE` only restarts on automatic end; a manual/nexttrack or an
 *   error-driven skip always moves on (otherwise the same broken track would
 *   loop and a manual skip would look broken).
 * - Shuffle picks any other index; with a single track it either ends (NONE) or
 *   restarts (ALL).
 * - At the end of the queue with `repeat: NONE` the result is `end`.
 */
export function computeNext({
  length,
  currentIndex,
  repeat,
  shuffle,
  reason,
  random = Math.random,
}: NextOptions): AdvanceResult {
  if (length <= 0) {
    return { kind: 'end' };
  }
  const current = currentIndex >= 0 && currentIndex < length ? currentIndex : -1;

  if (reason === 'ended' && repeat === 'ONE' && current >= 0) {
    return { kind: 'restart', index: current };
  }

  if (shuffle && length > 1) {
    const candidates = length - 1;
    const offset = Math.min(candidates - 1, Math.floor(random() * candidates));
    const base = current < 0 ? 0 : current;
    // Map [0, length-2] onto "every index except current".
    return { kind: 'play', index: (base + 1 + offset) % length };
  }

  if (length === 1) {
    if (repeat === 'NONE') {
      return { kind: 'end' };
    }
    return { kind: 'restart', index: 0 };
  }

  if (current < 0) {
    return { kind: 'play', index: 0 };
  }
  if (current + 1 < length) {
    return { kind: 'play', index: current + 1 };
  }
  if (repeat === 'ALL') {
    return { kind: 'play', index: 0 };
  }
  return { kind: 'end' };
}

export interface PrevOptions {
  length: number;
  currentIndex: number;
  repeat: RepeatMode;
  /** Shuffle history (previous indices, oldest first). */
  history?: number[];
}

/**
 * Previous-track decision. In shuffle mode the history stack wins so `prev`
 * returns to the track that was actually played before, not to `index - 1`.
 * At the head with `repeat: NONE` it restarts the first track.
 */
export function computePrev({
  length,
  currentIndex,
  repeat,
  history = [],
}: PrevOptions): { index: number; restart: boolean } {
  if (length <= 0) {
    return { index: -1, restart: false };
  }
  for (let i = history.length - 1; i >= 0; i--) {
    const candidate = history[i];
    if (candidate >= 0 && candidate < length && candidate !== currentIndex) {
      return { index: candidate, restart: false };
    }
  }
  if (currentIndex > 0) {
    return { index: Math.min(currentIndex, length) - 1, restart: false };
  }
  if (repeat === 'ALL') {
    return { index: length - 1, restart: false };
  }
  return { index: 0, restart: true };
}

/**
 * Index that will play next without mutating anything, used to prefetch. Shuffle
 * returns null because the actual candidate is only chosen at skip time and
 * prefetching a random track would waste data.
 */
export function peekNextIndex(
  length: number,
  currentIndex: number,
  repeat: RepeatMode,
  shuffle: boolean,
): number | null {
  if (shuffle || length === 0 || currentIndex < 0) {
    return null;
  }
  if (repeat === 'ONE') {
    return null; // already buffered: same track
  }
  if (currentIndex + 1 < length) {
    return currentIndex + 1;
  }
  return repeat === 'ALL' ? 0 : null;
}
