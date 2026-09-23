import { useMemo } from 'react';
import { X, FileText } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { getLyrics } from '@/api/lyrics';
import { usePlayerStore } from '@/stores/playerStore';
import Skeleton from './Skeleton';
import { cn } from '@/utils/cn';

interface LyricsSheetProps {
  open: boolean;
  onClose: () => void;
}

interface TimedLine {
  time: number;
  text: string;
}

const TIME_RE = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g;

/** Parses .lrc text into timed lines (ignores metadata tags like [ti:...]). */
function parseLrc(text: string): TimedLine[] {
  const lines: TimedLine[] = [];
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line.startsWith('[')) {
      continue;
    }
    const times: number[] = [];
    let match: RegExpExecArray | null;
    TIME_RE.lastIndex = 0;
    while ((match = TIME_RE.exec(line)) !== null) {
      const min = Number(match[1]);
      const sec = Number(match[2]);
      const frac = match[3] ? Number(match[3].padEnd(3, '0')) / 1000 : 0;
      times.push(min * 60 + sec + frac);
    }
    if (times.length === 0) {
      continue;
    }
    const text = line.replace(TIME_RE, '').trim();
    for (const time of times) {
      lines.push({ time, text });
    }
  }
  return lines.sort((a, b) => a.time - b.time);
}

/**
 * Bottom sheet with the current track's lyrics. Synced (.lrc) lyrics highlight
 * the active line based on player progress; plain lyrics just scroll.
 */
export default function LyricsSheet({ open, onClose }: LyricsSheetProps) {
  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const progress = usePlayerStore((s) => s.progress);
  const query = useQuery({
    queryKey: ['lyrics', currentTrack?.id],
    queryFn: () => getLyrics(currentTrack!.id),
    enabled: open && currentTrack != null,
  });

  const timed = useMemo(() => {
    if (!query.data?.lyrics || !query.data.synced) {
      return [];
    }
    return parseLrc(query.data.lyrics);
  }, [query.data]);

  const activeIndex = useMemo(() => {
    if (timed.length === 0) {
      return -1;
    }
    let idx = 0;
    for (let i = 0; i < timed.length; i++) {
      if (timed[i].time <= progress) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }, [timed, progress]);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/70 sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Letras"
    >
      <div
        className="flex h-[70vh] w-full max-w-md flex-col rounded-t-2xl border border-border bg-surface safe-bottom sm:h-[60vh] sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-1 flex items-center justify-between px-5 pt-5">
          <div className="min-w-0">
            <h2 className="text-lg font-bold text-text">Letras</h2>
            {currentTrack && (
              <p className="truncate text-xs text-text-muted">
                {currentTrack.title} · {currentTrack.artist}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="flex h-9 w-9 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <div className="overflow-y-auto px-5 pb-6 pt-2">
          {query.isPending ? (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-5 w-1/2" />
            </div>
          ) : !query.data?.lyrics ? (
            <div className="flex flex-col items-center gap-3 py-16 text-center">
              <FileText className="h-10 w-10 text-text-muted" aria-hidden />
              <p className="text-sm text-text-muted">No hay letras disponibles para esta canción.</p>
            </div>
          ) : timed.length > 0 ? (
            <div className="flex flex-col gap-3">
              {timed.map((line, i) => (
                <p
                  key={i}
                  className={cn(
                    'text-[15px] leading-relaxed transition-colors',
                    i === activeIndex ? 'neon-text font-bold' : 'text-text-muted',
                  )}
                >
                  {line.text || '\u00A0'}
                </p>
              ))}
            </div>
          ) : (
            <p className="whitespace-pre-line text-[15px] leading-relaxed text-text">
              {query.data.lyrics}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
