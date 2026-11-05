import { refreshAccessToken, isAccessTokenExpiring } from '@/api/client';
import { useAuthStore } from '@/stores/authStore';
import { useQualityStore } from '@/stores/qualityStore';
import { offlineDb } from '@/offline/offlineDb';

/** Streaming URL with the JWT as a query param (for <audio>/fetch). */
export function streamUrl(trackId: number): string {
  const token = useAuthStore.getState().token;
  const quality = useQualityStore.getState().quality;
  const params = new URLSearchParams();
  if (token) {
    params.set('token', token);
  }
  if (quality !== 'original') {
    params.set('quality', quality);
  }
  const query = params.toString();
  return `/api/v1/tracks/${trackId}/stream${query ? `?${query}` : ''}`;
}

export type PlayableKind = 'stream' | 'offline';

export interface PlayableSource {
  src: string;
  kind: PlayableKind;
}

/**
 * The track cannot be played because there is no network connection and no
 * downloaded copy. Typed so the player can show a clear message instead of
 * skipping through the queue in silence.
 */
export class OfflineUnavailableError extends Error {
  constructor(public readonly trackId: number) {
    super(`Track ${trackId} is not available offline`);
    this.name = 'OfflineUnavailableError';
  }
}

function networkAvailable(): boolean {
  // `navigator.onLine` is only a hint (false positives and negatives exist),
  // but a definitive `false` is still worth using to go straight to the blob.
  return typeof navigator === 'undefined' || navigator.onLine !== false;
}

/**
 * Stream URL that can be assigned synchronously, or null when async work is
 * required first (offline blob lookup, proactive token refresh). Keeping this
 * path synchronous preserves the user-activation window on the first play.
 */
export function getImmediatePlayableSrc(trackId: number): string | null {
  if (!networkAvailable()) {
    return null;
  }
  if (isAccessTokenExpiring(useAuthStore.getState().token)) {
    return null;
  }
  return streamUrl(trackId);
}

/** Online URL for prefetching, only when the current token is still valid. */
export function getPrefetchableStreamUrl(trackId: number): string | null {
  if (!networkAvailable() || isAccessTokenExpiring(useAuthStore.getState().token)) {
    return null;
  }
  return streamUrl(trackId);
}

/**
 * Resolves a playable source doing the async work only when strictly needed:
 * - offline: IndexedDB blob (or a typed "unavailable offline" error);
 * - online with a token about to expire: refresh first, then stream.
 */
export async function resolvePlayableSrc(trackId: number): Promise<PlayableSource> {
  if (!networkAvailable()) {
    const blobUrl = await getOfflineBlobUrl(trackId);
    if (blobUrl) {
      return { src: blobUrl, kind: 'offline' };
    }
    throw new OfflineUnavailableError(trackId);
  }
  if (isAccessTokenExpiring(useAuthStore.getState().token)) {
    try {
      await refreshAccessToken();
    } catch {
      // Keep the old token: if it is still valid the request works; if not,
      // the media error path classifies it without an infinite retry loop.
    }
  }
  return { src: streamUrl(trackId), kind: 'stream' };
}

/** Backwards-compatible helper returning only the URL. */
export async function getPlayableSrc(trackId: number): Promise<string> {
  return (await resolvePlayableSrc(trackId)).src;
}

// Blob URLs are cached per track id and revoked when replaced.
const blobUrlCache = new Map<number, string>();

/** Blob URL for a downloaded track, or null. */
export async function getOfflineBlobUrl(trackId: number): Promise<string | null> {
  const cached = blobUrlCache.get(trackId);
  if (cached) {
    return cached;
  }
  const record = await offlineDb.get(trackId).catch(() => undefined);
  if (!record || !record.blob) {
    return null;
  }
  const url = URL.createObjectURL(record.blob);
  blobUrlCache.set(trackId, url);
  return url;
}

/** Revokes a cached blob URL (e.g. when the offline copy is deleted). */
export function revokeBlobUrl(trackId: number): void {
  const url = blobUrlCache.get(trackId);
  if (url) {
    URL.revokeObjectURL(url);
    blobUrlCache.delete(trackId);
  }
}

/** Revokes every cached blob URL (logout / session reset). */
export function clearBlobUrlCache(): void {
  for (const url of blobUrlCache.values()) {
    URL.revokeObjectURL(url);
  }
  blobUrlCache.clear();
  deferredRevoke.clear();
}

// Tracks whose offline copy was deleted while they were playing: revoking their
// blob URL immediately would cut the audio, so it is deferred until playback
// moves to another track.
const deferredRevoke = new Set<number>();

/** Defers revoking a blob URL until the track is no longer playing. */
export function deferRevokeBlobUrl(trackId: number): void {
  deferredRevoke.add(trackId);
}

/** Revokes deferred blob URLs except the one currently playing. */
export function flushDeferredRevokes(keepTrackId?: number): void {
  for (const id of [...deferredRevoke]) {
    if (id !== keepTrackId) {
      revokeBlobUrl(id);
      deferredRevoke.delete(id);
    }
  }
}
