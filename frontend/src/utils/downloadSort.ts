import type { OfflineTrackRecord } from '@/offline/offlineDb';

export type DownloadSortKey =
  | 'default'
  | 'title-asc'
  | 'title-desc'
  | 'artist-asc'
  | 'artist-desc'
  | 'date-asc'
  | 'date-desc'
  | 'size-asc'
  | 'size-desc';

type Field = 'title' | 'artist' | 'date' | 'size';

const collator = new Intl.Collator('es', { sensitivity: 'base', numeric: true });

/** Orders the local offline-download records (IndexedDB). */
export function sortDownloads(
  records: OfflineTrackRecord[],
  key: DownloadSortKey,
): OfflineTrackRecord[] {
  if (key === 'default' || records.length < 2) {
    return records;
  }
  const [field, direction] = key.split('-') as [Field, 'asc' | 'desc'];
  const descending = direction === 'desc';

  return [...records].sort((a, b) => {
    let cmp = 0;
    switch (field) {
      case 'title':
        cmp = collator.compare(a.title, b.title);
        break;
      case 'artist':
        cmp = collator.compare(a.artist, b.artist);
        break;
      case 'date':
        cmp = a.downloadedAt - b.downloadedAt;
        break;
      case 'size':
        cmp = a.size - b.size;
        break;
    }
    if (cmp === 0) {
      cmp = collator.compare(a.title, b.title);
    }
    if (cmp === 0) {
      cmp = a.id - b.id;
    }
    return descending ? -cmp : cmp;
  });
}
