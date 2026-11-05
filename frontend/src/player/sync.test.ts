import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const h = vi.hoisted(() => {
  const state = {
    config: null as null | Record<string, unknown> & {
      webSocketFactory: () => unknown;
      beforeConnect: (client: { connectHeaders: Record<string, string> }) => Promise<void>;
      onConnect: () => void;
      onWebSocketClose: () => void;
      onStompError: () => void;
    },
  };

  class FakeClient {
    connected = false;
    connectHeaders: Record<string, string> = {};
    subscribe = vi.fn();
    publish = vi.fn();
    activate = vi.fn(() => {
      this.connected = true;
    });
    deactivate = vi.fn(() => {
      this.connected = false;
    });

    constructor(config: typeof state.config) {
      state.config = config;
    }
  }

  return {
    state,
    FakeClient,
    sockjs: vi.fn(),
    expiring: vi.fn(() => false),
    refresh: vi.fn(async () => 'fresh-token'),
  };
});

vi.mock('@stomp/stompjs', () => ({
  Client: h.FakeClient,
  ReconnectionTimeMode: { EXPONENTIAL: 'EXPONENTIAL' },
}));
vi.mock('sockjs-client', () => ({ default: h.sockjs }));
vi.mock('@/api/client', () => ({
  isAccessTokenExpiring: h.expiring,
  refreshAccessToken: h.refresh,
}));

import { connectSync, disconnectSync } from './sync';
import { useAuthStore } from '@/stores/authStore';
import { useSyncStore } from '@/stores/syncStore';

describe('player sync connection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.state.config = null;
    h.sockjs.mockReturnValue({});
    h.expiring.mockReturnValue(false);
    useAuthStore.setState({
      isAuthenticated: true,
      user: { id: 'user-1', email: 'u@example.com', name: 'User' },
      token: 'tok',
      refreshToken: 'refresh',
    });
    useSyncStore.getState().reset();
  });

  afterEach(() => {
    disconnectSync();
  });

  it('keeps the JWT out of the WebSocket URL', () => {
    connectSync();

    h.state.config?.webSocketFactory();

    expect(h.sockjs).toHaveBeenCalledWith('/ws');
    expect(h.sockjs.mock.calls.flat().join(' ')).not.toContain('token');
  });

  it('sends the token in STOMP CONNECT headers and refreshes when needed', async () => {
    h.expiring.mockReturnValue(true);
    connectSync();

    const target = { connectHeaders: {} as Record<string, string> };
    await h.state.config!.beforeConnect(target);

    expect(h.refresh).toHaveBeenCalledTimes(1);
    expect(target.connectHeaders.Authorization).toBe('Bearer fresh-token');
  });

  it('uses exponential backoff and exposes reconnection state', () => {
    connectSync();

    expect(h.state.config?.reconnectTimeMode).toBe('EXPONENTIAL');
    expect(h.state.config?.maxReconnectDelay).toBe(30_000);
    expect(useSyncStore.getState().status).toBe('connecting');

    h.state.config?.onConnect();
    expect(useSyncStore.getState().status).toBe('connected');

    h.state.config?.onWebSocketClose();
    expect(useSyncStore.getState().status).toBe('reconnecting');

    disconnectSync();
    expect(useSyncStore.getState().status).toBe('idle');
  });
});
