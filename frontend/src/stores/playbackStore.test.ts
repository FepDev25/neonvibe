import { beforeEach, describe, expect, it } from 'vitest';
import {
  DEFAULT_EQ_GAINS,
  EQ_MAX_DB,
  EQ_MIN_DB,
  usePlaybackStore,
} from './playbackStore';

beforeEach(() => {
  usePlaybackStore.setState({
    prefetch: true,
    eqEnabled: false,
    eqGains: [...DEFAULT_EQ_GAINS],
  });
});

describe('playbackStore', () => {
  it('toggles prefetch', () => {
    usePlaybackStore.getState().setPrefetch(false);
    expect(usePlaybackStore.getState().prefetch).toBe(false);
    usePlaybackStore.getState().setPrefetch(true);
    expect(usePlaybackStore.getState().prefetch).toBe(true);
  });

  it('clamps EQ gains to the supported range', () => {
    usePlaybackStore.getState().setEqGain(0, 99);
    usePlaybackStore.getState().setEqGain(1, -99);
    expect(usePlaybackStore.getState().eqGains).toEqual([EQ_MAX_DB, EQ_MIN_DB, 0, 0, 0]);
  });

  it('ignores out-of-range band indexes', () => {
    const before = [...usePlaybackStore.getState().eqGains];
    usePlaybackStore.getState().setEqGain(-1, 6);
    usePlaybackStore.getState().setEqGain(99, 6);
    expect(usePlaybackStore.getState().eqGains).toEqual(before);
  });

  it('resets the equalizer to flat', () => {
    usePlaybackStore.getState().setEqGain(2, 8);
    usePlaybackStore.getState().resetEq();
    expect(usePlaybackStore.getState().eqGains).toEqual(DEFAULT_EQ_GAINS);
  });

  it('does not change gains when enabling/disabling (clean bypass)', () => {
    usePlaybackStore.getState().setEqGain(3, 5);
    usePlaybackStore.getState().setEqEnabled(true);
    usePlaybackStore.getState().setEqEnabled(false);
    expect(usePlaybackStore.getState().eqGains[3]).toBe(5);
    expect(usePlaybackStore.getState().eqEnabled).toBe(false);
  });
});
