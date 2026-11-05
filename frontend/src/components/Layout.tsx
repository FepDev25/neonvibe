import { Outlet } from 'react-router-dom';
import TopHeader from './TopHeader';
import Sidebar from './Sidebar';
import BottomTabs from './BottomTabs';
import PlayerBar from './PlayerBar';
import Toaster from './Toaster';
import { usePlayerStore } from '@/stores/playerStore';
import { cn } from '@/utils/cn';

/**
 * App shell: desktop left sidebar (lg+), sticky contextual header, grouped
 * mobile bottom tabs, scrollable content and the player docked above the tabs.
 */
export default function Layout() {
  const hasPlayer = usePlayerStore((s) => s.currentTrack != null);

  return (
    <div className="flex min-h-dvh flex-col bg-bg text-text lg:pl-60">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-lg focus:bg-accent-strong focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Saltar al contenido
      </a>
      <Sidebar />
      <TopHeader />

      <main
        id="main-content"
        tabIndex={-1}
        className={cn(
          'mx-auto w-full max-w-[90rem] flex-1 px-4 pt-4 sm:px-6',
          hasPlayer ? 'pb-48 lg:pb-28' : 'pb-24 lg:pb-12',
        )}
      >
        <Outlet />
      </main>

      <BottomTabs />
      <PlayerBar />
      <Toaster />
    </div>
  );
}
