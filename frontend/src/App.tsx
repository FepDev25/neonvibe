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
import SettingsPage from './pages/SettingsPage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import { useAuthStore } from './stores/authStore';
import { ensureDevSession } from './api/devBootstrap';
import { connectSync, setSyncHandler } from './player/sync';
import { usePlayerStore } from './stores/playerStore';
import { useOfflineStore } from './offline/offlineStore';

/**
 * Top-level router. Every route renders inside the shared Layout.
 *
 * On mount: bootstrap the dev session (if any), connect the WebSocket sync
 * channel, restore the persisted play queue and load offline download state.
 */
export default function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  useEffect(() => {
    void (async () => {
      await ensureDevSession();
      setSyncHandler((message) => {
        const store = usePlayerStore.getState();
        if (message.type === 'PLAYER_SYNC') {
          void store._applyPlayerSync(message.payload);
        } else {
          store._applyQueueUpdate(message.payload);
        }
      });
      connectSync();
      await usePlayerStore.getState().restoreFromServer();
      await useOfflineStore.getState().ensureLoaded();
      // Ask for persistent storage so downloads survive storage pressure.
      if (navigator.storage?.persist) {
        void navigator.storage.persist().catch(() => undefined);
      }
    })();
  }, []);

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
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/home" element={<Navigate to="/" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </>
      )}
    </Routes>
  );
}
