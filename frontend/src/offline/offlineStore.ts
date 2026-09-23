import { create } from 'zustand';
import { useAuthStore } from '@/stores/authStore';
import { offlineDb, type OfflineTrackRecord } from './offlineDb';
import { revokeBlobUrl, streamUrl } from '@/player/playable';
import { usePlayerStore, type PlayerTrack } from '@/stores/playerStore';

export type DownloadStatus = 'idle' | 'downloading' | 'done' | 'error';

// Active fetch controllers so downloads can be aborted (e.g. on remove).
const activeControllers = new Map<number, AbortController>();

interface DownloadState {
  /** track id -> download status */
  statuses: Record<number, DownloadStatus>;
  /** track id -> progress 0..1 */
  progress: Record<number, number>;
  loaded: boolean;

  ensureLoaded: () => Promise<void>;
  isDownloaded: (trackId: number) => boolean;
  getStatus: (trackId: number) => DownloadStatus;
  downloadTrack: (track: PlayerTrack) => Promise<boolean>;
  downloadBatch: (tracks: PlayerTrack[]) => Promise<void>;
  removeTrack: (trackId: number) => Promise<void>;
  downloadedTracks: () => Promise<OfflineTrackRecord[]>;
}

let loadPromise: Promise<void> | null = null;

/**
 * Offline downloads: tracks are fetched as blobs (with the JWT header) and
 * stored in IndexedDB. Downloads run sequentially and report per-track progress.
 */
export const useOfflineStore = create<DownloadState>()((set, get) => ({
  statuses: {},
  progress: {},
  loaded: false,

  ensureLoaded: async () => {
    if (get().loaded) {
      return;
    }
    if (loadPromise) {
      return loadPromise;
    }
    loadPromise = (async () => {
      try {
        const keys = await offlineDb.keys();
        const statuses: Record<number, DownloadStatus> = {};
        for (const id of keys) {
          statuses[id] = 'done';
        }
        set({ statuses, loaded: true });
      } catch (err) {
        console.warn('[offline] load failed', err);
      } finally {
        loadPromise = null;
      }
    })();
    return loadPromise;
  },

  isDownloaded: (trackId) => get().statuses[trackId] === 'done',
  getStatus: (trackId) => get().statuses[trackId] ?? 'idle',

  downloadTrack: async (track) => {
    const token = useAuthStore.getState().token;
    const controller = new AbortController();
    activeControllers.set(track.id, controller);
    try {
      set((s) => ({
        statuses: { ...s.statuses, [track.id]: 'downloading' },
        progress: { ...s.progress, [track.id]: 0 },
      }));

      const res = await fetch(streamUrl(track.id), {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        signal: controller.signal,
      });
      if (!res.ok) {
        throw new Error(`Download failed: ${res.status}`);
      }
      if (!res.body) {
        throw new Error('No response body');
      }

      const total = Number(res.headers.get('Content-Length')) || 0;
      const reader = res.body.getReader();
      const chunks: BlobPart[] = [];
      let received = 0;
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        chunks.push(value);
        received += value.length;
        if (total > 0) {
          const p = Math.min(1, received / total);
          set((s) => ({ progress: { ...s.progress, [track.id]: p } }));
        }
      }
      const blob = new Blob(chunks, { type: 'audio/mpeg' });
      const record: OfflineTrackRecord = {
        id: track.id,
        blob,
        title: track.title,
        artist: track.artist,
        album: track.album,
        durationSeconds: track.durationSeconds,
        size: received,
        downloadedAt: Date.now(),
      };
      await offlineDb.put(record);
      set((s) => ({
        statuses: { ...s.statuses, [track.id]: 'done' },
        progress: { ...s.progress, [track.id]: 1 },
      }));
      return true;
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        return false; // aborted: leave the status as-is (remove clears it)
      }
      console.warn(`[offline] download failed for ${track.title}`, err);
      set((s) => ({ statuses: { ...s.statuses, [track.id]: 'error' } }));
      return false;
    } finally {
      activeControllers.delete(track.id);
    }
  },

  downloadBatch: async (tracks) => {
    // Sequential to avoid saturating the network / quota at once.
    for (const track of tracks) {
      if (get().statuses[track.id] === 'done') {
        continue;
      }
      await get().downloadTrack(track);
    }
  },

  removeTrack: async (trackId) => {
    activeControllers.get(trackId)?.abort();
    await offlineDb.delete(trackId).catch(() => undefined);
    // Don't revoke a blob that is currently playing (would cut audio).
    const playingId = usePlayerStore.getState().currentTrack?.id;
    if (playingId !== trackId) {
      revokeBlobUrl(trackId);
    }
    set((s) => {
      const statuses = { ...s.statuses };
      const progress = { ...s.progress };
      delete statuses[trackId];
      delete progress[trackId];
      return { statuses, progress };
    });
  },

  downloadedTracks: () => offlineDb.getAll(),
}));
