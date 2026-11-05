import { describe, expect, it } from 'vitest';
import {
  computeNext,
  computePrev,
  peekNextIndex,
  type NextOptions,
} from './queueNavigation';

const base: NextOptions = {
  length: 3,
  currentIndex: 1,
  repeat: 'NONE',
  shuffle: false,
  reason: 'manual',
};

describe('computeNext', () => {
  it('advances sequentially', () => {
    expect(computeNext(base)).toEqual({ kind: 'play', index: 2 });
  });

  it('stops at the end with repeat NONE', () => {
    expect(computeNext({ ...base, currentIndex: 2 })).toEqual({ kind: 'end' });
  });

  it('wraps with repeat ALL', () => {
    expect(computeNext({ ...base, currentIndex: 2, repeat: 'ALL' })).toEqual({
      kind: 'play',
      index: 0,
    });
  });

  it('restarts the same track on automatic end with repeat ONE', () => {
    expect(computeNext({ ...base, repeat: 'ONE', reason: 'ended' })).toEqual({
      kind: 'restart',
      index: 1,
    });
  });

  it('ignores repeat ONE for a manual skip (documented behaviour)', () => {
    expect(computeNext({ ...base, repeat: 'ONE', reason: 'manual' })).toEqual({
      kind: 'play',
      index: 2,
    });
  });

  it('ignores repeat ONE for error-driven skips to avoid loops', () => {
    expect(computeNext({ ...base, repeat: 'ONE', reason: 'error' })).toEqual({
      kind: 'play',
      index: 2,
    });
  });

  it('never picks the current index in shuffle when there are alternatives', () => {
    for (const random of [0, 0.24, 0.5, 0.75, 0.999]) {
      for (const currentIndex of [0, 1, 2]) {
        const result = computeNext({
          ...base,
          currentIndex,
          shuffle: true,
          random: () => random,
        });
        expect(result.kind).toBe('play');
        if (result.kind === 'play') {
          expect(result.index).not.toBe(currentIndex);
          expect(result.index).toBeGreaterThanOrEqual(0);
          expect(result.index).toBeLessThan(3);
        }
      }
    }
  });

  it('with a single track ends in NONE and restarts in ALL', () => {
    const single = { ...base, length: 1, currentIndex: 0 };
    expect(computeNext(single)).toEqual({ kind: 'end' });
    expect(computeNext({ ...single, repeat: 'ALL' })).toEqual({ kind: 'restart', index: 0 });
  });

  it('recovers from an invalid current index by starting at the first track', () => {
    expect(computeNext({ ...base, currentIndex: -1 })).toEqual({ kind: 'play', index: 0 });
  });
});

describe('computePrev', () => {
  it('moves to the previous index', () => {
    expect(computePrev({ length: 3, currentIndex: 2, repeat: 'NONE' })).toEqual({
      index: 1,
      restart: false,
    });
  });

  it('restarts the first track at the head with repeat NONE', () => {
    expect(computePrev({ length: 3, currentIndex: 0, repeat: 'NONE' })).toEqual({
      index: 0,
      restart: true,
    });
  });

  it('wraps to the last track with repeat ALL', () => {
    expect(computePrev({ length: 3, currentIndex: 0, repeat: 'ALL' })).toEqual({
      index: 2,
      restart: false,
    });
  });

  it('uses shuffle history before the array order', () => {
    expect(
      computePrev({ length: 4, currentIndex: 2, repeat: 'NONE', history: [0, 3, 1] }),
    ).toEqual({ index: 1, restart: false });
  });

  it('ignores stale history entries', () => {
    expect(
      computePrev({ length: 2, currentIndex: 1, repeat: 'NONE', history: [9, 1] }),
    ).toEqual({ index: 0, restart: false });
  });
});

describe('peekNextIndex (prefetch candidate)', () => {
  it('returns the next sequential index', () => {
    expect(peekNextIndex(3, 0, 'NONE', false)).toBe(1);
  });

  it('wraps only with repeat ALL', () => {
    expect(peekNextIndex(3, 2, 'NONE', false)).toBeNull();
    expect(peekNextIndex(3, 2, 'ALL', false)).toBe(0);
  });

  it('returns null for shuffle and repeat ONE', () => {
    expect(peekNextIndex(3, 0, 'NONE', true)).toBeNull();
    expect(peekNextIndex(3, 0, 'ONE', false)).toBeNull();
  });
});
