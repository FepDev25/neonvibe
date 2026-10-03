/**
 * Formatting helpers for durations and counts.
 */

export function formatDuration(seconds?: number): string {
  if (seconds == null || Number.isNaN(seconds) || seconds < 0) {
    return '--:--';
  }
  const total = Math.round(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${m}:${String(s).padStart(2, '0')}`;
}

export function formatTrackCount(count?: number): string {
  if (count == null) {
    return '';
  }
  return count === 1 ? '1 canción' : `${count} canciones`;
}

export function formatTotalDuration(seconds?: number): string {
  if (seconds == null) {
    return '';
  }
  return formatDuration(seconds);
}

/** Human-readable date/time for history and download entries. */
export function formatDateTime(iso?: string): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Human-readable total listening time (e.g. 8100 -> "2 h 15 min"). */
export function formatListeningTime(seconds?: number): string {
  if (seconds == null || Number.isNaN(seconds) || seconds <= 0) {
    return '0 min';
  }
  const total = Math.round(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  if (h > 0) {
    return m > 0 ? `${h} h ${m} min` : `${h} h`;
  }
  if (m > 0) {
    return `${m} min`;
  }
  return `${total} s`;
}

/** Human-readable byte size (e.g. 1536 -> "1.5 KB"). */
export function formatBytes(bytes?: number): string {
  if (bytes == null || Number.isNaN(bytes) || bytes < 0) {
    return '';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unit]}`;
}
