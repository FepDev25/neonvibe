import { describe, expect, it } from 'vitest';
import type { Track } from '@/types';
import { sortTracks, trackSortParam, type TrackSortKey } from './trackSort';

const track = (
  id: number,
  title: string,
  artist?: string,
  album?: string,
  duration?: number,
  year?: number,
): Track => ({
  id,
  title,
  artist,
  album,
  duration_seconds: duration,
  year,
});

const TRACKS: Track[] = [
  track(1, 'Zeta', 'Beta', 'Album B', 200, 2020),
  track(2, 'alpha', 'Álpha', 'Album A', 100, 2018),
  track(3, 'Beta', undefined, undefined, undefined, undefined),
];

const ids = (key: TrackSortKey) => sortTracks(TRACKS, key).map((t) => t.id);
const titles = (key: TrackSortKey) => sortTracks(TRACKS, key).map((t) => t.title);

describe('sortTracks', () => {
  it('keeps the original order for default and does not mutate it', () => {
    const input = [...TRACKS];
    const result = sortTracks(input, 'default');

    expect(result).toBe(input);
    expect(input.map((t) => t.id)).toEqual([1, 2, 3]);
  });

  it('sorts titles case/accent-insensitively', () => {
    expect(titles('title-asc')).toEqual(['alpha', 'Beta', 'Zeta']);
    expect(titles('title-desc')).toEqual(['Zeta', 'Beta', 'alpha']);
  });

  it('sorts by artist and puts missing values last', () => {
    expect(ids('artist-asc')).toEqual([2, 1, 3]);
    expect(ids('artist-desc')).toEqual([1, 2, 3]);
  });

  it('sorts by album with missing values last', () => {
    expect(ids('album-asc')).toEqual([2, 1, 3]);
    expect(ids('album-desc')).toEqual([1, 2, 3]);
  });

  it('sorts durations numerically and keeps missing values last in both directions', () => {
    expect(ids('duration-asc')).toEqual([2, 1, 3]);
    expect(ids('duration-desc')).toEqual([1, 2, 3]);
  });

  it('sorts years and keeps missing values last', () => {
    expect(ids('year-asc')).toEqual([2, 1, 3]);
    expect(ids('year-desc')).toEqual([1, 2, 3]);
  });

  it('uses the title as a stable tie-breaker', () => {
    const sameArtist = [
      track(10, 'B', 'Same'),
      track(11, 'A', 'Same'),
      track(12, 'C', 'Same'),
    ];
    expect(sortTracks(sameArtist, 'artist-asc').map((t) => t.title)).toEqual([
      'A',
      'B',
      'C',
    ]);
  });
});

describe('trackSortParam', () => {
  it('maps UI keys to Spring sort values', () => {
    expect(trackSortParam('default')).toBeUndefined();
    expect(trackSortParam('title-asc')).toBe('title,asc');
    expect(trackSortParam('artist-desc')).toBe('artist,desc');
    expect(trackSortParam('album-asc')).toBe('album,asc');
    expect(trackSortParam('duration-desc')).toBe('durationSeconds,desc');
    expect(trackSortParam('year-asc')).toBe('year,asc');
  });
});
