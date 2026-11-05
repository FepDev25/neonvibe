import { FileText, Heart, Plus } from 'lucide-react';
import { useFavoritesStore } from '@/stores/favoritesStore';
import { useIsAdmin } from '@/hooks/useIsAdmin';
import type { PlayerTrack } from '@/stores/playerStore';
import type { Track } from '@/types';
import ActionsSheet, { ACTION_ROW } from './ActionsSheet';
import TrackDownloadButton from './TrackDownloadButton';
import RadioButton from './RadioButton';
import TrackEditButton from './TrackEditButton';
import { cn } from '@/utils/cn';

interface TrackActionsSheetProps {
  track: Track;
  open: boolean;
  onClose: () => void;
  onAddToPlaylist: () => void;
  onOpenLyrics: () => void;
}

/**
 * Track row actions: favorite, playlist, offline download, radio, lyrics and
 * (for admins) metadata editing. The pencil no longer lives in the row.
 */
export default function TrackActionsSheet({
  track,
  open,
  onClose,
  onAddToPlaylist,
  onOpenLyrics,
}: TrackActionsSheetProps) {
  const isAdmin = useIsAdmin();
  const isFavorite = useFavoritesStore((s) => s.isFavorite('TRACK', track.id));
  const toggleFavorite = useFavoritesStore((s) => s.toggle);
  const playerTrack: PlayerTrack = {
    id: track.id,
    title: track.title,
    artist: track.artist ?? '',
    album: track.album,
    durationSeconds: track.duration_seconds,
  };

  return (
    <ActionsSheet
      open={open}
      onClose={onClose}
      title={track.title}
      description={[track.artist, track.album].filter(Boolean).join(' · ')}
    >
      <button
        type="button"
        onClick={() => {
          void toggleFavorite('TRACK', track.id);
          onClose();
        }}
        className={cn(ACTION_ROW, isFavorite && 'text-accent-text')}
      >
        <Heart
          className="h-4 w-4 shrink-0"
          fill={isFavorite ? 'currentColor' : 'none'}
          aria-hidden
        />
        {isFavorite ? 'Quitar de favoritos' : 'Marcar como favorito'}
      </button>

      <button type="button" onClick={onAddToPlaylist} className={ACTION_ROW}>
        <Plus className="h-4 w-4 shrink-0" aria-hidden />
        Añadir a playlist
      </button>

      <TrackDownloadButton track={playerTrack} showLabel className={ACTION_ROW} />

      <RadioButton
        trackId={track.id}
        variant="ghost"
        className={ACTION_ROW}
        label="Radio de esta canción"
      />

      <button type="button" onClick={onOpenLyrics} className={ACTION_ROW}>
        <FileText className="h-4 w-4 shrink-0" aria-hidden />
        Ver letras
      </button>

      {isAdmin && <TrackEditButton track={track} compact={false} />}
    </ActionsSheet>
  );
}
