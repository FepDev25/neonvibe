import { beforeEach, describe, expect, it } from 'vitest';
import { applyThemeClass, getInitialTheme } from './themeUtils';

describe('applyThemeClass', () => {
  beforeEach(() => {
    document.documentElement.className = '';
    document.documentElement.removeAttribute('data-theme');
  });

  it('adds the dark class and data attribute', () => {
    applyThemeClass('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('removes the dark class for the light theme', () => {
    applyThemeClass('dark');
    applyThemeClass('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });
});

describe('getInitialTheme', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('defaults to dark when nothing is persisted', () => {
    expect(getInitialTheme()).toBe('dark');
  });

  it('returns the persisted theme', () => {
    window.localStorage.setItem('neonvibe-theme', 'light');
    expect(getInitialTheme()).toBe('light');
  });

  it('falls back to dark for unknown values', () => {
    window.localStorage.setItem('neonvibe-theme', 'neon');
    expect(getInitialTheme()).toBe('dark');
  });
});
