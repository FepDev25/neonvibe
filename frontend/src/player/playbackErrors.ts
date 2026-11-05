/**
 * Error taxonomy for the playback engine.
 *
 * The player needs to distinguish "the browser refused to start audio" from
 * "the network failed", "the file is broken" or "there is no offline copy", so
 * it can retry at most once, fall back to a downloaded copy, or stop with a
 * visible message instead of skipping the whole queue.
 */
export type PlayerErrorKind =
  | 'autoplay-blocked'
  | 'network'
  | 'media'
  | 'offline-unavailable'
  | 'auth'
  | 'unknown';

export interface PlayerError {
  kind: PlayerErrorKind;
  message: string;
  trackId: number | null;
  /** Whether pressing "retry" / play can reasonably work. */
  recoverable: boolean;
}

const AUTOPLAY_MESSAGE =
  'El navegador bloqueó la reproducción automática. Pulsa reproducir para continuar.';
const NETWORK_MESSAGE =
  'No se pudo cargar la canción. Comprueba la conexión e inténtalo de nuevo.';
const MEDIA_MESSAGE =
  'El archivo de audio no se pudo reproducir (puede estar dañado o no estar disponible).';
const OFFLINE_MESSAGE =
  'Esta canción no está descargada y no hay conexión. Descárgala o vuelve a conectarte.';
const AUTH_MESSAGE =
  'La sesión ha caducado y no se pudo renovar. Vuelve a iniciar sesión.';
const UNKNOWN_MESSAGE = 'No se pudo reproducir la canción.';

function errorName(error: unknown): string {
  if (error && typeof error === 'object' && 'name' in error) {
    return String((error as { name?: unknown }).name ?? '');
  }
  return '';
}

/** Classifies the promise rejection from `HTMLMediaElement.play()`. */
export function classifyPlayRejection(error: unknown, trackId: number | null): PlayerError {
  const name = errorName(error);
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return {
      kind: 'autoplay-blocked',
      message: AUTOPLAY_MESSAGE,
      trackId,
      recoverable: true,
    };
  }
  if (name === 'NotSupportedError') {
    return { kind: 'media', message: MEDIA_MESSAGE, trackId, recoverable: false };
  }
  if (name === 'AbortError') {
    // A load/pause interrupted play(); never user-facing.
    return { kind: 'unknown', message: '', trackId, recoverable: true };
  }
  return { kind: 'unknown', message: UNKNOWN_MESSAGE, trackId, recoverable: true };
}

/** Classification of the media element's `error` event payload. */
export interface MediaErrorClassification {
  error: PlayerError;
  /** `true` when the event should be ignored (aborted/stale load). */
  ignore: boolean;
}

export function classifyMediaError(
  code: number | undefined,
  trackId: number | null,
  online: boolean,
): MediaErrorClassification {
  switch (code) {
    case 1: // MEDIA_ERR_ABORTED: the user agent stopped fetching (often our own
      // src swap). Not a failure the user should see.
      return {
        ignore: true,
        error: { kind: 'unknown', message: '', trackId, recoverable: true },
      };
    case 2: // MEDIA_ERR_NETWORK
      return {
        ignore: false,
        error: online
          ? { kind: 'network', message: NETWORK_MESSAGE, trackId, recoverable: true }
          : { kind: 'offline-unavailable', message: OFFLINE_MESSAGE, trackId, recoverable: true },
      };
    case 3: // MEDIA_ERR_DECODE
    case 4: // MEDIA_ERR_SRC_NOT_SUPPORTED (also what a 401/404 JSON body yields)
      return {
        ignore: false,
        error: { kind: 'media', message: MEDIA_MESSAGE, trackId, recoverable: false },
      };
    default:
      return {
        ignore: false,
        error: { kind: 'unknown', message: UNKNOWN_MESSAGE, trackId, recoverable: true },
      };
  }
}

export const PLAYER_ERROR_MESSAGES = {
  autoplayBlocked: AUTOPLAY_MESSAGE,
  network: NETWORK_MESSAGE,
  media: MEDIA_MESSAGE,
  offline: OFFLINE_MESSAGE,
  auth: AUTH_MESSAGE,
  unknown: UNKNOWN_MESSAGE,
};

export function authError(trackId: number | null): PlayerError {
  return { kind: 'auth', message: AUTH_MESSAGE, trackId, recoverable: false };
}

export function offlineError(trackId: number | null): PlayerError {
  return { kind: 'offline-unavailable', message: OFFLINE_MESSAGE, trackId, recoverable: true };
}
