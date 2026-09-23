import { useEffect, useRef, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Music2, Loader2, AlertTriangle } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import { fetchMe, loginWithGoogle } from '@/api/auth';

const GIS_SCRIPT = 'https://accounts.google.com/gsi/client';

/**
 * Google login (production). Loads Google Identity Services, renders the sign-in
 * button and exchanges the returned id_token for a NeonVibe session.
 *
 * DEV uses the automatic mock bootstrap instead; this page is only reachable in
 * production when there is no valid session.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const setToken = useAuthStore((s) => s.setToken);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const buttonRef = useRef<HTMLDivElement | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [scriptLoaded, setScriptLoaded] = useState(false);

  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/', { replace: true });
      return;
    }
    if (!clientId) {
      return; // config missing: show the setup notice
    }
    const render = () => {
      if (buttonRef.current && window.google?.accounts?.id) {
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: async (response) => {
            setBusy(true);
            setError(null);
            try {
              const auth = await loginWithGoogle(response.credential);
              // Store the token first so the request interceptor attaches the
              // Authorization header when fetchMe() runs (setAuth, which also
              // stores the token, comes after fetchMe).
              setToken(auth.access_token);
              const me = await fetchMe();
              setAuth(me, auth.access_token);
              navigate('/', { replace: true });
            } catch (err) {
              // 403 = cuenta válida pero fuera de la allowlist del servidor
              // (ALLOWED_EMAILS); distinguirlo evita reintentos inútiles.
              const status = (err as { response?: { status?: number } })?.response?.status;
              setError(
                status === 403
                  ? 'Esta cuenta no está autorizada en este servidor.'
                  : 'No se pudo iniciar sesión con Google. Inténtalo de nuevo.',
              );
              console.warn('[login] failed', err);
            } finally {
              setBusy(false);
            }
          },
        });
        window.google.accounts.id.renderButton(buttonRef.current, {
          theme: 'filled_black',
          size: 'large',
          width: 280,
        });
      }
    };

    if (window.google?.accounts?.id) {
      setScriptLoaded(true);
      render();
      return;
    }
    const script = document.createElement('script');
    script.src = GIS_SCRIPT;
    script.async = true;
    script.defer = true;
    script.onload = () => {
      setScriptLoaded(true);
      render();
    };
    document.head.appendChild(script);
    return () => {
      script.remove();
    };
  }, [clientId, isAuthenticated, navigate, setAuth, setToken]);

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-bg px-4 text-center">
      <div className="flex flex-col items-center gap-3">
        <div className="neon-border flex h-20 w-20 items-center justify-center rounded-3xl bg-surface">
          <Music2 className="h-10 w-10 text-neon-cyan" aria-hidden />
        </div>
        <h1 className="neon-text text-4xl font-extrabold tracking-tight">NeonVibe</h1>
        <p className="max-w-sm text-sm text-text-muted">
          Tu servidor de música personal. Inicia sesión para continuar.
        </p>
      </div>

      {busy && (
        <div className="flex items-center gap-2 text-sm text-text-muted">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          Verificando…
        </div>
      )}

      {error && (
        <p className="flex items-center gap-2 rounded-xl border border-neon-pink/40 bg-neon-pink/10 px-4 py-2 text-sm text-neon-pink">
          <AlertTriangle className="h-4 w-4" aria-hidden />
          {error}
        </p>
      )}

      {!clientId ? (
        <div className="max-w-md rounded-2xl border border-border bg-surface p-5 text-left">
          <p className="text-sm font-semibold text-text">Falta VITE_GOOGLE_CLIENT_ID</p>
          <p className="mt-1 text-xs leading-relaxed text-text-muted">
            Reconstruye el frontend con la variable{' '}
            <code className="rounded bg-surface-alt px-1">VITE_GOOGLE_CLIENT_ID</code>{' '}
            apuntando a tu Client ID de Google Cloud Console (OAuth 2.0 → Web).
            Consulta <span className="text-neon-cyan">docs/DEPLOY.md</span>.
          </p>
        </div>
      ) : !scriptLoaded ? (
        <p className="text-sm text-text-muted">Cargando Google Sign-In…</p>
      ) : (
        <div ref={buttonRef} />
      )}

      <Link to="/" className="text-xs text-text-muted hover:text-neon-cyan">
        Volver al inicio
      </Link>
    </div>
  );
}
