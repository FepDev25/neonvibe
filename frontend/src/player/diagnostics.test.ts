import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  MAX_DIAGNOSTIC_EVENTS,
  clearPlayerDiagnostics,
  exportPlayerDiagnostics,
  getPlayerDiagnostics,
  logPlayerEvent,
  sanitizeDiagnosticDetail,
  setPlayerDiagnosticsEnabled,
} from './diagnostics';

beforeEach(() => {
  clearPlayerDiagnostics();
  setPlayerDiagnosticsEnabled(true);
});

afterEach(() => {
  setPlayerDiagnosticsEnabled(false);
});

describe('player diagnostics', () => {
  it('keeps a bounded buffer of events', () => {
    for (let i = 0; i < MAX_DIAGNOSTIC_EVENTS + 20; i++) {
      logPlayerEvent('tick', { trackId: i });
    }
    const events = getPlayerDiagnostics();
    expect(events).toHaveLength(MAX_DIAGNOSTIC_EVENTS);
    expect(events[events.length - 1].trackId).toBe(MAX_DIAGNOSTIC_EVENTS + 19);
  });

  it('does not log when disabled', () => {
    setPlayerDiagnosticsEnabled(false);
    logPlayerEvent('play.request', { trackId: 1 });
    expect(getPlayerDiagnostics()).toHaveLength(0);
  });

  it('redacts tokens, bearer headers and blob URLs', () => {
    const detail = sanitizeDiagnosticDetail(
      'GET /api/v1/tracks/1/stream?token=secret.jwt.value&quality=normal Bearer abc.def blob:http://x/123',
    );
    expect(detail).not.toContain('secret.jwt.value');
    expect(detail).not.toContain('abc.def');
    expect(detail).not.toContain('blob:http://x/123');
    expect(detail).toContain('token=[redacted]');
    expect(detail).toContain('Bearer [redacted]');
    expect(detail).toContain('blob:[redacted]');
  });

  it('truncates long details and includes timestamp/category/status', () => {
    logPlayerEvent('audio.error', { trackId: 7, status: 'error', detail: 'x'.repeat(500) });
    const [event] = getPlayerDiagnostics();
    expect(event.event).toBe('audio.error');
    expect(event.trackId).toBe(7);
    expect(event.status).toBe('error');
    expect(event.at).toBeTypeOf('number');
    expect(event.detail?.length).toBeLessThanOrEqual(160);
  });

  it('exports sanitized JSON without credentials', () => {
    logPlayerEvent('play.rejected', {
      trackId: 2,
      detail: 'NotAllowedError /stream?token=super-secret',
    });
    const exported = exportPlayerDiagnostics();
    expect(exported).not.toContain('super-secret');
    const parsed = JSON.parse(exported) as { events: unknown[] };
    expect(parsed.events).toHaveLength(1);
  });
});
