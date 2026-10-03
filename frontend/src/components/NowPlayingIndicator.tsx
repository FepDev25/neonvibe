import { cn } from '@/utils/cn';

/**
 * Animated equalizer bars shown next to the currently playing track in the
 * listings (Spotify-style). Bounces only while audio is playing; the parent
 * decides when to render it vs. the track number.
 */
export default function NowPlayingBars({ className }: { className?: string }) {
  return (
    <span className={cn('eq-bars', className)} role="img" aria-label="Reproduciendo">
      <span />
      <span />
      <span />
    </span>
  );
}
