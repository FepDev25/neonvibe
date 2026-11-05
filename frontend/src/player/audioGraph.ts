import { getAudioElement } from '@/player/audioElement';
import {
  EQ_BANDS,
  EQ_MAX_DB,
  EQ_MIN_DB,
  usePlaybackStore,
} from '@/stores/playbackStore';

/**
 * Single Web Audio graph for the shared <audio> element:
 *
 *   MediaElementAudioSourceNode -> preamp GainNode -> 5 BiquadFilterNodes
 *     -> AnalyserNode -> destination
 *
 * The graph is created once and never torn down: disconnecting the source would
 * silence playback. The equalizer is flat (0 dB, a clean bypass) when disabled,
 * so creating the graph for the visualizer does not change the sound.
 *
 * The MediaElementAudioSourceNode is created exactly once per element (the
 * platform throws if it is recreated), and the context is resumed lazily from a
 * user gesture or when playback/visibility changes.
 */
let ctx: AudioContext | null = null;
let source: MediaElementAudioSourceNode | null = null;
let preamp: GainNode | null = null;
let analyser: AnalyserNode | null = null;
let filters: BiquadFilterNode[] = [];
let connected = false;
/** Set when the element was rerouted but the EQ chain could not be built. */
let directOnly = false;

function clampGain(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, value));
}

/** Applies the persisted EQ settings to the existing chain (no-op if none). */
export function applyEqSettings(): void {
  if (!connected) {
    return;
  }
  const { eqEnabled, eqGains } = usePlaybackStore.getState();
  filters.forEach((filter, index) => {
    filter.gain.value = eqEnabled ? clampGain(eqGains[index] ?? 0) : 0;
  });
}

/** Creates (and connects) the media-element -> EQ -> analyser -> destination graph. */
export function ensureGraph(): boolean {
  if (connected) {
    ensureAudioRunning();
    applyEqSettings();
    return true;
  }
  if (directOnly) {
    return false;
  }
  if (typeof window === 'undefined' || !window.AudioContext) {
    return false;
  }
  try {
    ctx = ctx ?? new AudioContext();
    if (ctx.state === 'suspended') {
      void ctx.resume().catch(() => undefined);
    }
    // Build every node before rerouting the element: if node creation fails,
    // audio keeps playing directly through the element.
    const gain = ctx.createGain();
    const bandFilters = EQ_BANDS.map((band) => {
      const filter = ctx!.createBiquadFilter();
      filter.type = 'peaking';
      filter.frequency.value = band.frequency;
      filter.Q.value = 1;
      filter.gain.value = 0;
      return filter;
    });
    const nextAnalyser = ctx.createAnalyser();
    nextAnalyser.fftSize = 128;
    nextAnalyser.smoothingTimeConstant = 0.8;

    const nextSource = ctx.createMediaElementSource(getAudioElement());
    try {
      nextSource.connect(gain);
      let node: AudioNode = gain;
      for (const filter of bandFilters) {
        node.connect(filter);
        node = filter;
      }
      node.connect(nextAnalyser);
      nextAnalyser.connect(ctx.destination);
    } catch {
      // The element is already routed through the context; connect it straight
      // to the destination so a partially built chain never silences playback.
      try {
        nextSource.connect(ctx.destination);
      } catch {
        /* nothing else we can do */
      }
      directOnly = true;
      source = nextSource;
      return false;
    }

    source = nextSource;
    preamp = gain;
    filters = bandFilters;
    analyser = nextAnalyser;
    connected = true;
    applyEqSettings();
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
    void ctx.resume().catch(() => undefined);
  }
}

/**
 * Prepares the output path before `audio.play()`: builds the EQ graph when the
 * user enabled it (inside the play gesture) and resumes a suspended context
 * (e.g. after the OS interrupted it in the background).
 */
export function prepareAudioOutput(): void {
  if (usePlaybackStore.getState().eqEnabled) {
    ensureGraph();
  }
  ensureAudioRunning();
}

export function getAnalyser(): AnalyserNode | null {
  return analyser;
}

/** Introspection for tests and diagnostics (no audio handles are exposed UI-side). */
export function getAudioGraphNodes() {
  return { context: ctx, source, preamp, filters, analyser, connected, directOnly };
}

/** Test helper: releases module state between tests. */
export function resetAudioGraphForTests(): void {
  ctx = null;
  source = null;
  preamp = null;
  analyser = null;
  filters = [];
  connected = false;
  directOnly = false;
}

// Slider/toggle changes take effect immediately when the graph is alive.
usePlaybackStore.subscribe(() => applyEqSettings());
