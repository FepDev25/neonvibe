import { describe, expect, it } from 'vitest';
import {
  formatBytes,
  formatDate,
  formatDateTime,
  formatDuration,
  formatListeningTime,
  formatNumber,
  formatPlays,
  formatRelativeDay,
  formatTime,
  formatTotalDuration,
  formatTrackCount,
} from './format';

describe('formatDuration', () => {
  it('formats sub-hour durations as m:ss', () => {
    expect(formatDuration(65)).toBe('1:05');
  });

  it('formats hour-long durations as h:mm:ss', () => {
    expect(formatDuration(3661)).toBe('1:01:01');
  });

  it('rounds fractional seconds', () => {
    expect(formatDuration(59.6)).toBe('1:00');
  });

  it('returns a placeholder for missing or invalid values', () => {
    expect(formatDuration()).toBe('--:--');
    expect(formatDuration(Number.NaN)).toBe('--:--');
    expect(formatDuration(-1)).toBe('--:--');
  });
});

describe('formatTrackCount', () => {
  it('uses the singular for one track', () => {
    expect(formatTrackCount(1)).toBe('1 canción');
  });

  it('uses the plural otherwise', () => {
    expect(formatTrackCount(0)).toBe('0 canciones');
    expect(formatTrackCount(7)).toBe('7 canciones');
  });

  it('returns an empty string when the count is missing', () => {
    expect(formatTrackCount()).toBe('');
  });
});

describe('formatTotalDuration', () => {
  it('delegates to formatDuration', () => {
    expect(formatTotalDuration(125)).toBe('2:05');
  });

  it('returns an empty string when the duration is missing', () => {
    expect(formatTotalDuration()).toBe('');
  });
});

describe('formatBytes', () => {
  it('formats bytes, KB, MB and GB', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1,5 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5,0 MB');
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe('3,0 GB');
  });

  it('returns an empty string for missing/invalid values', () => {
    expect(formatBytes()).toBe('');
    expect(formatBytes(-1)).toBe('');
  });
});

describe('formatDateTime', () => {
  it('returns an empty string for missing/invalid input', () => {
    expect(formatDateTime()).toBe('');
    expect(formatDateTime('not-a-date')).toBe('');
  });

  it('formats a valid ISO timestamp', () => {
    expect(formatDateTime('2026-01-02T03:04:00Z')).not.toBe('');
  });
});

describe('formatListeningTime', () => {
  it('formats hours and minutes', () => {
    expect(formatListeningTime(8100)).toBe('2 h 15 min');
    expect(formatListeningTime(7200)).toBe('2 h');
  });

  it('formats minutes and seconds', () => {
    expect(formatListeningTime(125)).toBe('2 min');
    expect(formatListeningTime(45)).toBe('45 s');
  });

  it('handles zero and invalid values', () => {
    expect(formatListeningTime(0)).toBe('0 min');
    expect(formatListeningTime()).toBe('0 min');
  });
});


describe('Spanish dates and counts', () => {
  it('formats dates in Spanish with a short month', () => {
    const value = formatDate('2026-11-05');
    expect(value).toMatch(/5/);
    expect(value).toMatch(/nov/i);
    expect(value).toMatch(/2026/);
    expect(value).not.toMatch(/Nov/);
  });

  it('formats 24-hour times and combines them with the date', () => {
    expect(formatTime('2026-11-05T07:52:00')).toBe('07:52');
    expect(formatDateTime('2026-11-05T07:52:00')).toBe(
      `${formatDate('2026-11-05T07:52:00')}, 07:52`,
    );
  });

  it('labels today and yesterday relative to a fixed now', () => {
    const now = new Date(2026, 10, 5, 18, 0, 0);
    expect(formatRelativeDay('2026-11-05', now)).toBe('Hoy');
    expect(formatRelativeDay('2026-11-04', now)).toBe('Ayer');
    expect(formatRelativeDay('2026-11-02', now)).toMatch(/nov/i);
  });

  it('formats play counts and numbers in Spanish', () => {
    expect(formatPlays(1)).toBe('1 reproducción');
    expect(formatPlays(12)).toBe('12 reproducciones');
    expect(formatNumber(12345)).toBe('12.345');
  });
});
