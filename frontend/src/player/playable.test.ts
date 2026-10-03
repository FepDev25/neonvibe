import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/offline/offlineDb', () => ({ offlineDb: { get: vi.fn() } }));

import { streamUrl } from './playable';
import { useAuthStore } from '@/stores/authStore';
import { useQualityStore } from '@/stores/qualityStore';

describe('streamUrl', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: 'tok' });
    useQualityStore.setState({ quality: 'original' });
  });

  it('omits the quality parameter for original', () => {
    expect(streamUrl(5)).toBe('/api/v1/tracks/5/stream?token=tok');
  });

  it('appends the selected quality', () => {
    useQualityStore.setState({ quality: 'normal' });

    expect(streamUrl(5)).toBe('/api/v1/tracks/5/stream?token=tok&quality=normal');
  });
});
