import { useQueryClient } from '@tanstack/react-query';
import { updateAlbumMetadata } from '@/api/metadata';
import MetadataEditButton from './MetadataEditButton';
import type { MetadataField } from './EditMetadataDialog';
import { toNumberOrNull, toTextOrNull } from '@/utils/metadataValues';
import type { Album } from '@/types';

const ALBUM_FIELDS: MetadataField[] = [
  { key: 'name', label: 'Nombre del álbum', required: true },
  { key: 'year', label: 'Año', type: 'number', placeholder: '1999' },
  { key: 'genre', label: 'Género' },
];

interface AlbumEditButtonProps {
  album: Album;
}

/** Edits album-wide metadata, propagated to every track file of the album. */
export default function AlbumEditButton({ album }: AlbumEditButtonProps) {
  const queryClient = useQueryClient();
  // The reserved Singles album cannot be renamed; year/genre still editable.
  const isSingles = album.name.toLowerCase() === 'singles' && !album.artist;
  const fields = isSingles ? ALBUM_FIELDS.filter((field) => field.key !== 'name') : ALBUM_FIELDS;

  const initial: Record<string, string> = {
    name: album.name ?? '',
    year: album.year != null ? String(album.year) : '',
    genre: album.genre ?? '',
  };

  const handleSave = async (values: Record<string, string>) => {
    await updateAlbumMetadata(album.id, {
      name: (values.name ?? album.name).trim(),
      year: toNumberOrNull(values.year),
      genre: toTextOrNull(values.genre),
    });
    // The edit is committed; a background refresh failure must not look like a
    // failed save in the dialog.
    await Promise.allSettled([
      queryClient.invalidateQueries({ queryKey: ['albums'] }),
      queryClient.invalidateQueries({ queryKey: ['album'] }),
      queryClient.invalidateQueries({ queryKey: ['tracks'] }),
      queryClient.invalidateQueries({ queryKey: ['artists'] }),
    ]);
  };

  return (
    <MetadataEditButton
      label="Editar álbum"
      title="Editar álbum"
      fields={fields}
      initial={initial}
      onSave={handleSave}
    />
  );
}
