import type { Track } from '@/types';

/**
 * Client-side ordering for fully-loaded track lists (album, playlist, artist,
 * favorites…). Kept pure so it is easy to test and reuse.
 */
export type TrackSortKey =
  | 'default'
  | 'title-asc'
  | 'title-desc'
  | 'artist-asc'
  | 'artist-desc'
  | 'album-asc'
  | 'album-desc'
  | 'duration-asc'
  | 'duration-desc'
  | 'year-asc'
  | 'year-desc';

type Field = 'title' | 'artist' | 'album' | 'duration' | 'year';

const collator = new Intl.Collator('es', { sensitivity: 'base', numeric: true });

function isMissing(value: string | number | null | undefined): boolean {
  return value == null || (typeof value === 'string' && value.trim() === '');
}

function compareOptional<T extends string | number>(
  a: T | null | undefined,
  b: T | null | undefined,
  compare: (x: T, y: T) => number,
  descending: boolean,
): number {
  const aMissing = isMissing(a);
  const bMissing = isMissing(b);
  if (aMissing || bMissing) {
    // Unknown metadata always goes last, regardless of direction.
    return aMissing && bMissing ? 0 : aMissing ? 1 : -1;
  }
  const cmp = compare(a as T, b as T);
  return descending ? -cmp : cmp;
}

function fieldValue(track: Track, field: Field): string | number | null | undefined {
  switch (field) {
    case 'title':
      return track.title;
    case 'artist':
      return track.artist;
    case 'album':
      return track.album;
    case 'duration':
      return track.duration_seconds;
    case 'year':
      return track.year;
  }
}

function compareField(a: Track, b: Track, field: Field, descending: boolean): number {
  const left = fieldValue(a, field);
  const right = fieldValue(b, field);
  if (field === 'duration' || field === 'year') {
    return compareOptional(
      left as number | null | undefined,
      right as number | null | undefined,
      (x, y) => x - y,
      descending,
    );
  }
  return compareOptional(
    left as string | null | undefined,
    right as string | null | undefined,
    (x, y) => collator.compare(x.trim(), y.trim()),
    descending,
  );
}

/** Returns a new sorted array; `default` returns the original order untouched. */
export function sortTracks(tracks: Track[], key: TrackSortKey): Track[] {
  if (key === 'default' || tracks.length < 2) {
    return tracks;
  }
  const [field, direction] = key.split('-') as [Field, 'asc' | 'desc'];
  const descending = direction === 'desc';

  return [...tracks].sort((a, b) => {
    let cmp = compareField(a, b, field, descending);
    if (cmp === 0) {
      cmp = compareOptional(a.title, b.title, (x, y) => collator.compare(x, y), false);
    }
    if (cmp === 0) {
      cmp = a.id - b.id;
    }
    return cmp;
  });
}

/**
 * Maps the UI key to a Spring Data `sort` value for the paginated library
 * endpoint. `default` means "let the backend apply its stable default".
 */
export function trackSortParam(key: TrackSortKey): string | undefined {
  if (key === 'default') {
    return undefined;
  }
  const [field, direction] = key.split('-');
  const property = (
    {
      title: 'title',
      artist: 'artist',
      album: 'album',
      duration: 'durationSeconds',
      year: 'year',
    } as Record<Field, string>
  )[field as Field];
  return property ? `${property},${direction}` : undefined;
}
