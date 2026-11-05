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
  UploadCloud,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Exact match for the active state (used for the index route). */
  end: boolean;
}

export interface NavGroup {
  id: string;
  label: string;
  items: NavItem[];
}

const HOME: NavItem = { to: '/', label: 'Inicio', icon: Home, end: true };
const LIBRARY: NavItem = { to: '/library', label: 'Biblioteca', icon: Library, end: false };
const SEARCH: NavItem = { to: '/search', label: 'Buscar', icon: Search, end: false };
const SETTINGS: NavItem = { to: '/settings', label: 'Ajustes', icon: Settings, end: false };

/** Settings is anchored at the bottom of the desktop sidebar. */
export const SETTINGS_NAV: NavItem = SETTINGS;

/** Primary destinations: always one tap away (desktop sidebar + mobile tabs). */
export const PRIMARY_NAV: NavItem[] = [HOME, LIBRARY, SEARCH];

/** Collections and activity live in grouped secondary navigation. */
export const COLLECTION_NAV: NavItem[] = [
  { to: '/albums', label: 'Álbumes', icon: Disc3, end: false },
  { to: '/artists', label: 'Artistas', icon: UserRound, end: false },
  { to: '/playlists', label: 'Playlists', icon: ListMusic, end: false },
  { to: '/favorites', label: 'Favoritos', icon: Heart, end: false },
];

export const ACTIVITY_NAV: NavItem[] = [
  { to: '/history', label: 'Historial', icon: History, end: false },
  { to: '/stats', label: 'Estadísticas', icon: BarChart3, end: false },
  { to: '/downloads', label: 'Descargas', icon: Download, end: false },
];

/** Admin-only destination. It never competes with the listening navigation. */
export const ADMIN_NAV: NavItem[] = [
  { to: '/upload', label: 'Subir', icon: UploadCloud, end: false },
];

/** Grouped desktop sidebar. Primary stays at three items (acceptance: <= 5). */
export const NAV_GROUPS: NavGroup[] = [
  { id: 'primary', label: 'Principal', items: PRIMARY_NAV },
  { id: 'collections', label: 'Colecciones', items: COLLECTION_NAV },
  { id: 'activity', label: 'Actividad', items: ACTIVITY_NAV },
];

/** Mobile bottom tabs: the four destinations that must always be one tap away. */
export const MOBILE_TABS: NavItem[] = [HOME, SEARCH, LIBRARY, SETTINGS];

/** Secondary destinations shown in the mobile "Más" sheet. */
export const SECONDARY_NAV: NavItem[] = [...COLLECTION_NAV, ...ACTIVITY_NAV];

const EXACT_TITLES = new Map<string, string>([
  ['/', 'Inicio'],
  ['/library', 'Biblioteca'],
  ['/library/albums', 'Álbumes'],
  ['/library/artists', 'Artistas'],
  ['/albums', 'Álbumes'],
  ['/artists', 'Artistas'],
  ['/playlists', 'Playlists'],
  ['/favorites', 'Favoritos'],
  ['/search', 'Buscar'],
  ['/history', 'Historial'],
  ['/stats', 'Estadísticas'],
  ['/downloads', 'Descargas'],
  ['/upload', 'Subir música'],
  ['/settings', 'Ajustes'],
]);

/**
 * Contextual header title for a route. Detail pages get a generic section name
 * because the real title is rendered by the page itself as its `h1`.
 */
export function pageTitle(pathname: string): string {
  const normalized =
    pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
  const exact = EXACT_TITLES.get(normalized);
  if (exact) {
    return exact;
  }
  if (normalized.startsWith('/album/')) {
    return 'Álbum';
  }
  if (normalized.startsWith('/artist/')) {
    return 'Artista';
  }
  if (normalized.startsWith('/playlist/')) {
    return 'Playlist';
  }
  return 'NeonVibe';
}
