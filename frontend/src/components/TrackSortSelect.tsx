import SortSelect, { type SortOption } from './SortSelect';
import type { TrackSortKey } from '@/utils/trackSort';

const TRACK_OPTIONS: SortOption[] = [
  { value: 'title-asc', label: 'Título (A–Z)' },
  { value: 'title-desc', label: 'Título (Z–A)' },
  { value: 'artist-asc', label: 'Artista (A–Z)' },
  { value: 'artist-desc', label: 'Artista (Z–A)' },
  { value: 'album-asc', label: 'Álbum (A–Z)' },
  { value: 'album-desc', label: 'Álbum (Z–A)' },
  { value: 'duration-asc', label: 'Duración (menor)' },
  { value: 'duration-desc', label: 'Duración (mayor)' },
  { value: 'year-asc', label: 'Año (antiguo)' },
  { value: 'year-desc', label: 'Año (reciente)' },
];

interface TrackSortSelectProps {
  id: string;
  value: TrackSortKey;
  onChange: (value: TrackSortKey) => void;
  /** Label for the default (original) order. */
  defaultLabel?: string;
  /** Album column makes no sense inside a single album. */
  includeAlbum?: boolean;
  className?: string;
}

/** Sort control for track lists. */
export default function TrackSortSelect({
  id,
  value,
  onChange,
  defaultLabel = 'Orden predeterminado',
  includeAlbum = false,
  className,
}: TrackSortSelectProps) {
  const options: SortOption[] = [
    { value: 'default', label: defaultLabel },
    ...TRACK_OPTIONS.filter((option) => includeAlbum || !option.value.startsWith('album-')),
  ];

  return (
    <SortSelect
      id={id}
      value={value}
      options={options}
      onChange={(next) => onChange(next as TrackSortKey)}
      className={className}
    />
  );
}
