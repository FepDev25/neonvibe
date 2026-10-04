import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Sun,
  Moon,
  LogOut,
  LogIn,
  Bell,
  Trash2,
  RefreshCw,
} from 'lucide-react';
import { useThemeStore } from '@/stores/themeStore';
import { useAuthStore } from '@/stores/authStore';
import { useSettings, useUpdateSettings } from '@/hooks/useSettings';
import {
  clearCache,
  disconnectLastFm,
  getLastFmAuthUrl,
} from '@/api/settings';
import { getPushKey, sendTestPush } from '@/api/push';
import { disablePush, enablePush, pushSupported } from '@/push/notifications';
import { logout as revokeSession } from '@/api/auth';
import Card from '@/components/Card';
import Button from '@/components/Button';
import ScannerCard from '@/components/ScannerCard';
import Skeleton from '@/components/Skeleton';
import { cn } from '@/utils/cn';

function Toggle({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative h-7 w-12 shrink-0 rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        checked ? 'bg-neon-cyan' : 'bg-surface-alt',
      )}
    >
      <span
        className={cn(
          'absolute top-0.5 h-6 w-6 rounded-full bg-white transition-all',
          checked ? 'left-[22px]' : 'left-0.5',
        )}
      />
    </button>
  );
}

/**
 * Settings: account, theme (persisted to DB), notifications, Last.fm scrobbling,
 * cover sources and cache management.
 */
export default function SettingsPage() {
  const theme = useThemeStore((s) => s.theme);
  const setTheme = useThemeStore((s) => s.setTheme);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const logout = useAuthStore((s) => s.logout);

  const { data: settings, isPending } = useSettings();
  const updateSettings = useUpdateSettings();

  const [connecting, setConnecting] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [cleared, setCleared] = useState<number | null>(null);
  const [pushBusy, setPushBusy] = useState(false);
  const [pushError, setPushError] = useState<string | null>(null);
  const [pushTesting, setPushTesting] = useState(false);
  const [pushSent, setPushSent] = useState<number | null>(null);

  const pushKey = useQuery({ queryKey: ['pushKey'], queryFn: getPushKey });
  const pushConfigured = pushKey.data?.configured ?? false;
  const pushAvailable = pushSupported() && pushConfigured;

  const set = (payload: Parameters<typeof updateSettings.mutate>[0]) =>
    void updateSettings.mutate(payload);

  const handleToggleNotifications = async (enabled: boolean) => {
    setPushBusy(true);
    setPushError(null);
    try {
      if (enabled) {
        const ok = await enablePush();
        if (!ok) {
          setPushError('No se pudo activar: permiso denegado o push no disponible.');
          return;
        }
        set({ notifications_enabled: true });
      } else {
        await disablePush();
        set({ notifications_enabled: false });
      }
    } finally {
      setPushBusy(false);
    }
  };

  const handleTestPush = async () => {
    setPushTesting(true);
    setPushSent(null);
    try {
      const { sent } = await sendTestPush();
      setPushSent(sent);
    } catch {
      setPushSent(0);
    } finally {
      setPushTesting(false);
    }
  };

  const connectLastFm = async () => {
    setConnecting(true);
    try {
      const { url, configured } = await getLastFmAuthUrl();
      if (configured && url) {
        window.location.href = url;
      }
    } catch (err) {
      // e.g. 503 when Last.fm is not configured; avoid an unhandled rejection.
      console.warn('[lastfm] could not start auth', err);
    } finally {
      setConnecting(false);
    }
  };

  const handleLogout = async () => {
    // Revoke the server-side refresh token, then clear local state.
    await revokeSession().catch(() => undefined);
    logout();
  };

  const handleDisconnect = async () => {
    setDisconnecting(true);
    await disconnectLastFm().catch(() => undefined);
    updateSettings.mutate({});
    setDisconnecting(false);
  };

  const handleClearCache = async () => {
    setClearing(true);
    const result = await clearCache().catch(() => ({ cleared: 0 }));
    setCleared(result.cleared);
    setClearing(false);
  };

  if (isPending || !settings) {
    return (
      <div className="flex max-w-xl flex-col gap-4">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-24 w-full rounded-2xl" />
        <Skeleton className="h-24 w-full rounded-2xl" />
      </div>
    );
  }

  const lastfmConnected = settings.lastfm.connected;

  return (
    <div className="flex max-w-xl flex-col gap-4">
      <h1 className="neon-text text-2xl font-bold">Ajustes</h1>

      {/* Cuenta */}
      <Card className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-neon-purple text-white neon-glow">
            {user?.name ? user.name[0].toUpperCase() : '?'}
          </div>
          <div>
            <p className="font-semibold">
              {isAuthenticated && user ? user.name : 'Sin sesión'}
            </p>
            <p className="text-sm text-text-muted">
              {isAuthenticated && user ? user.email : 'Cuenta (OAuth Google pendiente en prod)'}
            </p>
          </div>
        </div>
        {isAuthenticated ? (
          <Button variant="ghost" size="sm" onClick={() => void handleLogout()}>
            <LogOut className="h-4 w-4" aria-hidden />
            Cerrar
          </Button>
        ) : (
          <Link to="/settings">
            <Button variant="secondary" size="sm" disabled>
              <LogIn className="h-4 w-4" aria-hidden />
              Iniciar sesión
            </Button>
          </Link>
        )}
      </Card>

      {/* Tema */}
      <Card className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {theme === 'dark' ? (
            <Moon className="h-6 w-6 text-neon-cyan" aria-hidden />
          ) : (
            <Sun className="h-6 w-6 text-neon-yellow" aria-hidden />
          )}
          <div>
            <p className="font-semibold">Tema</p>
            <p className="text-sm text-text-muted">
              {theme === 'dark' ? 'Oscuro (neón, por defecto)' : 'Claro'}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button
            variant={theme === 'dark' ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => {
              if (theme !== 'dark') {
                setTheme('dark');
                set({ theme: 'dark' });
              }
            }}
          >
            Oscuro
          </Button>
          <Button
            variant={theme === 'light' ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => {
              if (theme !== 'light') {
                setTheme('light');
                set({ theme: 'light' });
              }
            }}
          >
            Claro
          </Button>
        </div>
      </Card>

      {/* Notificaciones */}
      <Card className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Bell className="h-6 w-6 text-neon-cyan" aria-hidden />
          <div>
            <p className="font-semibold">Notificaciones</p>
            <p className="text-sm text-text-muted">
              {!pushSupported()
                ? 'Este navegador no soporta notificaciones push.'
                : !pushConfigured
                  ? 'Push no configurado en el servidor.'
                  : 'Avisos nativos (p. ej. escaneo completado).'}
            </p>
            {pushError && <p className="text-xs text-neon-pink">{pushError}</p>}
            {pushSent != null && (
              <p className="text-xs text-text-muted">
                {pushSent > 0
                  ? `Enviada a ${pushSent} dispositivo${pushSent === 1 ? '' : 's'}.`
                  : 'No se pudo enviar.'}
              </p>
            )}
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {pushAvailable && settings.notifications_enabled && (
            <Button variant="ghost" size="sm" onClick={() => void handleTestPush()} disabled={pushTesting}>
              {pushTesting ? 'Enviando…' : 'Probar'}
            </Button>
          )}
          <Toggle
            checked={settings.notifications_enabled}
            onChange={(v) => void handleToggleNotifications(v)}
            label="Notificaciones"
            disabled={!pushAvailable || pushBusy}
          />
        </div>
      </Card>

      {/* Last.fm scrobbling */}
      <Card className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="flex h-6 w-6 items-center justify-center rounded bg-text-muted text-[10px] font-black text-bg">
              fm
            </span>
            <div>
              <p className="font-semibold">Last.fm scrobbling</p>
              <p className="text-sm text-text-muted">
                {lastfmConnected
                  ? `Conectado: ${settings.lastfm.username}`
                  : 'Registra lo que escuchas en tu perfil de Last.fm'}
              </p>
            </div>
          </div>
          {lastfmConnected ? (
            <Button variant="ghost" size="sm" onClick={handleDisconnect} disabled={disconnecting}>
              <LogOut className="h-4 w-4" aria-hidden />
              Desconectar
            </Button>
          ) : (
            <Button variant="secondary" size="sm" onClick={connectLastFm} disabled={connecting}>
              <RefreshCw className="h-4 w-4" aria-hidden />
              Conectar
            </Button>
          )}
        </div>

        <div className="flex items-center justify-between gap-3 border-t border-border pt-3">
          <div>
            <p className="text-sm font-semibold">Scrobble automático</p>
            <p className="text-xs text-text-muted">Al completar o escuchar ≥50% de un track</p>
          </div>
          <Toggle
            checked={settings.scrobble_enabled}
            onChange={(v) => set({ scrobble_enabled: v })}
            label="Scrobble automático"
          />
        </div>
      </Card>

      {/* Covers sources */}
      <Card className="flex flex-col gap-3">
        <p className="font-semibold">Fuentes de carátulas</p>
        {(['iTunes', 'MusicBrainz', 'LastFm'] as const).map((source) => (
          <div key={source} className="flex items-center justify-between gap-3">
            <p className="text-sm text-text-muted">{source}</p>
            <Toggle
              checked={settings.cover_sources[source] ?? true}
              onChange={(v) => set({ cover_sources: { [source]: v } })}
              label={`Carátulas de ${source}`}
            />
          </div>
        ))}
      </Card>

      {/* Cache */}
      <Card className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Trash2 className="h-6 w-6 text-neon-pink" aria-hidden />
          <div>
            <p className="font-semibold">Cache de carátulas y letras</p>
            <p className="text-sm text-text-muted">
              {cleared != null ? `Se eliminaron ${cleared} archivos.` : 'Libera espacio en disco'}
            </p>
          </div>
        </div>
        <Button variant="ghost" size="sm" onClick={handleClearCache} disabled={clearing}>
          <Trash2 className="h-4 w-4" aria-hidden />
          Limpiar
        </Button>
      </Card>

      {/* Scanner (admin only; hidden for non-admins) */}
      <ScannerCard />

      <p className="text-xs text-text-muted">
        NeonVibe v0.1 — preproducción. Los ajustes se guardan en la base de datos.
      </p>
    </div>
  );
}
