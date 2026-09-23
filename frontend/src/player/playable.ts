import { useAuthStore } from '@/stores/authStore';
import { offlineDb } from '@/offline/offlineDb';

/** Streaming URL with the JWT as a query param (for <audio>/fetch). */
export function streamUrl(trackId: number): string {
  const token = useAuthStore.getState().token;
  return `/api/v1/tracks/${trackId}/stream${token ? `?token=${encodeURIComponent(token)}` : ''}`;
}

// Blob URLs are cached per track id and revoked when replaced.
const blobUrlCache = new Map<number, string>();

/**
 * Returns a playable URL for a track: the normal streaming URL when online, or
 * a blob URL from IndexedDB when offline and the track was downloaded.
 */
export async function getPlayableSrc(trackId: number): Promise<string> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    const blobUrl = await getOfflineBlobUrl(trackId);
    if (blobUrl) {
      return blobUrl;
    }
  }
  return streamUrl(trackId);
}

/** Blob URL for a downloaded track, or null. */
export async function getOfflineBlobUrl(trackId: number): Promise<string | null> {
  const cached = blobUrlCache.get(trackId);
  if (cached) {
    return cached;
  }
  const record = await offlineDb.get(trackId).catch(() => undefined);
  if (!record) {
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
