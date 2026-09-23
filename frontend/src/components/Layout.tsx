import { Outlet } from 'react-router-dom';
import TopHeader from './TopHeader';
import BottomNav from './BottomNav';
import Sidebar from './Sidebar';
import PlayerBar from './PlayerBar';
import { usePlayerStore } from '@/stores/playerStore';

/**
 * App shell: desktop left sidebar (lg+), sticky top header, scrollable content,
 * player bar and bottom nav (mobile only). Mobile-first.
 */
export default function Layout() {
  const hasPlayer = usePlayerStore((s) => s.currentTrack != null);

  return (
    <div className="flex min-h-dvh flex-col bg-bg text-text lg:pl-60">
      <Sidebar />
      <TopHeader />

      <main
        className={
          hasPlayer
            ? 'mx-auto w-full max-w-5xl flex-1 px-4 pb-44 pt-4 sm:px-6 lg:pb-28'
            : 'mx-auto w-full max-w-5xl flex-1 px-4 pb-24 pt-4 sm:px-6 lg:pb-12'
        }
      >
        <Outlet />
      </main>

      <PlayerBar />
      <BottomNav />
    </div>
  );
}
