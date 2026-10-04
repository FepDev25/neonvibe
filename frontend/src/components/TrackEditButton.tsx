import { useQueryClient } from '@tanstack/react-query';
import { updateTrackMetadata } from '@/api/metadata';
import MetadataEditButton from './MetadataEditButton';
import type { MetadataField } from './EditMetadataDialog';
import { toNumberOrNull, toTextOrNull } from '@/utils/metadataValues';
import type { Track } from '@/types';

const TRACK_FIELDS: MetadataField[] = [
  { key: 'title', label: 'Título', required: true },
  { key: 'artist', label: 'Artista' },
  { key: 'album', label: 'Álbum' },
  { key: 'album_artist', label: 'Artista del álbum' },
  { key: 'year', label: 'Año', type: 'number', placeholder: '1999' },
  { key: 'genre', label: 'Género' },
  { key: 'track_number', label: 'Nº de pista', type: 'number' },
  { key: 'disc_number', label: 'Nº de disco', type: 'number' },
];

interface TrackEditButtonProps {
  track: Track;
  className?: string;
}

/** Pencil action to edit a single track's tags (writes them to the file). */
export default function TrackEditButton({ track, className }: TrackEditButtonProps) {
  const queryClient = useQueryClient();

  const initial: Record<string, string> = {
    title: track.title ?? '',
    artist: track.artist ?? '',
    album: track.album ?? '',
    album_artist: track.album_artist ?? '',
    year: track.year != null ? String(track.year) : '',
    genre: track.genre ?? '',
    track_number: track.track_number != null ? String(track.track_number) : '',
    disc_number: track.disc_number != null ? String(track.disc_number) : '',
  };

  const handleSave = async (values: Record<string, string>) => {
    await updateTrackMetadata(track.id, {
      title: values.title.trim(),
      artist: toTextOrNull(values.artist),
      album: toTextOrNull(values.album),
      album_artist: toTextOrNull(values.album_artist),
      year: toNumberOrNull(values.year),
      genre: toTextOrNull(values.genre),
      track_number: toNumberOrNull(values.track_number),
      disc_number: toNumberOrNull(values.disc_number),
    });
    // A track edit can regroup albums/artists, so refresh the whole library.
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['tracks'] }),
      queryClient.invalidateQueries({ queryKey: ['albums'] }),
      queryClient.invalidateQueries({ queryKey: ['artists'] }),
      queryClient.invalidateQueries({ queryKey: ['album'] }),
      queryClient.invalidateQueries({ queryKey: ['artist'] }),
    ]);
  };

  return (
    <MetadataEditButton
      compact
      label={`Editar metadata de ${track.title}`}
      title="Editar canción"
      fields={TRACK_FIELDS}
      initial={initial}
      onSave={handleSave}
      className={className}
    />
  );
}
