/**
 * Minimal promise-based IndexedDB wrapper for offline track downloads.
 *
 * DB `neonvibe-offline`, store `tracks` (keyPath `id`), indexed by
 * `downloadedAt`. Blobs are stored directly so a downloaded album can be
 * played without network.
 */
export interface OfflineTrackRecord {
  id: number;
  blob: Blob;
  title: string;
  artist: string;
  album?: string;
  durationSeconds?: number;
  size: number;
  downloadedAt: number;
}

const DB_NAME = 'neonvibe-offline';
const STORE = 'tracks';
const VERSION = 1;

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) {
    return dbPromise;
  }
  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB is not available'));
      return;
    }
    const req = indexedDB.open(DB_NAME, VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'id' });
        store.createIndex('downloadedAt', 'downloadedAt', { unique: false });
      }
    };
    req.onsuccess = () => {
      const db = req.result;
      // Close this connection if another tab upgrades the DB.
      db.onversionchange = () => db.close();
      resolve(db);
    };
    req.onerror = () => reject(req.error ?? new Error('IndexedDB open failed'));
  });
  return dbPromise;
}

function tx<T>(mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(STORE, mode);
        const request = fn(t.objectStore(STORE));
        t.oncomplete = () => resolve(request.result);
        t.onerror = () => reject(t.error ?? new Error('IndexedDB transaction failed'));
        t.onabort = () => reject(t.error ?? new Error('IndexedDB transaction aborted'));
      }),
  );
}

export const offlineDb = {
  put(record: OfflineTrackRecord): Promise<IDBValidKey> {
    return tx('readwrite', (s) => s.put(record));
  },

  get(id: number): Promise<OfflineTrackRecord | undefined> {
    return tx('readonly', (s) => s.get(id));
  },

  delete(id: number): Promise<undefined> {
    return tx('readwrite', (s) => s.delete(id) as IDBRequest<undefined>);
  },

  keys(): Promise<number[]> {
    return tx('readonly', (s) => s.getAllKeys() as IDBRequest<number[]>);
  },

  getAll(): Promise<OfflineTrackRecord[]> {
    return tx('readonly', (s) => s.getAll());
  },
};
