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
