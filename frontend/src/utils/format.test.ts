import { describe, expect, it } from 'vitest';
import { formatDuration, formatTotalDuration, formatTrackCount } from './format';

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
