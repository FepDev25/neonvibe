import { NavLink } from 'react-router-dom';
import {
  Home,
  Library,
  Disc3,
  UserRound,
  ListMusic,
  Heart,
  Search,
  Settings,
  Music2,
} from 'lucide-react';
import { cn } from '@/utils/cn';

const ITEMS = [
  { to: '/', label: 'Inicio', icon: Home, end: true },
  { to: '/library', label: 'Biblioteca', icon: Library, end: false },
  { to: '/albums', label: 'Álbumes', icon: Disc3, end: false },
  { to: '/artists', label: 'Artistas', icon: UserRound, end: false },
  { to: '/playlists', label: 'Playlists', icon: ListMusic, end: false },
  { to: '/favorites', label: 'Favoritos', icon: Heart, end: false },
  { to: '/search', label: 'Buscar', icon: Search, end: false },
  { to: '/settings', label: 'Ajustes', icon: Settings, end: false },
] as const;

/**
 * Desktop left sidebar (lg+). Mobile keeps the bottom navigation instead.
 */
export default function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-border bg-surface lg:flex">
      <div className="flex items-center gap-2 px-5 pb-2 pt-5">
        <Music2 className="h-6 w-6 text-neon-cyan" aria-hidden />
        <span className="neon-text text-lg font-bold tracking-tight">NeonVibe</span>
      </div>
      <nav className="flex flex-col gap-0.5 px-3 py-2">
        {ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                'flex min-h-[44px] items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-neon-purple/15 text-neon-cyan'
                  : 'text-text-muted hover:bg-surface-alt hover:text-text',
              )
            }
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
