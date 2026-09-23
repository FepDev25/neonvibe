import { apiClient } from './client';
import type { AuthResponse, User } from '@/types';

/**
 * Backend auth endpoints. In dev the backend accepts any non-blank id_token
 * as a mock (Google validation disabled); in prod this exchanges a real
 * Google id_token.
 */
export async function loginWithGoogle(idToken: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/google', {
    id_token: idToken,
  });
  return data;
}

export async function fetchMe(): Promise<User> {
  const { data } = await apiClient.get<User>('/auth/me');
  return data;
}
