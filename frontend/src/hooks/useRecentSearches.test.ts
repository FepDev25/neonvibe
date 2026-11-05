import { renderHook, act } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { useRecentSearches } from './useRecentSearches';

const KEY = 'neonvibe-recent-searches';

describe('useRecentSearches', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('remembers, deduplicates and persists terms', () => {
    const { result } = renderHook(() => useRecentSearches());

    act(() => result.current.remember('Ghost'));
    act(() => result.current.remember('Pink Floyd'));
    act(() => result.current.remember('ghost'));

    expect(result.current.recent).toEqual(['ghost', 'Pink Floyd']);
    expect(JSON.parse(localStorage.getItem(KEY) ?? '[]')).toEqual([
      'ghost',
      'Pink Floyd',
    ]);
  });

  it('caps the list at five terms', () => {
    const { result } = renderHook(() => useRecentSearches());

    for (const term of ['a1', 'a2', 'a3', 'a4', 'a5', 'a6']) {
      act(() => result.current.remember(term));
    }

    expect(result.current.recent).toHaveLength(5);
    expect(result.current.recent[0]).toBe('a6');
  });

  it('removes and clears terms', () => {
    const { result } = renderHook(() => useRecentSearches());

    act(() => result.current.remember('Ghost'));
    act(() => result.current.remember('Muse'));
    act(() => result.current.remove('Ghost'));
    expect(result.current.recent).toEqual(['Muse']);

    act(() => result.current.clear());
    expect(result.current.recent).toEqual([]);
    expect(JSON.parse(localStorage.getItem(KEY) ?? 'null')).toEqual([]);
  });

  it('hydrates from localStorage and ignores malformed values', () => {
    localStorage.setItem(KEY, JSON.stringify(['Ghost', 'Muse']));
    const first = renderHook(() => useRecentSearches());
    expect(first.result.current.recent).toEqual(['Ghost', 'Muse']);

    localStorage.setItem(KEY, '{oops');
    const second = renderHook(() => useRecentSearches());
    expect(second.result.current.recent).toEqual([]);
  });
});
