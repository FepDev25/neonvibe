import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Regression tests for theme persistence: a persisted light theme must be
 * applied to <html> on load, not silently reset to dark.
 */
describe('themeStore persistence', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.className = '';
    document.documentElement.removeAttribute('data-theme');
    vi.resetModules();
  });

  it('applies the persisted light theme on load', async () => {
    window.localStorage.setItem(
      'neonvibe-theme',
      JSON.stringify({ state: { theme: 'light' }, version: 0 }),
    );

    const { useThemeStore } = await import('./themeStore');

    await vi.waitFor(() => {
      expect(useThemeStore.getState().theme).toBe('light');
    });
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('defaults to dark when nothing is persisted', async () => {
    const { useThemeStore } = await import('./themeStore');

    expect(useThemeStore.getState().theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });
});
