import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './authStore';

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ isAuthenticated: false, user: null, token: null, refreshToken: null });
  });

  it('setToken stores the token without authenticating', () => {
    useAuthStore.getState().setToken('access');

    const state = useAuthStore.getState();
    expect(state.token).toBe('access');
    // Must stay unauthenticated: fetchMe() has not confirmed the session yet.
    expect(state.isAuthenticated).toBe(false);
  });

  it('setAuth authenticates and stores both tokens', () => {
    useAuthStore.getState().setAuth({ id: 'u1', email: 'a@b.c', name: 'A' }, 'access', 'refresh');

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.user?.id).toBe('u1');
    expect(state.token).toBe('access');
    expect(state.refreshToken).toBe('refresh');
  });

  it('setTokens updates the access token and keeps the user', () => {
    useAuthStore.getState().setAuth({ id: 'u1', email: 'a@b.c', name: 'A' }, 'access', 'refresh');

    useAuthStore.getState().setTokens('new-access', 'new-refresh');

    const state = useAuthStore.getState();
    expect(state.token).toBe('new-access');
    expect(state.refreshToken).toBe('new-refresh');
    expect(state.isAuthenticated).toBe(true);
    expect(state.user?.id).toBe('u1');
  });

  it('logout clears everything', () => {
    useAuthStore.getState().setAuth({ id: 'u1', email: 'a@b.c', name: 'A' }, 'access', 'refresh');

    useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
    expect(state.token).toBeNull();
    expect(state.refreshToken).toBeNull();
  });
});
