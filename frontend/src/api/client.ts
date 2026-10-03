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

// In-flight refresh shared by concurrent 401s so only one refresh happens.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
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
        const token = await (refreshPromise ??= refreshAccessToken().finally(() => {
          refreshPromise = null;
        }));
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
