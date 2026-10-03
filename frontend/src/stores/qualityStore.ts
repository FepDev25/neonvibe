import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type AudioQuality = 'original' | 'high' | 'normal' | 'data';

export interface QualityOption {
  id: AudioQuality;
  label: string;
}

/** Presets shown in the player's quality selector. */
export const QUALITY_OPTIONS: QualityOption[] = [
  { id: 'original', label: 'Original' },
  { id: 'high', label: 'Alta · 320' },
  { id: 'normal', label: 'Normal · 192' },
  { id: 'data', label: 'Ahorro · 128' },
];

interface QualityState {
  quality: AudioQuality;
  setQuality: (quality: AudioQuality) => void;
}

/**
 * Selected streaming quality (manual, persisted locally). The stream URL reads
 * it at play time; changing it reloads the current track at the same position.
 */
export const useQualityStore = create<QualityState>()(
  persist(
    (set) => ({
      quality: 'original',
      setQuality: (quality) => set({ quality }),
    }),
    { name: 'neonvibe-quality' },
  ),
);
