import { getAudioElement } from '@/stores/playerStore';

// Web Audio graph is created once and never torn down, so routing the audio
// element through the AnalyserNode keeps sound alive (disconnecting it would
// silence playback). AudioContext needs a user gesture; it is resumed lazily.
let ctx: AudioContext | null = null;
let analyser: AnalyserNode | null = null;
let connected = false;

/** Creates (and connects) the media-element -> analyser -> destination graph. */
export function ensureGraph(): boolean {
  if (connected && analyser) {
    if (ctx?.state === 'suspended') {
      void ctx.resume();
    }
    return true;
  }
  if (typeof window === 'undefined' || !window.AudioContext) {
    return false;
  }
  try {
    ctx = ctx ?? new AudioContext();
    if (ctx.state === 'suspended') {
      void ctx.resume();
    }
    if (!connected) {
      const source = ctx.createMediaElementSource(getAudioElement());
      analyser = ctx.createAnalyser();
      analyser.fftSize = 128;
      analyser.smoothingTimeConstant = 0.8;
      source.connect(analyser);
      analyser.connect(ctx.destination);
      connected = true;
    }
    return true;
  } catch {
    return false;
  }
}

/**
 * Keeps the AudioContext running so playback stays audible. Safe to call from
 * the player before play(); no-op when the graph was never created (audio then
 * plays directly through the media element).
 */
export function ensureAudioRunning(): void {
  if (ctx && ctx.state === 'suspended') {
    void ctx.resume();
  }
}

export function getAnalyser(): AnalyserNode | null {
  return analyser;
}
