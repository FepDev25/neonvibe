import { describe, expect, it } from 'vitest';
import { isAuthEndpoint, shouldAutoLogout } from './client';

describe('isAuthEndpoint', () => {
  it('flags login/refresh/logout as auth endpoints', () => {
    expect(isAuthEndpoint('/auth/google')).toBe(true);
    expect(isAuthEndpoint('/auth/refresh')).toBe(true);
    expect(isAuthEndpoint('/auth/logout')).toBe(true);
  });

  it('does not flag authenticated routes', () => {
    expect(isAuthEndpoint('/auth/me')).toBe(false);
    expect(isAuthEndpoint('/tracks')).toBe(false);
  });
});

describe('shouldAutoLogout', () => {
  it('clears the session on 401 from authenticated routes', () => {
    expect(shouldAutoLogout(401, '/auth/me')).toBe(true);
    expect(shouldAutoLogout(401, '/tracks')).toBe(true);
  });

  it('does not clear the session on login/refresh failures', () => {
    expect(shouldAutoLogout(401, '/auth/google')).toBe(false);
    expect(shouldAutoLogout(401, '/auth/refresh')).toBe(false);
  });

  it('ignores non-401 statuses', () => {
    expect(shouldAutoLogout(500, '/tracks')).toBe(false);
    expect(shouldAutoLogout(undefined, '/tracks')).toBe(false);
  });
});
