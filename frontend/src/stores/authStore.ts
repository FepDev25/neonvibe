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
  refreshToken: string | null;
  /** Sets a user + tokens after a successful login. */
  setAuth: (user: AuthUser, token: string, refreshToken?: string) => void;
  /** Updates the tokens after a refresh, keeping the current user. */
  setTokens: (accessToken: string, refreshToken?: string) => void;
  /**
   * Stores the access token only, WITHOUT marking the session authenticated.
   * Needed during login: fetchMe() (GET /auth/me) runs before setAuth(), so the
   * request interceptor would send no Authorization header and the backend would
   * 401. Crucially, this must not set `isAuthenticated`: if fetchMe() fails the
   * session stays unauthenticated and the login error is shown instead of a
   * "ghost" session.
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
    (set, get) => ({
      isAuthenticated: false,
      user: null,
      token: null,
      refreshToken: null,
      setAuth: (user, token, refreshToken) =>
        set({ isAuthenticated: true, user, token, refreshToken: refreshToken ?? null }),
      setTokens: (accessToken, refreshToken) =>
        set({ token: accessToken, refreshToken: refreshToken ?? get().refreshToken }),
      setToken: (token) => set({ token }),
      logout: () => set({ isAuthenticated: false, user: null, token: null, refreshToken: null }),
    }),
    { name: 'neonvibe-auth' },
  ),
);
