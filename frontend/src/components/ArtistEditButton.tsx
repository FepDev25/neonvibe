import { useQueryClient } from '@tanstack/react-query';
import { updateArtistMetadata } from '@/api/metadata';
import MetadataEditButton from './MetadataEditButton';
import type { MetadataField } from './EditMetadataDialog';
import type { Artist } from '@/types';

const ARTIST_FIELDS: MetadataField[] = [
  { key: 'name', label: 'Nombre del artista', required: true },
];

interface ArtistEditButtonProps {
  artist: Artist;
}

/** Renames an artist, propagated to every track file and its albums. */
export default function ArtistEditButton({ artist }: ArtistEditButtonProps) {
  const queryClient = useQueryClient();

  const handleSave = async (values: Record<string, string>) => {
    await updateArtistMetadata(artist.id, { name: values.name.trim() });
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['artists'] }),
      queryClient.invalidateQueries({ queryKey: ['artist'] }),
      queryClient.invalidateQueries({ queryKey: ['albums'] }),
      queryClient.invalidateQueries({ queryKey: ['tracks'] }),
    ]);
  };

  return (
    <MetadataEditButton
      label="Editar artista"
      title="Editar artista"
      fields={ARTIST_FIELDS}
      initial={{ name: artist.name ?? '' }}
      onSave={handleSave}
    />
  );
}
