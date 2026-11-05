import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/player/audioElement', () => ({ getAudioElement: () => ({}) }));

import {
  applyEqSettings,
  ensureAudioRunning,
  ensureGraph,
  getAudioGraphNodes,
  prepareAudioOutput,
  resetAudioGraphForTests,
} from './audioGraph';
import { DEFAULT_EQ_GAINS, usePlaybackStore } from '@/stores/playbackStore';

class FakeNode {
  connections: FakeNode[] = [];
  connect(node: FakeNode): FakeNode {
    this.connections.push(node);
    return node;
  }
}

class FakeGain extends FakeNode {
  gain = { value: 1 };
}

class FakeFilter extends FakeNode {
  type = '';
  frequency = { value: 0 };
  Q = { value: 0 };
  gain = { value: 0 };
  failConnect = false;
  override connect(node: FakeNode): FakeNode {
    if (this.failConnect) {
      throw new Error('connect failed');
    }
    return super.connect(node);
  }
}

class FakeAnalyser extends FakeNode {
  fftSize = 0;
  smoothingTimeConstant = 0;
}

class FakeSource extends FakeNode {}

class FakeContext {
  static instances: FakeContext[] = [];
  state: AudioContextState = 'running';
  destination = new FakeNode();
  gains: FakeGain[] = [];
  filters: FakeFilter[] = [];
  analysers: FakeAnalyser[] = [];
  sources: FakeSource[] = [];
  resume = vi.fn(async () => {
    this.state = 'running';
  });
  failSource = false;
  failFilterConnect = false;

  constructor() {
    FakeContext.instances.push(this);
  }

  createGain(): FakeGain {
    const gain = new FakeGain();
    this.gains.push(gain);
    return gain;
  }

  createBiquadFilter(): FakeFilter {
    const filter = new FakeFilter();
    filter.failConnect = this.failFilterConnect;
    this.filters.push(filter);
    return filter;
  }

  createAnalyser(): FakeAnalyser {
    const analyser = new FakeAnalyser();
    this.analysers.push(analyser);
    return analyser;
  }

  createMediaElementSource(): FakeSource {
    if (this.failSource) {
      throw new Error('source already connected');
    }
    const source = new FakeSource();
    this.sources.push(source);
    return source;
  }
}

const originalAudioContext = window.AudioContext;

describe('audioGraph', () => {
  beforeEach(() => {
    FakeContext.instances = [];
    resetAudioGraphForTests();
    usePlaybackStore.setState({
      prefetch: true,
      eqEnabled: false,
      eqGains: [...DEFAULT_EQ_GAINS],
    });
    (window as unknown as { AudioContext: unknown }).AudioContext = FakeContext;
  });

  afterAll(() => {
    (window as unknown as { AudioContext: unknown }).AudioContext = originalAudioContext;
  });

  it('builds source -> preamp -> 5 filters -> analyser -> destination once', () => {
    expect(ensureGraph()).toBe(true);
    const nodes = getAudioGraphNodes();
    const asFake = (node: unknown) => node as FakeNode;
    expect(nodes.connected).toBe(true);
    expect(nodes.filters).toHaveLength(5);
    expect(asFake(nodes.source).connections[0]).toBe(nodes.preamp);
    expect(asFake(nodes.preamp).connections[0]).toBe(nodes.filters[0]);
    for (let i = 0; i < nodes.filters.length - 1; i++) {
      expect(asFake(nodes.filters[i]).connections[0]).toBe(nodes.filters[i + 1]);
    }
    expect(asFake(nodes.filters[4]).connections[0]).toBe(nodes.analyser);
    expect(asFake(nodes.analyser).connections[0]).toBe(nodes.context?.destination);

    // Second call reuses the same graph (the source node cannot be recreated).
    expect(ensureGraph()).toBe(true);
    expect(FakeContext.instances).toHaveLength(1);
  });

  it('is a flat bypass while the EQ is disabled and applies gains when enabled', () => {
    ensureGraph();
    const { filters } = getAudioGraphNodes();

    expect(filters.map((f) => f.gain.value)).toEqual([0, 0, 0, 0, 0]);

    usePlaybackStore.setState({ eqEnabled: true, eqGains: [6, -3, 0, 4, 12] });
    applyEqSettings();

    expect(filters.map((f) => f.gain.value)).toEqual([6, -3, 0, 4, 12]);

    usePlaybackStore.setState({ eqEnabled: false });
    applyEqSettings();
    expect(filters.map((f) => f.gain.value)).toEqual([0, 0, 0, 0, 0]);
  });

  it('clamps out-of-range gains', () => {
    ensureGraph();
    usePlaybackStore.setState({ eqEnabled: true, eqGains: [99, -99, 0, 0, 0] });
    applyEqSettings();
    expect(getAudioGraphNodes().filters.map((f) => f.gain.value)).toEqual([12, -12, 0, 0, 0]);
  });

  it('resumes a suspended context when playback needs it', () => {
    ensureGraph();
    const nodes = getAudioGraphNodes();
    if (nodes.context) {
      (nodes.context as unknown as { state: AudioContextState }).state = 'suspended';
    }
    ensureAudioRunning();
    expect(nodes.context?.resume).toHaveBeenCalled();
  });

  it('returns false when the platform has no AudioContext', () => {
    (window as unknown as { AudioContext: unknown }).AudioContext = undefined;
    expect(ensureGraph()).toBe(false);
  });

  it('returns false without silencing audio when the source cannot be created', () => {
    (window as unknown as { AudioContext: unknown }).AudioContext = class extends FakeContext {
      constructor() {
        super();
        this.failSource = true;
      }
    };
    expect(ensureGraph()).toBe(false);
    expect(getAudioGraphNodes().connected).toBe(false);
  });

  it('falls back to a direct destination connection if the chain fails', () => {
    (window as unknown as { AudioContext: unknown }).AudioContext = class extends FakeContext {
      constructor() {
        super();
        this.failFilterConnect = true;
      }
    };
    expect(ensureGraph()).toBe(false);
    const nodes = getAudioGraphNodes();
    const asFake = (node: unknown) => node as FakeNode;
    expect(nodes.directOnly).toBe(true);
    expect(asFake(nodes.source).connections).toContain(nodes.context?.destination);
  });

  it('builds the graph before play only when the EQ is enabled', () => {
    prepareAudioOutput();
    expect(FakeContext.instances).toHaveLength(0);

    usePlaybackStore.setState({ eqEnabled: true });
    prepareAudioOutput();
    expect(FakeContext.instances).toHaveLength(1);
    expect(getAudioGraphNodes().connected).toBe(true);
  });
});
