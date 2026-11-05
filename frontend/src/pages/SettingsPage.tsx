import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  LogOut,
  LogIn,
  Bell,
  Trash2,
  RefreshCw,
  UploadCloud,
} from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import { useIsAdmin } from '@/hooks/useIsAdmin';
import {
  EQ_BANDS,
  EQ_MAX_DB,
  EQ_MIN_DB,
  usePlaybackStore,
} from '@/stores/playbackStore';
import { applyEqSettings, ensureGraph } from '@/player/audioGraph';
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
import { initials } from '@/utils/initials';
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
        checked ? 'bg-accent-strong' : 'bg-text-muted/80',
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
 * Settings: account/logout, playback, notifications, Last.fm, cover sources and
 * cache management. Theme lives only in the header toggle.
 */
export default function SettingsPage() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const logout = useAuthStore((s) => s.logout);
  const isAdmin = useIsAdmin();

  const { data: settings, isPending } = useSettings();
  const updateSettings = useUpdateSettings();

  const [confirmLogout, setConfirmLogout] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [cleared, setCleared] = useState<number | null>(null);
  const [pushBusy, setPushBusy] = useState(false);
  const [pushError, setPushError] = useState<string | null>(null);
  const [pushTesting, setPushTesting] = useState(false);
  const [pushSent, setPushSent] = useState<number | null>(null);

  const prefetch = usePlaybackStore((s) => s.prefetch);
  const setPrefetch = usePlaybackStore((s) => s.setPrefetch);
  const eqEnabled = usePlaybackStore((s) => s.eqEnabled);
  const setEqEnabled = usePlaybackStore((s) => s.setEqEnabled);
  const eqGains = usePlaybackStore((s) => s.eqGains);
  const setEqGain = usePlaybackStore((s) => s.setEqGain);
  const resetEq = usePlaybackStore((s) => s.resetEq);
  const [eqError, setEqError] = useState(false);

  const toggleEq = (enabled: boolean) => {
    if (enabled && !ensureGraph()) {
      setEqError(true);
      return;
    }
    setEqError(false);
    setEqEnabled(enabled);
    applyEqSettings();
  };

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
      <Card className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-neon-purple text-white neon-glow">
            {user?.name ? initials(user.name) : '?'}
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
          confirmLogout ? (
            <div
              role="group"
              aria-label="Confirmar cierre de sesión"
              className="flex flex-col items-end gap-1"
            >
              <p className="text-right text-xs text-text-muted">
                Se cerrará la sesión en todos los dispositivos.
              </p>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => void handleLogout()}>
                  Cerrar sesión
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirmLogout(false)}>
                  Cancelar
                </Button>
              </div>
            </div>
          ) : (
            <Button variant="ghost" size="sm" onClick={() => setConfirmLogout(true)}>
              <LogOut className="h-4 w-4" aria-hidden />
              Cerrar sesión
            </Button>
          )
        ) : (
          <Link to="/settings">
            <Button variant="secondary" size="sm" disabled>
              <LogIn className="h-4 w-4" aria-hidden />
              Iniciar sesión
            </Button>
          </Link>
        )}
      </Card>

      {/* Reproducción */}
      <Card className="flex flex-col gap-3">
        <div>
          <p className="font-semibold">Reproducción</p>
          <p className="text-xs text-text-muted">
            Ajustes locales de este dispositivo (no se guardan en la cuenta).
          </p>
        </div>

        <div className="flex items-center justify-between gap-3 border-t border-border pt-3">
          <div>
            <p className="text-sm font-semibold">Precargar la siguiente canción</p>
            <p className="text-xs text-text-muted">
              Hasta 12 MB, solo una pista y respetando el ahorro de datos. Mejora el
              cambio de canción; si falla, se reproduce en streaming normal.
            </p>
          </div>
          <Toggle checked={prefetch} onChange={setPrefetch} label="Precargar la siguiente canción" />
        </div>

        <div className="flex items-center justify-between gap-3 border-t border-border pt-3">
          <div>
            <p className="text-sm font-semibold">Ecualizador</p>
            <p className="text-xs text-text-muted">
              Desactivado por defecto (bypass). No es normalización de volumen.
            </p>
            {eqError && (
              <p className="text-xs text-danger-text">
                Este navegador no permite procesar el audio en tiempo real.
              </p>
            )}
          </div>
          <Toggle checked={eqEnabled} onChange={toggleEq} label="Ecualizador" disabled={eqError} />
        </div>

        {eqEnabled && (
          <div className="flex flex-col gap-2 border-t border-border pt-3">
            {EQ_BANDS.map((band, index) => (
              <label key={band.frequency} className="flex items-center gap-2 text-xs">
                <span className="w-14 shrink-0 text-text-muted">{band.label}</span>
                <input
                  type="range"
                  min={EQ_MIN_DB}
                  max={EQ_MAX_DB}
                  step={1}
                  value={eqGains[index] ?? 0}
                  onChange={(event) => setEqGain(index, Number(event.target.value))}
                  aria-label={`Ganancia ${band.label}`}
                  className="neon-range h-1.5 flex-1"
                />
                <span className="w-10 shrink-0 text-right tabular-nums text-text-muted">
                  {eqGains[index] ?? 0} dB
                </span>
              </label>
            ))}
            <Button variant="ghost" size="sm" onClick={resetEq} className="self-start">
              Restablecer ecualizador
            </Button>
          </div>
        )}
      </Card>

      {/* Notificaciones */}
      <Card className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Bell className="h-6 w-6 text-accent-text" aria-hidden />
          <div>
            <p className="font-semibold">Notificaciones</p>
            <p className="text-sm text-text-muted">
              {!pushSupported()
                ? 'Este navegador no soporta notificaciones push.'
                : !pushConfigured
                  ? 'Push no configurado en el servidor.'
                  : 'Avisos nativos (p. ej. escaneo completado).'}
            </p>
            {pushError && <p className="text-xs text-danger-text">{pushError}</p>}
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

      {/* Admin: upload + scanner. Both hide themselves for regular users. */}
      {isAdmin && (
        <Card className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <UploadCloud className="h-6 w-6 text-accent-text" aria-hidden />
            <div>
              <p className="font-semibold">Subir música</p>
              <p className="text-sm text-text-muted">
                Añade canciones o un ZIP; se organizan por artista y álbum.
              </p>
            </div>
          </div>
          <Link
            to="/upload"
            className="inline-flex min-h-[44px] shrink-0 items-center rounded-xl border border-accent-text px-4 text-sm font-semibold text-accent-text transition-colors hover:bg-accent-soft"
          >
            Abrir
          </Link>
        </Card>
      )}
      <ScannerCard />

      <p className="text-xs text-text-muted">
        NeonVibe v{__APP_VERSION__}. Los ajustes se guardan en la base de datos.
      </p>
    </div>
  );
}
