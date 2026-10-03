import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  ChevronDown,
  ListMusic,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Plus,
  FileText,
  Radio,
  Waves,
  Loader2,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { usePlayerStore, tracksToPlayerQueue } from '@/stores/playerStore';
import { useQualityStore, QUALITY_OPTIONS, type AudioQuality } from '@/stores/qualityStore';
import { getRadioSeed } from '@/api/radio';
import { getTranscodeStatus } from '@/api/transcode';
import { trackCoverUrl } from '@/api/cover';
import AlbumCover from './AlbumCover';
import SeekBar from './SeekBar';
import FavoriteButton from './FavoriteButton';
import TrackDownloadButton from './TrackDownloadButton';
import AddToPlaylistSheet from './AddToPlaylistSheet';
import LyricsSheet from './LyricsSheet';
import QueueSheet from './QueueSheet';
import Visualizer from './Visualizer';
import { cn } from '@/utils/cn';

interface NowPlayingViewProps {
  open: boolean;
  onClose: () => void;
}

/** Shared visual for the round action tiles below the cover. */
const TILE =
  'flex h-12 w-12 items-center justify-center rounded-full border border-border/60 bg-surface/60 backdrop-blur transition-all hover:-translate-y-0.5 hover:border-neon-cyan/60 focus:outline-none focus-visible:ring-2 focus-visible:ring-neon-cyan';

/**
 * Full-screen "Now Playing" overlay, opened by tapping the current track in the
 * player bar. Big art, transport controls and per-track actions in one place;
 * works as a takeover on mobile and as a centered column on desktop.
 */
export default function NowPlayingView({ open, onClose }: NowPlayingViewProps) {
  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const progress = usePlayerStore((s) => s.progress);
  const duration = usePlayerStore((s) => s.duration);
  const volume = usePlayerStore((s) => s.volume);
  const shuffle = usePlayerStore((s) => s.shuffle);
  const repeat = usePlayerStore((s) => s.repeat);
  const queue = usePlayerStore((s) => s.queue);
  const currentIndex = usePlayerStore((s) => s.currentIndex);
  const toggle = usePlayerStore((s) => s.toggle);
  const next = usePlayerStore((s) => s.next);
  const prev = usePlayerStore((s) => s.prev);
  const seek = usePlayerStore((s) => s.seek);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);
  const cycleRepeat = usePlayerStore((s) => s.cycleRepeat);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const playTrack = usePlayerStore((s) => s.playTrack);
  const reloadCurrent = usePlayerStore((s) => s.reloadCurrent);

  const quality = useQualityStore((s) => s.quality);
  const setQuality = useQualityStore((s) => s.setQuality);
  const transcode = useQuery({
    queryKey: ['transcodeStatus'],
    queryFn: getTranscodeStatus,
    staleTime: 5 * 60_000,
  });

  const chooseQuality = (next: AudioQuality) => {
    if (next === quality) {
      return;
    }
    setQuality(next);
    reloadCurrent();
  };

  const [addOpen, setAddOpen] = useState(false);
  const [lyricsOpen, setLyricsOpen] = useState(false);
  const [queueOpen, setQueueOpen] = useState(false);
  const [vizOpen, setVizOpen] = useState(false);
  const [radioBusy, setRadioBusy] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  // Focus the overlay on open and close the topmost layer on Escape. The nested
  // sheets (queue/lyrics/add) register their own capture-phase Escape handler
  // and stopPropagation, so this listener only fires when none of them is open.
  useEffect(() => {
    if (!open) {
      return;
    }
    rootRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') {
        return;
      }
      if (vizOpen) {
        setVizOpen(false);
      } else if (!addOpen && !lyricsOpen && !queueOpen) {
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, vizOpen, addOpen, lyricsOpen, queueOpen, onClose]);

  if (!open || !currentTrack) {
    return null;
  }

  const coverSrc = trackCoverUrl(currentTrack.id);
  const muted = volume === 0;
  const hasNext =
    shuffle && queue.length > 1
      ? true
      : repeat === 'ALL'
        ? queue.length > 0
        : currentIndex < queue.length - 1;
  const hasPrev = currentIndex > 0 || repeat === 'ALL' || progress > 3;

  const startRadio = async (trackId: number) => {
    setRadioBusy(true);
    try {
      const tracks = await getRadioSeed(trackId, 20);
      if (tracks.length > 0) {
        const q = tracksToPlayerQueue(tracks);
        playTrack(q[0], q);
      }
    } catch (err) {
      console.warn('[radio] failed to start', err);
    } finally {
      setRadioBusy(false);
    }
  };

  const iconBtn = 'flex h-11 w-11 items-center justify-center rounded-full transition-colors';

  return (
    <>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`Reproduciendo ${currentTrack.title}`}
        className="fixed inset-0 z-50 flex flex-col bg-bg/90 backdrop-blur-2xl"
      >
        {/* Ambient wash built from the cover itself, tinted by the neon palette. */}
        <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
          <div
            className="absolute inset-0 scale-125 opacity-40 blur-3xl"
            style={{ backgroundImage: `url(${coverSrc})`, backgroundSize: 'cover', backgroundPosition: 'center' }}
          />
          <div className="absolute inset-0 bg-gradient-to-b from-bg/70 via-bg/85 to-bg" />
          <div className="np-orb np-orb--purple np-orb--a left-[12%] top-[-12%] h-80 w-80" />
          <div className="np-orb np-orb--cyan np-orb--b bottom-[-14%] right-[-10%] h-72 w-72" />
          <div className="np-orb np-orb--pink np-orb--c left-[-14%] top-[42%] h-64 w-64" />
        </div>

        <div
          ref={rootRef}
          tabIndex={-1}
          className="relative mx-auto flex h-full w-full max-w-md flex-col px-5 pb-6 outline-none safe-top safe-bottom"
        >
          <header className="flex items-center justify-between pt-3">
            <button
              type="button"
              onClick={onClose}
              aria-label="Cerrar reproductor"
              title="Cerrar"
              className="flex h-10 w-10 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text"
            >
              <ChevronDown className="h-6 w-6" aria-hidden />
            </button>
            <p className="text-[11px] font-semibold uppercase tracking-[0.35em] text-text-muted">
              Reproduciendo
            </p>
            <button
              type="button"
              onClick={() => setQueueOpen(true)}
              aria-label="Ver cola"
              title="Cola"
              className="flex h-10 w-10 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-neon-cyan"
            >
              <ListMusic className="h-5 w-5" aria-hidden />
            </button>
          </header>

          <div className="flex flex-1 flex-col items-center justify-center gap-6 py-4">
            <div className="relative aspect-square w-full max-w-[17rem] overflow-hidden rounded-[2rem] border border-white/10 shadow-[0_0_60px_-10px_var(--color-glow)]">
              <AlbumCover
                seed={`${currentTrack.title}-${currentTrack.artist}`}
                alt={`Carátula de ${currentTrack.title}`}
                src={coverSrc}
                className="h-full w-full"
              />
              <div className="pointer-events-none absolute inset-0 rounded-[2rem] ring-1 ring-inset ring-white/10" />
            </div>

            <div className="w-full text-center">
              <h1 className="neon-text truncate text-2xl font-bold">{currentTrack.title}</h1>
              <p className="mt-1 truncate text-sm text-text-muted">
                {currentTrack.artist}
                {currentTrack.album ? ` · ${currentTrack.album}` : ''}
              </p>
            </div>
          </div>

          <SeekBar progress={progress} duration={duration} onSeek={seek} />

          <div className="mt-3 flex items-center justify-center gap-2 sm:gap-4">
            <button
              type="button"
              onClick={toggleShuffle}
              aria-label={shuffle ? 'Desactivar aleatorio' : 'Activar aleatorio'}
              aria-pressed={shuffle}
              title="Aleatorio"
              className={cn(iconBtn, shuffle ? 'text-neon-cyan' : 'text-text-muted hover:text-text')}
            >
              <Shuffle className="h-5 w-5" aria-hidden />
            </button>

            <button
              type="button"
              onClick={prev}
              disabled={!hasPrev}
              aria-label="Anterior"
              title="Anterior"
              className={cn(iconBtn, 'text-text hover:text-neon-cyan disabled:opacity-30')}
            >
              <SkipBack className="h-6 w-6" aria-hidden />
            </button>

            <button
              type="button"
              onClick={toggle}
              aria-label={isPlaying ? 'Pausar' : 'Reproducir'}
              title={isPlaying ? 'Pausar' : 'Reproducir'}
              className="flex h-16 w-16 items-center justify-center rounded-full bg-neon-cyan text-black neon-glow transition-colors hover:bg-neon-pink hover:text-white"
            >
              {isPlaying ? (
                <Pause className="h-7 w-7" aria-hidden />
              ) : (
                <Play className="h-7 w-7 translate-x-[2px]" aria-hidden />
              )}
            </button>

            <button
              type="button"
              onClick={next}
              disabled={!hasNext}
              aria-label="Siguiente"
              title="Siguiente"
              className={cn(iconBtn, 'text-text hover:text-neon-cyan disabled:opacity-30')}
            >
              <SkipForward className="h-6 w-6" aria-hidden />
            </button>

            <button
              type="button"
              onClick={cycleRepeat}
              aria-label={`Repetición: ${repeat === 'ONE' ? 'una' : repeat === 'ALL' ? 'todo' : 'ninguna'}`}
              aria-pressed={repeat !== 'NONE'}
              title="Repetición"
              className={cn(iconBtn, repeat !== 'NONE' ? 'text-neon-cyan' : 'text-text-muted hover:text-text')}
            >
              {repeat === 'ONE' ? (
                <Repeat1 className="h-5 w-5" aria-hidden />
              ) : (
                <Repeat className="h-5 w-5" aria-hidden />
              )}
            </button>
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-center gap-2 sm:gap-3">
            <FavoriteButton entityType="TRACK" entityId={currentTrack.id} size="lg" className={TILE} />

            <button
              type="button"
              onClick={() => setAddOpen(true)}
              aria-label="Añadir a playlist"
              title="Añadir a playlist"
              className={cn(TILE, 'text-text-muted hover:text-neon-cyan')}
            >
              <Plus className="h-5 w-5" aria-hidden />
            </button>

            <TrackDownloadButton track={currentTrack} className={TILE} />

            <button
              type="button"
              onClick={() => setLyricsOpen(true)}
              aria-label="Ver letras"
              title="Letras"
              className={cn(TILE, 'text-text-muted hover:text-neon-cyan')}
            >
              <FileText className="h-5 w-5" aria-hidden />
            </button>

            <button
              type="button"
              onClick={() => void startRadio(currentTrack.id)}
              disabled={radioBusy}
              aria-label="Radio basada en esta canción"
              title="Radio"
              className={cn(TILE, 'text-text-muted hover:text-neon-purple disabled:opacity-50')}
            >
              {radioBusy ? (
                <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
              ) : (
                <Radio className="h-5 w-5" aria-hidden />
              )}
            </button>

            <button
              type="button"
              onClick={() => setVizOpen(true)}
              aria-label="Visualizador"
              title="Visualizador"
              className={cn(TILE, 'text-text-muted hover:text-neon-pink')}
            >
              <Waves className="h-5 w-5" aria-hidden />
            </button>
          </div>

          {transcode.data?.available && (
            <div className="mt-5 flex flex-col items-center gap-2">
              <span className="text-[10px] font-semibold uppercase tracking-[0.3em] text-text-muted">
                Calidad
              </span>
              <div className="flex flex-wrap items-center justify-center gap-2">
                {QUALITY_OPTIONS.map((option) => (
                  <button
                    key={option.id}
                    type="button"
                    onClick={() => chooseQuality(option.id)}
                    aria-pressed={quality === option.id}
                    className={cn(
                      'rounded-full border px-3 py-1 text-xs font-semibold transition-colors',
                      quality === option.id
                        ? 'border-neon-cyan bg-neon-cyan/10 text-neon-cyan'
                        : 'border-border text-text-muted hover:border-neon-cyan/50 hover:text-text',
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="mt-5 hidden items-center gap-3 sm:flex">
            <button
              type="button"
              onClick={() => setVolume(muted ? 1 : 0)}
              aria-label={muted ? 'Activar sonido' : 'Silenciar'}
              className={cn(iconBtn, 'shrink-0 text-text-muted hover:text-neon-cyan')}
            >
              {muted ? (
                <VolumeX className="h-5 w-5" aria-hidden />
              ) : (
                <Volume2 className="h-5 w-5" aria-hidden />
              )}
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={volume}
              aria-label="Volumen"
              onChange={(e) => setVolume(Number(e.target.value))}
              style={{ '--fill': `${Math.round(volume * 100)}%` } as CSSProperties}
              className="neon-range h-1.5 flex-1 cursor-pointer"
            />
          </div>
        </div>
      </div>

      <AddToPlaylistSheet open={addOpen} trackId={currentTrack.id} onClose={() => setAddOpen(false)} />
      <LyricsSheet open={lyricsOpen} onClose={() => setLyricsOpen(false)} />
      <QueueSheet open={queueOpen} onClose={() => setQueueOpen(false)} />

      {vizOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Visualizador de audio"
          className="fixed inset-0 z-[60] flex flex-col bg-black/95 outline-none"
        >
          <div className="flex items-center justify-between px-5 pt-5">
            <p className="neon-text text-lg font-bold">Visualizador</p>
            <button
              type="button"
              onClick={() => setVizOpen(false)}
              aria-label="Cerrar visualizador"
              className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-alt text-text hover:text-neon-cyan"
            >
              <ChevronDown className="h-5 w-5" aria-hidden />
            </button>
          </div>
          <div className="flex flex-1 px-4 py-6">
            <Visualizer />
          </div>
        </div>
      )}
    </>
  );
}
