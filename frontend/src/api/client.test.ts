import { describe, expect, it } from 'vitest';
import {
  getTokenExpirySeconds,
  isAccessTokenExpiring,
  isAuthEndpoint,
  shouldAutoLogout,
} from './client';

function fakeJwt(payload: Record<string, unknown>): string {
  const encode = (value: unknown) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

describe('isAccessTokenExpiring', () => {
  const now = 1_700_000_000_000;

  it('treats a missing token as needing authentication', () => {
    expect(isAccessTokenExpiring(null)).toBe(true);
  });

  it('treats opaque (non-JWT) tokens as non-expiring', () => {
    expect(getTokenExpirySeconds('dev-mock-token')).toBeNull();
    expect(isAccessTokenExpiring('dev-mock-token')).toBe(false);
  });

  it('reads the exp claim from a JWT', () => {
    const token = fakeJwt({ exp: Math.floor(now / 1000) + 3600 });
    expect(getTokenExpirySeconds(token)).toBe(Math.floor(now / 1000) + 3600);
  });

  it('flags a token inside the skew window', () => {
    const token = fakeJwt({ exp: Math.floor(now / 1000) + 30 });
    expect(isAccessTokenExpiring(token, 60_000, now)).toBe(true);
  });

  it('does not flag a token with plenty of time left', () => {
    const token = fakeJwt({ exp: Math.floor(now / 1000) + 3600 });
    expect(isAccessTokenExpiring(token, 60_000, now)).toBe(false);
  });

  it('ignores malformed payloads safely', () => {
    expect(getTokenExpirySeconds('a.%%%.c')).toBeNull();
    expect(isAccessTokenExpiring(fakeJwt({}), 60_000, now)).toBe(false);
  });
});


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
