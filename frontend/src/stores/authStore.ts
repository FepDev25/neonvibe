import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  /** snake_case: matches the backend `User` DTO (`avatar_url`). */
  avatar_url?: string;
}

interface AuthState {
  isAuthenticated: boolean;
  user: AuthUser | null;
  token: string | null;
  /** Sets a user + token after a successful login (backend integration in Fase 1). */
  setAuth: (user: AuthUser, token: string) => void;
  /**
   * Stores the access token only, without user data. Needed during login:
   * fetchMe() (GET /auth/me) runs before setAuth(), so the request interceptor
   * would send no Authorization header and the backend would 401. Setting the
   * token first lets fetchMe() authenticate, then setAuth() fills the user.
   */
  setToken: (token: string) => void;
  /** Clears all session data (logout). */
  logout: () => void;
}

/**
 * Placeholder auth store. Backed by localStorage so a toggled state survives
 * reload, but no real network auth happens yet — that lands with the backend
 * OAuth/JWT flow. Kept deliberately small.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isAuthenticated: false,
      user: null,
      token: null,
      setAuth: (user, token) =>
        set({ isAuthenticated: true, user, token }),
      setToken: (token) => set({ token, isAuthenticated: true }),
      logout: () => set({ isAuthenticated: false, user: null, token: null }),
    }),
    { name: 'neonvibe-auth' },
  ),
);
