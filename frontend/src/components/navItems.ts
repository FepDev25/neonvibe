import {
  Home,
  Library,
  Disc3,
  UserRound,
  ListMusic,
  Heart,
  Search,
  Settings,
  History,
  Download,
  BarChart3,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Exact match for the active state (used for the index route). */
  end: boolean;
}

/** Single source of truth for the app navigation (sidebar + mobile drawer). */
export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Inicio', icon: Home, end: true },
  { to: '/library', label: 'Biblioteca', icon: Library, end: false },
  { to: '/albums', label: 'Álbumes', icon: Disc3, end: false },
  { to: '/artists', label: 'Artistas', icon: UserRound, end: false },
  { to: '/playlists', label: 'Playlists', icon: ListMusic, end: false },
  { to: '/favorites', label: 'Favoritos', icon: Heart, end: false },
  { to: '/history', label: 'Historial', icon: History, end: false },
  { to: '/stats', label: 'Estadísticas', icon: BarChart3, end: false },
  { to: '/downloads', label: 'Descargas', icon: Download, end: false },
  { to: '/search', label: 'Buscar', icon: Search, end: false },
  { to: '/settings', label: 'Ajustes', icon: Settings, end: false },
];
