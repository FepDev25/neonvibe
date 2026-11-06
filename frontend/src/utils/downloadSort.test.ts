import { describe, expect, it } from 'vitest';
import type { OfflineTrackRecord } from '@/offline/offlineDb';
import { sortDownloads, type DownloadSortKey } from './downloadSort';

const record = (
  id: number,
  title: string,
  artist: string,
  downloadedAt: number,
  size: number,
): OfflineTrackRecord => ({
  id,
  title,
  artist,
  downloadedAt,
  size,
  blob: new Blob(),
});

const RECORDS: OfflineTrackRecord[] = [
  record(1, 'Zeta', 'Beta', 300, 30),
  record(2, 'alpha', 'Álpha', 100, 10),
  record(3, 'Beta', 'Gamma', 200, 20),
];

const ids = (key: DownloadSortKey) => sortDownloads(RECORDS, key).map((r) => r.id);

describe('sortDownloads', () => {
  it('keeps the stored order for default', () => {
    expect(ids('default')).toEqual([1, 2, 3]);
  });

  it('sorts by title', () => {
    expect(ids('title-asc')).toEqual([2, 3, 1]);
    expect(ids('title-desc')).toEqual([1, 3, 2]);
  });

  it('sorts by artist', () => {
    expect(ids('artist-asc')).toEqual([2, 1, 3]);
    expect(ids('artist-desc')).toEqual([3, 1, 2]);
  });

  it('sorts by download date', () => {
    expect(ids('date-asc')).toEqual([2, 3, 1]);
    expect(ids('date-desc')).toEqual([1, 3, 2]);
  });

  it('sorts by size', () => {
    expect(ids('size-asc')).toEqual([2, 3, 1]);
    expect(ids('size-desc')).toEqual([1, 3, 2]);
  });
});
