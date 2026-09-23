import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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
import Card from '@/components/Card';
import Button from '@/components/Button';
import Skeleton from '@/components/Skeleton';
import { cn } from '@/utils/cn';

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative h-7 w-12 shrink-0 rounded-full transition-colors',
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

  // Apply the persisted theme on load (settings win over localStorage).
  useEffect(() => {
    if (settings?.theme && settings.theme !== theme) {
      setTheme(settings.theme);
    }
  }, [settings, theme, setTheme]);

  const set = (payload: Parameters<typeof updateSettings.mutate>[0]) =>
    void updateSettings.mutate(payload);

  const connectLastFm = async () => {
    setConnecting(true);
    try {
      const { url, configured } = await getLastFmAuthUrl();
      if (configured && url) {
        window.location.href = url;
      }
    } finally {
      setConnecting(false);
    }
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
          <Button variant="ghost" size="sm" onClick={logout}>
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
              setTheme('dark');
              set({ theme: 'dark' });
            }}
          >
            Oscuro
          </Button>
          <Button
            variant={theme === 'light' ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => {
              setTheme('light');
              set({ theme: 'light' });
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
            <p className="text-sm text-text-muted">Preferencia guardada (push nativo futuro)</p>
          </div>
        </div>
        <Toggle
          checked={settings.notifications_enabled}
          onChange={(v) => set({ notifications_enabled: v })}
          label="Notificaciones"
        />
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

      <p className="text-xs text-text-muted">
        NeonVibe v0.1 — preproducción. Los ajustes se guardan en la base de datos.
      </p>
    </div>
  );
}
