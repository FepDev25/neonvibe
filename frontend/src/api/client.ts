import axios, { AxiosError } from 'axios';
import { useAuthStore } from '@/stores/authStore';

/**
 * Shared axios instance for the NeonVibe backend.
 *
 * - Attaches the JWT from `authStore` on every request.
 * - On 401, clears the session (a next app mount re-establishes it in dev).
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

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const url = error.config?.url ?? '';
    // Keep auth endpoints out of the auto-logout: a failed refresh/login is not
    // a session problem. Only clear the session for real authenticated routes.
    if (error.response?.status === 401 && !url.startsWith('/auth/')) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  },
);
