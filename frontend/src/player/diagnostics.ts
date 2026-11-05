/**
 * Opt-in playback diagnostics.
 *
 * The player must never log JWTs, authenticated URLs, private metadata or blob
 * contents. This module keeps a small bounded ring buffer of sanitised events
 * (category, timestamp, track id, status) that can be exported as JSON when the
 * owner needs to diagnose background playback on a real device.
 *
 * Enabled automatically in development; in production it requires the explicit
 * opt-in `localStorage.setItem('neonvibe-player-debug', '1')` (and a reload).
 */
export interface PlayerDiagnosticEvent {
  at: number;
  event: string;
  trackId: number | null;
  status?: string;
  detail?: string;
}

export const PLAYER_DIAGNOSTICS_KEY = 'neonvibe-player-debug';
export const MAX_DIAGNOSTIC_EVENTS = 100;

const buffer: PlayerDiagnosticEvent[] = [];
let enabled: boolean | null = null;

function detectEnabled(): boolean {
  if (typeof localStorage === 'undefined') {
    return false;
  }
  try {
    return localStorage.getItem(PLAYER_DIAGNOSTICS_KEY) === '1';
  } catch {
    return false;
  }
}

export function playerDiagnosticsEnabled(): boolean {
  if (enabled == null) {
    // Development builds are verbose by default; production is opt-in only.
    enabled = (typeof import.meta !== 'undefined' && import.meta.env?.DEV === true)
      || detectEnabled();
  }
  return enabled;
}

/** Allows tests and the owner to toggle diagnostics at runtime. */
export function setPlayerDiagnosticsEnabled(value: boolean): void {
  enabled = value;
}

/** Removes tokens, authenticated URLs and blob URLs from free-form details. */
export function sanitizeDiagnosticDetail(detail: string): string {
  return detail
    .replace(/([?&]token=)[^&\s]+/gi, '$1[redacted]')
    .replace(/blob:[^\s]+/gi, 'blob:[redacted]')
    .replace(/Bearer\s+[^\s]+/gi, 'Bearer [redacted]')
    .slice(0, 160);
}

export function logPlayerEvent(
  event: string,
  data: { trackId?: number | null; status?: string; detail?: string } = {},
): void {
  if (!playerDiagnosticsEnabled()) {
    return;
  }
  const entry: PlayerDiagnosticEvent = {
    at: Date.now(),
    event,
    trackId: data.trackId ?? null,
    ...(data.status ? { status: data.status } : {}),
    ...(data.detail ? { detail: sanitizeDiagnosticDetail(data.detail) } : {}),
  };
  buffer.push(entry);
  if (buffer.length > MAX_DIAGNOSTIC_EVENTS) {
    buffer.splice(0, buffer.length - MAX_DIAGNOSTIC_EVENTS);
  }
}

export function getPlayerDiagnostics(): readonly PlayerDiagnosticEvent[] {
  return buffer;
}

export function clearPlayerDiagnostics(): void {
  buffer.length = 0;
}

/** Copy/paste-friendly sanitised export (never includes credentials). */
export function exportPlayerDiagnostics(): string {
  return JSON.stringify(
    {
      exportedAt: new Date().toISOString(),
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'unknown',
      events: buffer,
    },
    null,
    2,
  );
}

// Opt-in bridge for the owner's Android QA: no diagnostics object exists unless
// development mode or `neonvibe-player-debug=1` enabled it.
if (typeof window !== 'undefined' && playerDiagnosticsEnabled()) {
  (window as Window & { neonvibePlayerDiagnostics?: unknown }).neonvibePlayerDiagnostics = {
    export: exportPlayerDiagnostics,
    clear: clearPlayerDiagnostics,
    get: getPlayerDiagnostics,
  };
}
