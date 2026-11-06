import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Download, Trash2 } from 'lucide-react';
import { useOfflineStore } from '@/offline/offlineStore';
import Button from '@/components/Button';
import EmptyState from '@/components/EmptyState';
import Skeleton from '@/components/Skeleton';
import SortSelect, { type SortOption } from '@/components/SortSelect';
import { sortDownloads, type DownloadSortKey } from '@/utils/downloadSort';
import { formatBytes, formatDateTime } from '@/utils/format';

const SORT_OPTIONS: SortOption[] = [
  { value: 'default', label: 'Orden predeterminado' },
  { value: 'title-asc', label: 'Título (A–Z)' },
  { value: 'title-desc', label: 'Título (Z–A)' },
  { value: 'artist-asc', label: 'Artista (A–Z)' },
  { value: 'artist-desc', label: 'Artista (Z–A)' },
  { value: 'date-desc', label: 'Descarga (reciente)' },
  { value: 'date-asc', label: 'Descarga (antigua)' },
  { value: 'size-desc', label: 'Tamaño (mayor)' },
  { value: 'size-asc', label: 'Tamaño (menor)' },
];

/**
 * Offline downloads management: lists downloaded tracks with size/date and
 * lets the user delete them.
 */
export default function DownloadsPage() {
  const queryClient = useQueryClient();
  const removeTrack = useOfflineStore((s) => s.removeTrack);
  const downloads = useQuery({
    queryKey: ['downloads'],
    queryFn: () => useOfflineStore.getState().downloadedTracks(),
  });
  const [sort, setSort] = useState<DownloadSortKey>('default');
  const records = downloads.data ?? [];
  const sortedRecords = sortDownloads(records, sort);
  const totalBytes = records.reduce((acc, r) => acc + (r.size ?? 0), 0);

  const handleDelete = async (id: number) => {
    await removeTrack(id);
    void queryClient.invalidateQueries({ queryKey: ['downloads'] });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-baseline justify-between gap-3">
        <h1 className="neon-text text-2xl font-bold">Descargas</h1>
        {records.length > 0 && (
          <span className="text-xs text-text-muted">
            {records.length} · {formatBytes(totalBytes)}
          </span>
        )}
      </div>

      {downloads.isPending ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-14 w-full rounded-xl" />
          <Skeleton className="h-14 w-full rounded-xl" />
        </div>
      ) : records.length === 0 ? (
        <EmptyState
          icon={<Download className="h-10 w-10" aria-hidden />}
          title="No tienes descargas"
          description="Usa el botón Descargar en una canción, álbum o playlist para escucharla sin conexión."
          action={
            <Link to="/library">
              <Button variant="secondary">Ir a la biblioteca</Button>
            </Link>
          }
        />
      ) : (
        <div className="flex flex-col gap-1">
          {records.length > 1 && (
            <div className="flex justify-end pb-1">
              <SortSelect
                id="downloads-sort"
                value={sort}
                options={SORT_OPTIONS}
                onChange={(next) => setSort(next as DownloadSortKey)}
              />
            </div>
          )}
          {sortedRecords.map((record) => (
            <div
              key={record.id}
              className="flex min-h-[56px] items-center gap-3 rounded-xl px-3 py-2 transition-colors hover:bg-surface-alt"
            >
              <Download className="h-4 w-4 shrink-0 text-accent-text" aria-hidden />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-sm font-medium text-text">{record.title}</span>
                <span className="truncate text-xs text-text-muted">
                  {record.artist}
                  {record.album ? ` · ${record.album}` : ''}
                </span>
              </span>
              <span className="shrink-0 text-right text-[11px] text-text-muted">
                <span className="block">{formatBytes(record.size)}</span>
                <span className="block">
                  {formatDateTime(new Date(record.downloadedAt).toISOString())}
                </span>
              </span>
              <button
                type="button"
                aria-label={`Borrar descarga de ${record.title}`}
                onClick={() => void handleDelete(record.id)}
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-alt hover:text-neon-pink"
              >
                <Trash2 className="h-4 w-4" aria-hidden />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
