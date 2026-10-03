import { Outlet } from 'react-router-dom';
import TopHeader from './TopHeader';
import Sidebar from './Sidebar';
import PlayerBar from './PlayerBar';
import { usePlayerStore } from '@/stores/playerStore';
import { cn } from '@/utils/cn';

/**
 * App shell: desktop left sidebar (lg+), sticky top header with a hamburger
 * drawer on mobile, scrollable content and a fixed player bar.
 */
export default function Layout() {
  const hasPlayer = usePlayerStore((s) => s.currentTrack != null);

  return (
    <div className="flex min-h-dvh flex-col bg-bg text-text lg:pl-60">
      <Sidebar />
      <TopHeader />

      <main
        className={cn(
          'mx-auto w-full max-w-5xl flex-1 px-4 pt-4 sm:px-6',
          hasPlayer ? 'pb-32 lg:pb-28' : 'pb-8 lg:pb-12',
        )}
      >
        <Outlet />
      </main>

      <PlayerBar />
    </div>
  );
}
