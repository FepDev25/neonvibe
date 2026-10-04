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

  const initial: Record<string, string> = {
    name: album.name ?? '',
    year: album.year != null ? String(album.year) : '',
    genre: album.genre ?? '',
  };

  const handleSave = async (values: Record<string, string>) => {
    await updateAlbumMetadata(album.id, {
      name: values.name.trim(),
      year: toNumberOrNull(values.year),
      genre: toTextOrNull(values.genre),
    });
    await Promise.all([
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
      fields={ALBUM_FIELDS}
      initial={initial}
      onSave={handleSave}
    />
  );
}
