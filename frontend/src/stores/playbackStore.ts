import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface EqBand {
  frequency: number;
  label: string;
}

/** Five octave-ish bands, enough for a lightweight tone control. */
export const EQ_BANDS: EqBand[] = [
  { frequency: 60, label: '60 Hz' },
  { frequency: 230, label: '230 Hz' },
  { frequency: 910, label: '910 Hz' },
  { frequency: 3600, label: '3.6 kHz' },
  { frequency: 14000, label: '14 kHz' },
];

export const EQ_MIN_DB = -12;
export const EQ_MAX_DB = 12;
export const DEFAULT_EQ_GAINS: number[] = EQ_BANDS.map(() => 0);

interface PlaybackState {
  /** Bounded prefetch of the next track. On by default (opt-out). */
  prefetch: boolean;
  /** Equalizer is opt-in: off means a clean bypass. */
  eqEnabled: boolean;
  /** Gain in dB per band, same order as EQ_BANDS. */
  eqGains: number[];
  setPrefetch: (enabled: boolean) => void;
  setEqEnabled: (enabled: boolean) => void;
  setEqGain: (index: number, gainDb: number) => void;
  resetEq: () => void;
}

/**
 * Local playback preferences (device-specific, so localStorage is the right
 * store). Kept free of audio imports to avoid cycles: `audioGraph` reads this
 * store when it builds/applies the DSP chain.
 */
export const usePlaybackStore = create<PlaybackState>()(
  persist(
    (set, get) => ({
      prefetch: true,
      eqEnabled: false,
      eqGains: [...DEFAULT_EQ_GAINS],
      setPrefetch: (prefetch) => set({ prefetch }),
      setEqEnabled: (eqEnabled) => set({ eqEnabled }),
      setEqGain: (index, gainDb) => {
        const gains = [...get().eqGains];
        if (index < 0 || index >= gains.length) {
          return;
        }
        gains[index] = Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, gainDb));
        set({ eqGains: gains });
      },
      resetEq: () => set({ eqGains: [...DEFAULT_EQ_GAINS] }),
    }),
    { name: 'neonvibe-playback' },
  ),
);
