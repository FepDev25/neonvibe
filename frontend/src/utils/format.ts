/**
 * Formatting helpers for durations, counts and Spanish dates.
 */

const LOCALE = 'es-ES';

function parseDate(iso?: string): Date | null {
  if (!iso) {
    return null;
  }
  // Date-only values are local calendar days: parsing them as UTC midnight
  // could render the previous day in western timezones.
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  const date = dateOnly
    ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    : new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

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

/** Short Spanish date, e.g. "5 nov 2026". */
export function formatDate(iso?: string): string {
  const date = parseDate(iso);
  if (!date) {
    return '';
  }
  return new Intl.DateTimeFormat(LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

/** 24-hour time, e.g. "07:52". */
export function formatTime(iso?: string): string {
  const date = parseDate(iso);
  if (!date) {
    return '';
  }
  return new Intl.DateTimeFormat(LOCALE, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

/** Consistent date + time, e.g. "5 nov 2026, 07:52". */
export function formatDateTime(iso?: string): string {
  const date = formatDate(iso);
  const time = formatTime(iso);
  return date && time ? `${date}, ${time}` : date || time;
}

/** "Hoy" / "Ayer" / short date, for grouped history sections. */
export function formatRelativeDay(iso?: string, now: Date = new Date()): string {
  const date = parseDate(iso);
  if (!date) {
    return '';
  }
  const startOf = (value: Date) =>
    new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
  const days = Math.round((startOf(now) - startOf(date)) / 86_400_000);
  if (days === 0) {
    return 'Hoy';
  }
  if (days === 1) {
    return 'Ayer';
  }
  return formatDate(iso);
}

/** Thousands separators in the Spanish locale. */
export function formatNumber(value?: number): string {
  if (value == null || Number.isNaN(value)) {
    return '';
  }
  return new Intl.NumberFormat(LOCALE).format(value);
}

/** "1 reproducción" / "N reproducciones". */
export function formatPlays(count?: number): string {
  if (count == null || Number.isNaN(count)) {
    return '';
  }
  return count === 1 ? '1 reproducción' : `${formatNumber(count)} reproducciones`;
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
  const formatted = value.toFixed(value >= 10 ? 0 : 1).replace('.', ',');
  return `${formatted} ${units[unit]}`;
}
