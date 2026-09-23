import { useState } from 'react';
import { cn } from '@/utils/cn';

const COVER_PALETTE: Array<[string, string]> = [
  ['#bc13fe', '#00f3ff'], // purple -> cyan
  ['#ff00ff', '#bc13fe'], // pink -> purple
  ['#00f3ff', '#ff00ff'], // cyan -> pink
  ['#bc13fe', '#1a1a28'], // purple -> surface
  ['#ff00ff', '#0a0a12'], // pink -> bg
  ['#00f3ff', '#12121c'], // cyan -> surface
  ['#faff00', '#bc13fe'], // yellow -> purple
  ['#ff7a00', '#ff00ff'], // orange -> pink
];

function hashCode(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function paletteFor(seed: string): [string, string] {
  const index = hashCode(seed) % COVER_PALETTE.length;
  return COVER_PALETTE[index];
}

interface AlbumCoverProps {
  seed: string;
  alt: string;
  className?: string;
  /** Optional real cover URL; rendered as <img> with gradient fallback. */
  src?: string;
  /** Rendered inside the placeholder cover (e.g. a music icon). */
  children?: React.ReactNode;
}

/**
 * Album/artist art. Renders a real image when `src` is provided and loads;
 * falls back to a deterministic neon gradient placeholder on error.
 */
export default function AlbumCover({ seed, alt, className, src, children }: AlbumCoverProps) {
  const [failed, setFailed] = useState(false);
  const [from, to] = paletteFor(seed);

  if (src && !failed) {
    return (
      <div className={cn('relative aspect-square overflow-hidden', className)}>
        <img
          src={src}
          alt={alt}
          loading="lazy"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      </div>
    );
  }

  return (
    <div
      role="img"
      aria-label={alt}
      className={cn('flex aspect-square items-center justify-center overflow-hidden', className)}
      style={{ backgroundImage: `linear-gradient(135deg, ${from}, ${to})` }}
    >
      {children}
    </div>
  );
}
