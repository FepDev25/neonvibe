import { describe, expect, it } from 'vitest';
import {
  authError,
  classifyMediaError,
  classifyPlayRejection,
  offlineError,
} from './playbackErrors';

function namedError(name: string): Error {
  return Object.assign(new Error(name), { name });
}

describe('classifyPlayRejection', () => {
  it('marks a blocked autoplay as recoverable', () => {
    const error = classifyPlayRejection(namedError('NotAllowedError'), 7);
    expect(error.kind).toBe('autoplay-blocked');
    expect(error.trackId).toBe(7);
    expect(error.recoverable).toBe(true);
    expect(error.message).toContain('Pulsa reproducir');
  });

  it('treats security errors like blocked autoplay', () => {
    expect(classifyPlayRejection(namedError('SecurityError'), 1).kind).toBe('autoplay-blocked');
  });

  it('classifies unsupported media as non-recoverable', () => {
    const error = classifyPlayRejection(namedError('NotSupportedError'), 2);
    expect(error.kind).toBe('media');
    expect(error.recoverable).toBe(false);
  });

  it('treats AbortError as an internal interruption with no user message', () => {
    const error = classifyPlayRejection(namedError('AbortError'), 3);
    expect(error.message).toBe('');
  });

  it('falls back to unknown for unexpected rejections', () => {
    expect(classifyPlayRejection(new Error('boom'), null).kind).toBe('unknown');
  });
});

describe('classifyMediaError', () => {
  it('ignores MEDIA_ERR_ABORTED', () => {
    expect(classifyMediaError(1, 1, true).ignore).toBe(true);
  });

  it('classifies network errors differently online and offline', () => {
    expect(classifyMediaError(2, 1, true).error.kind).toBe('network');
    expect(classifyMediaError(2, 1, false).error.kind).toBe('offline-unavailable');
  });

  it('classifies decode and src-not-supported as media errors', () => {
    expect(classifyMediaError(3, 1, true).error.kind).toBe('media');
    expect(classifyMediaError(4, 1, true).error.kind).toBe('media');
  });

  it('has a safe default when the code is missing', () => {
    expect(classifyMediaError(undefined, 1, true).error.kind).toBe('unknown');
  });
});

describe('error constructors', () => {
  it('builds auth and offline errors with the track id', () => {
    expect(authError(4)).toMatchObject({ kind: 'auth', trackId: 4, recoverable: false });
    expect(offlineError(5)).toMatchObject({
      kind: 'offline-unavailable',
      trackId: 5,
      recoverable: true,
    });
  });
});
