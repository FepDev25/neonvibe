import { create } from 'zustand';

export type SyncStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

interface SyncState {
  status: SyncStatus;
  setStatus: (status: SyncStatus) => void;
  reset: () => void;
}

/** Live state of the player-sync WebSocket, shown in the header. */
export const useSyncStore = create<SyncState>()((set) => ({
  status: 'idle',
  setStatus: (status) => set({ status }),
  reset: () => set({ status: 'idle' }),
}));
