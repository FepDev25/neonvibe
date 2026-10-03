import { describe, expect, it } from 'vitest';
import {
  formatBytes,
  formatDateTime,
  formatDuration,
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
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe('3.0 GB');
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
