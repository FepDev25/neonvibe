import { useState, type CSSProperties } from 'react';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  ListMusic,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import { usePlayerStore, tracksToPlayerQueue } from '@/stores/playerStore';
import { getRadioSeed } from '@/api/radio';
import { trackCoverUrl } from '@/api/cover';
import AlbumCover from './AlbumCover';
import SeekBar from './SeekBar';
import QueueSheet from './QueueSheet';
import LyricsSheet from './LyricsSheet';
import AddToPlaylistSheet from './AddToPlaylistSheet';
import NowPlayingView from './NowPlayingView';
import PlayerMoreMenu from './PlayerMoreMenu';
import { cn } from '@/utils/cn';

/**
 * Fixed player bar, docked above the mobile bottom tabs.
 *
 * Desktop: three zones (track info | transport + seek | volume/queue/actions)
 * so the play button sits in the middle of the content column. Mobile: a 64px
 * mini-player with cover, title/artist, play/pause and next only; the remaining
 * actions live in Now Playing and the desktop "⋯" menu.
 */
export default function PlayerBar() {
  const currentTrack = usePlayerStore((s) => s.currentTrack);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const progress = usePlayerStore((s) => s.progress);
  const duration = usePlayerStore((s) => s.duration);
  const queue = usePlayerStore((s) => s.queue);
  const currentIndex = usePlayerStore((s) => s.currentIndex);
  const shuffle = usePlayerStore((s) => s.shuffle);
  const repeat = usePlayerStore((s) => s.repeat);
  const volume = usePlayerStore((s) => s.volume);
  const toggle = usePlayerStore((s) => s.toggle);
  const next = usePlayerStore((s) => s.next);
  const prev = usePlayerStore((s) => s.prev);
  const seek = usePlayerStore((s) => s.seek);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);
  const cycleRepeat = usePlayerStore((s) => s.cycleRepeat);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const playTrack = usePlayerStore((s) => s.playTrack);
  const playerError = usePlayerStore((s) => s.error);
  const retry = usePlayerStore((s) => s.retry);
  const dismissError = usePlayerStore((s) => s.dismissError);

  const [queueOpen, setQueueOpen] = useState(false);
  const [lyricsOpen, setLyricsOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [nowPlayingOpen, setNowPlayingOpen] = useState(false);
  const [radioBusy, setRadioBusy] = useState(false);

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

  if (!currentTrack) {
    return null;
  }

  const hasNext =
    shuffle && queue.length > 1
      ? true
      : repeat === 'ALL'
        ? queue.length > 0
        : currentIndex < queue.length - 1;
  const hasPrev = currentIndex > 0 || repeat === 'ALL' || progress > 3;
  const muted = volume === 0;
  const progressPct =
    duration > 0 ? Math.min(100, Math.max(0, (progress / duration) * 100)) : 0;
  const transportBtn =
    'flex h-11 w-11 items-center justify-center rounded-full transition-colors hover:bg-surface-alt';
  const activeIcon = 'text-accent-text';

  return (
    <>
      <div className="fixed inset-x-0 bottom-[calc(3.5rem+env(safe-area-inset-bottom))] z-30 border-t border-border bg-surface/95 backdrop-blur lg:inset-x-auto lg:bottom-0 lg:left-60 lg:right-0">
        <div className="mx-auto w-full max-w-[90rem] px-3 py-1.5 sm:px-6">
          {playerError && (
            <div
              role="alert"
              className="mb-1 flex items-center justify-between gap-2 rounded-lg border border-neon-pink/40 bg-neon-pink/10 px-2 py-1 text-[11px] text-text"
            >
              <span className="min-w-0 flex-1 truncate" title={playerError.message}>
                {playerError.message}
              </span>
              <div className="flex shrink-0 items-center gap-1">
                {playerError.recoverable && (
                  <button
                    type="button"
                    onClick={() => retry()}
                    className="min-h-[44px] rounded px-2 font-semibold text-accent-text hover:bg-surface-alt"
                  >
                    Reintentar
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => dismissError()}
                  aria-label="Descartar aviso de reproducción"
                  className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
                >
                  <X className="h-4 w-4" aria-hidden />
                </button>
              </div>
            </div>
          )}

          {/* Mobile mini-player: info + exactly two transport controls. */}
          <div role="group" aria-label="Reproductor compacto" className="lg:hidden">
            <div
              role="progressbar"
              aria-label="Progreso de reproducción"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={Math.round(progressPct)}
              className="-mx-3 h-0.5 bg-surface-alt"
            >
              <div
                className="h-full bg-gradient-to-r from-accent-text to-accent-strong"
                style={{ width: `${progressPct}%` }}
              />
            </div>
            <div className="flex h-16 items-center gap-1">
              <button
                type="button"
                onClick={() => setNowPlayingOpen(true)}
                aria-label="Abrir canción"
                title="Abrir canción"
                className="flex min-w-0 flex-1 items-center gap-2 rounded-xl py-1 pr-1 text-left transition-colors hover:bg-surface-alt/60"
              >
                <AlbumCover
                  seed={`${currentTrack.title}-${currentTrack.artist}`}
                  alt={`Carátula de ${currentTrack.title}`}
                  src={trackCoverUrl(currentTrack.id)}
                  className="h-10 w-10 shrink-0 rounded-lg"
                />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-sm font-semibold text-text">
                    {currentTrack.title}
                  </span>
                  <span className="truncate text-xs text-text-muted">
                    {currentTrack.artist}
                  </span>
                </span>
              </button>

              <button
                type="button"
                onClick={toggle}
                aria-label={isPlaying ? 'Pausar' : 'Reproducir'}
                title={isPlaying ? 'Pausar' : 'Reproducir'}
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-neon-cyan text-black neon-glow transition-colors hover:bg-neon-pink hover:text-white"
              >
                {isPlaying ? (
                  <Pause className="h-5 w-5" aria-hidden />
                ) : (
                  <Play className="h-5 w-5 translate-x-[1px]" aria-hidden />
                )}
              </button>

              <button
                type="button"
                onClick={next}
                disabled={!hasNext}
                aria-label="Siguiente"
                title="Siguiente"
                className={cn(transportBtn, 'shrink-0 text-text hover:text-accent-text disabled:opacity-30')}
              >
                <SkipForward className="h-5 w-5" aria-hidden />
              </button>
            </div>
          </div>

          {/* Desktop player bar: info | centered transport + seek | volume/queue. */}
          <div
            role="group"
            aria-label="Reproductor de escritorio"
            className="hidden items-center gap-4 py-1 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_minmax(0,1fr)]"
          >
            <button
              type="button"
              onClick={() => setNowPlayingOpen(true)}
              aria-label="Abrir canción"
              title="Abrir canción"
              className="group flex min-w-0 items-center gap-3 rounded-xl p-1 text-left transition-colors hover:bg-surface-alt/60"
            >
              <AlbumCover
                seed={`${currentTrack.title}-${currentTrack.artist}`}
                alt={`Carátula de ${currentTrack.title}`}
                src={trackCoverUrl(currentTrack.id)}
                className="h-12 w-12 shrink-0 rounded-lg"
              />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-sm font-semibold text-text">
                  {currentTrack.title}
                </span>
                <span className="truncate text-xs text-text-muted">
                  {currentTrack.artist}
                </span>
              </span>
            </button>

            <div className="flex min-w-0 flex-col items-center gap-0.5">
              <div className="flex items-center justify-center gap-1 sm:gap-2">
                <button
                  type="button"
                  onClick={toggleShuffle}
                  aria-label={shuffle ? 'Desactivar aleatorio' : 'Activar aleatorio'}
                  aria-pressed={shuffle}
                  title="Aleatorio"
                  className={cn(
                    transportBtn,
                    'hidden sm:flex',
                    shuffle ? activeIcon : 'text-text-muted hover:text-text',
                  )}
                >
                  <Shuffle className="h-4 w-4" aria-hidden />
                </button>

                <button
                  type="button"
                  onClick={prev}
                  disabled={!hasPrev}
                  aria-label="Anterior"
                  title="Anterior"
                  className={cn(transportBtn, 'text-text hover:text-accent-text disabled:opacity-30')}
                >
                  <SkipBack className="h-5 w-5" aria-hidden />
                </button>

                <button
                  type="button"
                  onClick={toggle}
                  aria-label={isPlaying ? 'Pausar' : 'Reproducir'}
                  title={isPlaying ? 'Pausar' : 'Reproducir'}
                  className="flex h-12 w-12 items-center justify-center rounded-full bg-neon-cyan text-black neon-glow transition-colors hover:bg-neon-pink hover:text-white"
                >
                  {isPlaying ? (
                    <Pause className="h-5 w-5" aria-hidden />
                  ) : (
                    <Play className="h-5 w-5 translate-x-[1px]" aria-hidden />
                  )}
                </button>

                <button
                  type="button"
                  onClick={next}
                  disabled={!hasNext}
                  aria-label="Siguiente"
                  title="Siguiente"
                  className={cn(transportBtn, 'text-text hover:text-accent-text disabled:opacity-30')}
                >
                  <SkipForward className="h-5 w-5" aria-hidden />
                </button>

                <button
                  type="button"
                  onClick={cycleRepeat}
                  aria-label={`Repetición: ${repeat === 'ONE' ? 'una' : repeat === 'ALL' ? 'todo' : 'ninguna'}`}
                  aria-pressed={repeat !== 'NONE'}
                  title="Repetición"
                  className={cn(
                    transportBtn,
                    'hidden sm:flex',
                    repeat !== 'NONE' ? activeIcon : 'text-text-muted hover:text-text',
                  )}
                >
                  {repeat === 'ONE' ? (
                    <Repeat1 className="h-4 w-4" aria-hidden />
                  ) : (
                    <Repeat className="h-4 w-4" aria-hidden />
                  )}
                </button>
              </div>

              <SeekBar
                progress={progress}
                duration={duration}
                onSeek={seek}
                className="w-full max-w-xl"
              />
            </div>

            <div className="flex items-center justify-end gap-1">
              <button
                type="button"
                onClick={() => setVolume(muted ? 1 : 0)}
                aria-label={muted ? 'Activar sonido' : 'Silenciar'}
                title={muted ? 'Activar sonido' : 'Silenciar'}
                className={cn(transportBtn, 'text-text-muted hover:text-accent-text')}
              >
                {muted ? (
                  <VolumeX className="h-4 w-4" aria-hidden />
                ) : (
                  <Volume2 className="h-4 w-4" aria-hidden />
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
                className="neon-range hidden h-1.5 w-24 cursor-pointer xl:block"
              />

              <button
                type="button"
                onClick={() => setQueueOpen(true)}
                aria-label="Ver cola"
                title="Cola"
                className={cn(transportBtn, 'text-text-muted hover:text-accent-text')}
              >
                <ListMusic className="h-5 w-5" aria-hidden />
              </button>

              <PlayerMoreMenu
                track={currentTrack}
                onAddToPlaylist={() => setAddOpen(true)}
                onOpenLyrics={() => setLyricsOpen(true)}
                onStartRadio={() => void startRadio(currentTrack.id)}
                radioBusy={radioBusy}
              />
            </div>
          </div>
        </div>
      </div>

      <QueueSheet open={queueOpen} onClose={() => setQueueOpen(false)} />
      <LyricsSheet open={lyricsOpen} onClose={() => setLyricsOpen(false)} />
      <AddToPlaylistSheet open={addOpen} trackId={currentTrack.id} onClose={() => setAddOpen(false)} />
      <NowPlayingView open={nowPlayingOpen} onClose={() => setNowPlayingOpen(false)} />
    </>
  );
}
