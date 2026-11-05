import { usePlaybackStore } from '@/stores/playbackStore';
import { useQualityStore, type AudioQuality } from '@/stores/qualityStore';
import { getPrefetchableStreamUrl } from './playable';

/**
 * Bounded next-track prefetch.
 *
 * Design constraints (see docs/player/diagnostico-motor-web.md):
 * - Only the SINGLE next track, never an album/playlist.
 * - Hard byte cap; if the server does not advertise the size, the body is read
 *   with a running cap and cancelled as soon as it is exceeded.
 * - Never plays audio: the bytes become a Blob URL consumed by the main
 *   `<audio>` element on the next transition.
 * - Respects Save-Data / 2G / prefers-reduced-data and the opt-out setting.
 * - One in-flight request and one cached entry; cancelled when the track,
 *   queue, quality or user changes.
 *
 * This is a latency optimisation, never a correctness requirement: every
 * failure is swallowed and playback falls back to normal range streaming.
 */
export const MAX_PREFETCH_BYTES = 12 * 1024 * 1024;

interface PrefetchEntry {
  trackId: number;
  url: string;
  quality: AudioQuality;
}

interface NetworkInformationLike {
  saveData?: boolean;
  effectiveType?: string;
}

let controller: AbortController | null = null;
let activeTrackId: number | null = null;
let entry: PrefetchEntry | null = null;

export function getConnectionInfo(): NetworkInformationLike | undefined {
  if (typeof navigator === 'undefined') {
    return undefined;
  }
  return (navigator as Navigator & { connection?: NetworkInformationLike }).connection;
}

/** Whether prefetch is allowed right now by settings + network hints. */
export function prefetchEnabled(): boolean {
  if (!usePlaybackStore.getState().prefetch) {
    return false;
  }
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return false;
  }
  const connection = getConnectionInfo();
  if (connection?.saveData) {
    return false;
  }
  if (connection?.effectiveType && /^(slow-2g|2g)$/.test(connection.effectiveType)) {
    return false;
  }
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-data: reduce)').matches) {
    return false;
  }
  return typeof fetch === 'function' && typeof URL.createObjectURL === 'function';
}

function dismissEntry(): void {
  if (entry) {
    URL.revokeObjectURL(entry.url);
    entry = null;
  }
}

function parseTotal(headers: Headers): number | null {
  const contentRange = headers.get('Content-Range');
  if (contentRange) {
    const match = /\/(\d+)\s*$/.exec(contentRange);
    if (match) {
      return Number(match[1]);
    }
  }
  const length = headers.get('Content-Length');
  if (length) {
    const value = Number(length);
    return Number.isFinite(value) ? value : null;
  }
  return null;
}

/** Reads a response body, aborting as soon as it exceeds `limit` bytes. */
async function readCapped(
  response: Response,
  limit: number,
  signal: AbortSignal,
): Promise<Blob | null> {
  const type = response.headers.get('Content-Type') || 'application/octet-stream';
  if (!response.body) {
    const buffer = await response.arrayBuffer();
    if (buffer.byteLength > limit) {
      return null;
    }
    return new Blob([buffer], { type });
  }
  const reader = response.body.getReader();
  const chunks: BlobPart[] = [];
  let received = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      if (signal.aborted) {
        await reader.cancel().catch(() => undefined);
        return null;
      }
      if (value) {
        received += value.byteLength;
        if (received > limit) {
          await reader.cancel().catch(() => undefined);
          return null;
        }
        chunks.push(value as BlobPart);
      }
    }
  } catch {
    return null;
  }
  return new Blob(chunks, { type });
}

/**
 * Starts prefetching `trackId` if policy allows it. Safe to call repeatedly:
 * the same track in flight/cached is ignored.
 */
export function prefetchTrack(trackId: number): void {
  if (!prefetchEnabled()) {
    return;
  }
  if (activeTrackId === trackId || entry?.trackId === trackId) {
    return;
  }
  const url = getPrefetchableStreamUrl(trackId);
  if (!url) {
    return;
  }

  cancelPrefetch();
  dismissEntry();

  const quality = useQualityStore.getState().quality;
  const request = new AbortController();
  controller = request;
  activeTrackId = trackId;

  void (async () => {
    try {
      const response = await fetch(url, {
        signal: request.signal,
        headers: { Range: 'bytes=0-' },
        credentials: 'same-origin',
      });
      if (!response.ok) {
        return;
      }
      const advertised = parseTotal(response.headers);
      if (advertised != null && advertised > MAX_PREFETCH_BYTES) {
        return;
      }
      const blob = await readCapped(response, MAX_PREFETCH_BYTES, request.signal);
      if (!blob || request.signal.aborted || blob.size === 0) {
        return;
      }
      // A newer prefetch or a track change may have happened while reading.
      if (controller !== request) {
        return;
      }
      if (useQualityStore.getState().quality !== quality) {
        return;
      }
      entry = { trackId, url: URL.createObjectURL(blob), quality };
    } catch {
      /* Prefetch is best-effort: network errors and aborts are ignored. */
    } finally {
      if (controller === request) {
        controller = null;
        activeTrackId = null;
      }
    }
  })();
}

/**
 * Returns the prefetched Blob URL for `trackId` and transfers ownership to the
 * caller (who must revoke it). A quality mismatch discards the entry.
 */
export function takePrefetched(
  trackId: number,
  quality: AudioQuality = useQualityStore.getState().quality,
): string | null {
  if (!entry || entry.trackId !== trackId) {
    return null;
  }
  if (entry.quality !== quality) {
    dismissEntry();
    return null;
  }
  const url = entry.url;
  entry = null;
  return url;
}

/** Cancels the in-flight request, keeping an already completed entry. */
export function cancelPrefetch(): void {
  if (controller) {
    controller.abort();
    controller = null;
  }
  activeTrackId = null;
}

/** Cancels and revokes every prefetched resource (logout / reset). */
export function clearPrefetch(): void {
  cancelPrefetch();
  dismissEntry();
}

/** Test/introspection helper. */
export function hasPrefetched(trackId: number): boolean {
  return entry?.trackId === trackId;
}
