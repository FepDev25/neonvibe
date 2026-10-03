import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ useSettings: vi.fn() }));

vi.mock('./useSettings', () => ({ useSettings: mocks.useSettings }));

import { useThemeHydration } from './useThemeHydration';
import { useThemeStore } from '@/stores/themeStore';

describe('useThemeHydration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useSettings.mockReturnValue({ data: { theme: 'light' } });
    useThemeStore.setState({ theme: 'dark' });
  });

  it('applies the server theme once', async () => {
    renderHook(() => useThemeHydration());

    await waitFor(() => expect(useThemeStore.getState().theme).toBe('light'));
  });

  it('does not override a later local change', async () => {
    const { rerender } = renderHook(() => useThemeHydration());
    await waitFor(() => expect(useThemeStore.getState().theme).toBe('light'));

    // The user switches to dark; re-rendering (e.g. navigating) must not revert.
    useThemeStore.getState().setTheme('dark');
    rerender();

    expect(useThemeStore.getState().theme).toBe('dark');
  });
});
