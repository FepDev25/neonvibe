import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponse } from '@/types';

/**
 * Shared axios instance for the NeonVibe backend.
 *
 * - Attaches the JWT from `authStore` on every request.
 * - On 401, tries to refresh the access token once (rotation is supported by the
 *   backend) and retries the original request. If refresh fails, the session is
 *   cleared.
 */
export const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const AUTH_ENDPOINTS = ['/auth/google', '/auth/refresh', '/auth/logout'];

/** Auth endpoints that must never trigger the refresh/auto-logout flow. */
export function isAuthEndpoint(url: string): boolean {
  return AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint));
}

/**
 * Whether a failed response means the session is gone and must be cleared.
 *
 * Only login/refresh failures are exempt (a bad credential is not an expired
 * session). `/auth/me` returning 401 DOES mean the session is invalid, so it
 * must log out — excluding all of `/auth/` used to leave a ghost session.
 */
export function shouldAutoLogout(status: number | undefined, url: string): boolean {
  if (status !== 401) {
    return false;
  }
  return !url.startsWith('/auth/google') && !url.startsWith('/auth/refresh');
}

// In-flight refresh shared by concurrent 401s and by the player's media-token
// check, so only one rotation ever happens at a time.
let refreshPromise: Promise<string> | null = null;

async function performRefresh(): Promise<string> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  // Bare axios (no interceptors) to avoid recursing into this same handler.
  const { data } = await axios.post<AuthResponse>('/api/v1/auth/refresh', {
    refresh_token: refreshToken,
  });
  useAuthStore.getState().setTokens(data.access_token, data.refresh_token);
  return data.access_token;
}

/**
 * Refreshes the access token, sharing one in-flight request between every
 * caller. Exported for the player: `<audio src>` requests cannot go through the
 * axios interceptor, so the player checks the JWT expiry before using a stream
 * URL and refreshes proactively.
 */
export function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/** Reads the `exp` claim (seconds) from a JWT without verifying it. */
export function getTokenExpirySeconds(token: string | null | undefined): number | null {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null; // opaque token (e.g. dev mocks): no expiry information
  }
  try {
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = payload.padEnd(Math.ceil(payload.length / 4) * 4, '=');
    const json = JSON.parse(atob(padded)) as { exp?: unknown };
    return typeof json.exp === 'number' ? json.exp : null;
  } catch {
    return null;
  }
}

/**
 * True when the access token is missing, malformed with an expiry, or expires
 * within `skewMs`. Opaque tokens are treated as non-expiring: the backend will
 * reject them if needed and the media error path handles it.
 */
export function isAccessTokenExpiring(
  token: string | null | undefined,
  skewMs = 60_000,
  nowMs = Date.now(),
): boolean {
  if (!token) {
    return true;
  }
  const exp = getTokenExpirySeconds(token);
  if (exp == null) {
    return false;
  }
  return exp * 1000 - nowMs <= skewMs;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const url = error.config?.url ?? '';
    const status = error.response?.status;
    const original = error.config as RetriableConfig | undefined;

    if (
      status === 401 &&
      original &&
      !original._retry &&
      !isAuthEndpoint(url) &&
      useAuthStore.getState().refreshToken
    ) {
      original._retry = true;
      try {
        const token = await refreshAccessToken();
        original.headers = {
          ...original.headers,
          Authorization: `Bearer ${token}`,
        } as typeof original.headers;
        return apiClient(original);
      } catch {
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }
    }

    if (shouldAutoLogout(status, url)) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  },
);
