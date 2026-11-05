import { useState } from 'react';
import { Ellipsis } from 'lucide-react';
import { useFavoritesStore } from '@/stores/favoritesStore';
import type { PlayerTrack } from '@/stores/playerStore';
import TrackRowBase from './TrackRowBase';
import TrackActionsSheet from './TrackActionsSheet';
import AddToPlaylistSheet from './AddToPlaylistSheet';
import LyricsSheet from './LyricsSheet';
import type { Track } from '@/types';

interface TrackRowProps {
  track: Track;
  /** Display number (track_number, or 1-based index in a list). */
  number?: number;
  /** Surrounding list used to build the play queue (defaults to [track]). */
  queue?: PlayerTrack[];
  /** Hide the artist line when the whole list shares it (album/artist pages). */
  showArtist?: boolean;
  /** Hide the album column when it would repeat the page title. */
  showAlbum?: boolean;
}

/**
 * Single track row. The title/cover start playback; every secondary action
 * (favorite, playlist, download, radio, lyrics, admin metadata) lives in `⋯`,
 * so mobile keeps one visible control and the title gets the width.
 */
export default function TrackRow({
  track,
  number,
  queue,
  showArtist = true,
  showAlbum = true,
}: TrackRowProps) {
  const [actionsOpen, setActionsOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [lyricsOpen, setLyricsOpen] = useState(false);
  const isFavorite = useFavoritesStore((s) => s.isFavorite('TRACK', track.id));

  return (
    <>
      <TrackRowBase
        track={track}
        number={number}
        queue={queue}
        showArtist={showArtist}
        showAlbum={showAlbum}
        favorite={isFavorite}
        actions={
          <button
            type="button"
            onClick={() => setActionsOpen(true)}
            aria-label={`Acciones de ${track.title}`}
            title="Más acciones"
            className="flex h-11 w-11 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-text lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100"
          >
            <Ellipsis className="h-5 w-5" aria-hidden />
          </button>
        }
      />

      {actionsOpen && (
        <TrackActionsSheet
          track={track}
          open
          onClose={() => setActionsOpen(false)}
          onAddToPlaylist={() => {
            setActionsOpen(false);
            setAddOpen(true);
          }}
          onOpenLyrics={() => {
            setActionsOpen(false);
            setLyricsOpen(true);
          }}
        />
      )}
      {addOpen && (
        <AddToPlaylistSheet open trackId={track.id} onClose={() => setAddOpen(false)} />
      )}
      {lyricsOpen && <LyricsSheet open onClose={() => setLyricsOpen(false)} track={track} />}
    </>
  );
}
