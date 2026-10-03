import { useRef, useState } from 'react';
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
  FileText,
  Waves,
  X,
  Radio,
  Loader2,
  Plus,
  ChevronUp,
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
import FavoriteButton from './FavoriteButton';
import TrackDownloadButton from './TrackDownloadButton';
import Visualizer from './Visualizer';
import { useModalA11y } from '@/hooks/useModalA11y';
import { cn } from '@/utils/cn';

/**
 * Fixed player bar, docked above the bottom navigation. Shows the current track,
 * seek bar, playback controls, shuffle/repeat and the queue sheet trigger.
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

  const [queueOpen, setQueueOpen] = useState(false);
  const [lyricsOpen, setLyricsOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [nowPlayingOpen, setNowPlayingOpen] = useState(false);
  const [vizOpen, setVizOpen] = useState(false);
  const [radioBusy, setRadioBusy] = useState(false);
  const vizRef = useRef<HTMLDivElement>(null);
  useModalA11y(vizOpen, () => setVizOpen(false), vizRef);

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

  const controlBtn =
    'flex h-9 w-9 items-center justify-center rounded-full transition-colors hover:bg-surface-alt';

  return (
    <>
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/95 backdrop-blur safe-bottom lg:inset-x-auto lg:left-60 lg:right-0">
        <div className="mx-auto flex w-full max-w-5xl flex-col px-3 py-1.5 sm:px-6">
          <SeekBar progress={progress} duration={duration} onSeek={seek} className="mb-1" />

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setNowPlayingOpen(true)}
              aria-label="Abrir canción"
              title="Abrir canción"
              className="group -m-1 flex min-w-0 flex-1 items-center gap-2 rounded-xl p-1 text-left transition-colors hover:bg-surface-alt/60"
            >
              <AlbumCover
                seed={`${currentTrack.title}-${currentTrack.artist}`}
                alt={`Carátula de ${currentTrack.title}`}
                src={trackCoverUrl(currentTrack.id)}
                className="h-11 w-11 shrink-0 rounded-lg"
              />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-sm font-semibold text-text">{currentTrack.title}</span>
                <span className="truncate text-xs text-text-muted">{currentTrack.artist}</span>
              </span>
              <ChevronUp
                className="hidden h-4 w-4 shrink-0 text-text-muted opacity-0 transition-opacity group-hover:opacity-100 sm:block"
                aria-hidden
              />
            </button>

            <FavoriteButton
              entityType="TRACK"
              entityId={currentTrack.id}
              size="sm"
              className="hidden shrink-0 sm:flex"
            />

            <button
              type="button"
              onClick={() => setAddOpen(true)}
              aria-label="Añadir a playlist"
              title="Añadir a playlist"
              className={cn(controlBtn, 'hidden sm:flex text-text-muted hover:text-neon-cyan')}
            >
              <Plus className="h-4 w-4" aria-hidden />
            </button>

            <TrackDownloadButton
              track={currentTrack}
              className={cn(controlBtn, 'hidden sm:flex')}
            />

            <button
              type="button"
              onClick={toggleShuffle}
              aria-label={shuffle ? 'Desactivar aleatorio' : 'Activar aleatorio'}
              aria-pressed={shuffle}
              title="Aleatorio"
              className={cn(
                controlBtn,
                'hidden sm:flex',
                shuffle ? 'text-neon-cyan' : 'text-text-muted hover:text-text',
              )}
            >
              <Shuffle className="h-4 w-4" aria-hidden />
            </button>

            <button
              type="button"
              onClick={cycleRepeat}
              aria-label={`Repetición: ${repeat === 'ONE' ? 'una' : repeat === 'ALL' ? 'todo' : 'ninguna'}`}
              aria-pressed={repeat !== 'NONE'}
              title="Repetición"
              className={cn(
                controlBtn,
                'hidden sm:flex',
                repeat !== 'NONE' ? 'text-neon-cyan' : 'text-text-muted hover:text-text',
              )}
            >
              {repeat === 'ONE' ? (
                <Repeat1 className="h-4 w-4" aria-hidden />
              ) : (
                <Repeat className="h-4 w-4" aria-hidden />
              )}
            </button>

            <button
              type="button"
              onClick={prev}
              disabled={!hasPrev}
              aria-label="Anterior"
              title="Anterior"
              className={cn(controlBtn, 'text-text hover:text-neon-cyan disabled:opacity-30')}
            >
              <SkipBack className="h-5 w-5" aria-hidden />
            </button>

            <button
              type="button"
              onClick={toggle}
              aria-label={isPlaying ? 'Pausar' : 'Reproducir'}
              title={isPlaying ? 'Pausar' : 'Reproducir'}
              className="flex h-11 w-11 items-center justify-center rounded-full bg-neon-cyan text-black neon-glow transition-colors hover:bg-neon-pink hover:text-white"
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
              className={cn(controlBtn, 'text-text hover:text-neon-cyan disabled:opacity-30')}
            >
              <SkipForward className="h-5 w-5" aria-hidden />
            </button>

            <button
              type="button"
              onClick={() => setVolume(muted ? 1 : 0)}
              aria-label={muted ? 'Activar sonido' : 'Silenciar'}
              title={muted ? 'Activar sonido' : 'Silenciar'}
              className={cn(controlBtn, 'hidden sm:flex text-text-muted hover:text-neon-cyan')}
            >
              {muted ? (
                <VolumeX className="h-4 w-4" aria-hidden />
              ) : (
                <Volume2 className="h-4 w-4" aria-hidden />
              )}
            </button>

            <button
              type="button"
              onClick={() => setVizOpen(true)}
              aria-label="Visualizador"
              title="Visualizador"
              className={cn(controlBtn, 'hidden sm:flex text-text-muted hover:text-neon-pink')}
            >
              <Waves className="h-4 w-4" aria-hidden />
            </button>

            <button
              type="button"
              onClick={() => void startRadio(currentTrack.id)}
              disabled={radioBusy}
              aria-label="Radio basada en esta canción"
              title="Radio basada en esta canción"
              className={cn(controlBtn, 'hidden sm:flex text-text-muted hover:text-neon-purple disabled:opacity-50')}
            >
              {radioBusy ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
              ) : (
                <Radio className="h-4 w-4" aria-hidden />
              )}
            </button>

            <button
              type="button"
              onClick={() => setLyricsOpen(true)}
              aria-label="Ver letras"
              title="Letras"
              className={cn(controlBtn, 'text-text-muted hover:text-neon-cyan')}
            >
              <FileText className="h-4 w-4" aria-hidden />
            </button>

            <button
              type="button"
              onClick={() => setQueueOpen(true)}
              aria-label="Ver cola"
              title="Cola"
              className={cn(controlBtn, 'text-text-muted hover:text-neon-cyan')}
            >
              <ListMusic className="h-5 w-5" aria-hidden />
            </button>
          </div>
        </div>
      </div>

      <QueueSheet open={queueOpen} onClose={() => setQueueOpen(false)} />
      <LyricsSheet open={lyricsOpen} onClose={() => setLyricsOpen(false)} />
      <AddToPlaylistSheet open={addOpen} trackId={currentTrack.id} onClose={() => setAddOpen(false)} />
      <NowPlayingView open={nowPlayingOpen} onClose={() => setNowPlayingOpen(false)} />

      {vizOpen && (
        <div
          ref={vizRef}
          tabIndex={-1}
          role="dialog"
          aria-modal="true"
          aria-label="Visualizador de audio"
          className="fixed inset-0 z-50 flex flex-col bg-black/95 outline-none"
        >
          <div className="flex items-center justify-between px-5 pt-5">
            <p className="neon-text text-lg font-bold">Visualizador</p>
            <button
              type="button"
              onClick={() => setVizOpen(false)}
              aria-label="Cerrar visualizador"
              className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-alt text-text hover:text-neon-cyan"
            >
              <X className="h-5 w-5" aria-hidden />
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
