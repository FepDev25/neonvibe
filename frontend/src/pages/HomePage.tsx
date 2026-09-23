import { Link } from 'react-router-dom';
import { Music2, Library, Sparkles, ListMusic, Heart } from 'lucide-react';
import Button from '@/components/Button';
import Card from '@/components/Card';

/**
 * Landing / welcome screen with neon branding. Placeholder content until the
 * library (Fase 5) and player (Fase 7) are wired up.
 */
export default function HomePage() {
  return (
    <div className="flex flex-col items-center gap-8 py-8 text-center">
      <div className="flex flex-col items-center gap-3">
        <div className="neon-border flex h-20 w-20 items-center justify-center rounded-3xl bg-surface">
          <Music2 className="h-10 w-10 text-neon-cyan" aria-hidden />
        </div>
        <h1 className="neon-text text-4xl font-extrabold tracking-tight sm:text-5xl">
          NeonVibe
        </h1>
        <p className="max-w-md text-text-muted">
          Tu servidor de música personal. Escucha tu biblioteca con una estética
          cyberpunk, en cualquier dispositivo.
        </p>
      </div>

      <div className="grid w-full max-w-2xl grid-cols-1 gap-4 sm:grid-cols-3">
        <Card className="flex flex-col items-center gap-2 text-center">
          <Library className="h-8 w-8 text-neon-pink" aria-hidden />
          <h2 className="font-semibold">Tu biblioteca</h2>
          <p className="text-sm text-text-muted">
            Álbumes, artistas y canciones de tu colección.
          </p>
        </Card>
        <Card className="flex flex-col items-center gap-2 text-center">
          <Sparkles className="h-8 w-8 text-neon-yellow" aria-hidden />
          <h2 className="font-semibold">Tema neón</h2>
          <p className="text-sm text-text-muted">
            Oscuro por defecto, claro si lo prefieres.
          </p>
        </Card>
        <Card className="flex flex-col items-center gap-2 text-center">
          <Music2 className="h-8 w-8 text-neon-cyan" aria-hidden />
          <h2 className="font-semibold">Reproductor</h2>
          <p className="text-sm text-text-muted">
            Streaming con seek, cola e historial (próximo).
          </p>
        </Card>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link to="/library">
          <Button variant="primary" size="lg">
            Explorar biblioteca
          </Button>
        </Link>
        <Link to="/playlists">
          <Button variant="secondary" size="lg">
            <ListMusic className="h-4 w-4" aria-hidden />
            Playlists
          </Button>
        </Link>
        <Link to="/favorites">
          <Button variant="ghost" size="lg">
            <Heart className="h-4 w-4 text-neon-pink" aria-hidden />
            Favoritos
          </Button>
        </Link>
      </div>
    </div>
  );
}
