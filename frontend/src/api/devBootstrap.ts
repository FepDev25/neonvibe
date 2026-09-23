import { useAuthStore } from '@/stores/authStore';
import { fetchMe, loginWithGoogle } from './auth';

let bootstrapPromise: Promise<void> | null = null;

/**
 * Development-only session bootstrap.
 *
 * The dev backend disables Google validation and accepts any non-blank
 * id_token as a mock. This logs in automatically so the library pages can be
 * validated end-to-end without a login UI. It never runs in production
 * (`import.meta.env.PROD`), where the real OAuth flow is still pending.
 *
 * Guarded by a module-level promise so React StrictMode (double mount) only
 * triggers it once.
 */
export function ensureDevSession(): Promise<void> {
  if (!import.meta.env.DEV) {
    return Promise.resolve();
  }
  if (bootstrapPromise) {
    return bootstrapPromise;
  }
  bootstrapPromise = (async () => {
    const { token } = useAuthStore.getState();
    if (token) {
      return;
    }
    try {
      const auth = await loginWithGoogle('dev-bootstrap');
      const me = await fetchMe();
      useAuthStore.getState().setAuth(me, auth.access_token);
    } catch (err) {
      console.warn('[dev] session bootstrap failed', err);
    }
  })();
  return bootstrapPromise;
}
