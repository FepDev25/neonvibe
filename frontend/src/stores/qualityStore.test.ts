import { beforeEach, describe, expect, it } from 'vitest';
import { useQualityStore } from './qualityStore';

describe('qualityStore', () => {
  beforeEach(() => {
    useQualityStore.setState({ quality: 'original' });
  });

  it('defaults to original', () => {
    expect(useQualityStore.getState().quality).toBe('original');
  });

  it('updates the selected quality', () => {
    useQualityStore.getState().setQuality('normal');

    expect(useQualityStore.getState().quality).toBe('normal');
  });
});
