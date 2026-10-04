import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import LibraryPage from './pages/LibraryPage';
import AlbumBrowserPage from './pages/AlbumBrowserPage';
import ArtistBrowserPage from './pages/ArtistBrowserPage';
import AlbumDetailPage from './pages/AlbumDetailPage';
import ArtistDetailPage from './pages/ArtistDetailPage';
import PlaylistsPage from './pages/PlaylistsPage';
import PlaylistDetailPage from './pages/PlaylistDetailPage';
import PublicPlaylistPage from './pages/PublicPlaylistPage';
import FavoritesPage from './pages/FavoritesPage';
import SearchPage from './pages/SearchPage';
import HistoryPage from './pages/HistoryPage';
import StatsPage from './pages/StatsPage';
import DownloadsPage from './pages/DownloadsPage';
import UploadPage from './pages/UploadPage';
import SettingsPage from './pages/SettingsPage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import { useAuthStore } from './stores/authStore';
import { ensureDevSession } from './api/devBootstrap';
import { useSessionBootstrap } from './hooks/useSessionBootstrap';

/**
 * Top-level router. Every route renders inside the shared Layout.
 *
 * On mount: bootstrap the dev session (if any). The session-dependent wiring
 * (WebSocket sync, queue restore, offline state) is handled by
 * {@link useSessionBootstrap}, keyed on the authenticated user so it also runs
 * after a production login.
 */
export default function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const userId = useAuthStore((s) => s.user?.id);

  useEffect(() => {
    void ensureDevSession();
  }, []);

  useSessionBootstrap(isAuthenticated, userId);

  // Production gate: no session -> login. DEV uses the mock bootstrap instead.
  const needsLogin = import.meta.env.PROD && !isAuthenticated;

  return (
    <Routes>
      <Route path="/p/:id" element={<PublicPlaylistPage />} />
      {needsLogin ? (
        <Route path="*" element={<LoginPage />} />
      ) : (
        <>
          <Route element={<Layout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/library" element={<LibraryPage />} />
            <Route path="/albums" element={<AlbumBrowserPage />} />
            <Route path="/artists" element={<ArtistBrowserPage />} />
            <Route path="/album/:id" element={<AlbumDetailPage />} />
            <Route path="/artist/:id" element={<ArtistDetailPage />} />
            <Route path="/playlists" element={<PlaylistsPage />} />
            <Route path="/playlist/:id" element={<PlaylistDetailPage />} />
            <Route path="/favorites" element={<FavoritesPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/stats" element={<StatsPage />} />
            <Route path="/downloads" element={<DownloadsPage />} />
            <Route path="/upload" element={<UploadPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/home" element={<Navigate to="/" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </>
      )}
    </Routes>
  );
}
