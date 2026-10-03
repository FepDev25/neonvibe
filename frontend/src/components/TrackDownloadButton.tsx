import { useEffect } from 'react';
import { Download, Check, Loader2 } from 'lucide-react';
import { useOfflineStore } from '@/offline/offlineStore';
import type { PlayerTrack } from '@/stores/playerStore';
import { cn } from '@/utils/cn';

interface TrackDownloadButtonProps {
  track: PlayerTrack;
  className?: string;
}

/**
 * Offline download toggle for a single track. Shows a download icon, a spinner
 * while fetching, or a check once saved (click again to remove). Backed by the
 * same IndexedDB store as the album/playlist batch download.
 */
export default function TrackDownloadButton({ track, className }: TrackDownloadButtonProps) {
  const ensureLoaded = useOfflineStore((s) => s.ensureLoaded);
  const status = useOfflineStore((s) => s.getStatus(track.id));
  const progress = useOfflineStore((s) => s.progress[track.id] ?? 0);
  const downloadTrack = useOfflineStore((s) => s.downloadTrack);
  const removeTrack = useOfflineStore((s) => s.removeTrack);

  useEffect(() => {
    void ensureLoaded();
  }, [ensureLoaded]);

  const downloading = status === 'downloading';
  const done = status === 'done';
  const label = downloading
    ? `Descargando ${Math.round(progress * 100)}%`
    : done
      ? `Eliminar descarga de ${track.title}`
      : `Descargar ${track.title}`;

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={downloading}
      onClick={() => {
        if (done) {
          void removeTrack(track.id);
        } else {
          void downloadTrack(track);
        }
      }}
      className={cn(className, (downloading || done) && 'text-neon-cyan')}
    >
      {done ? (
        <Check className="h-5 w-5" aria-hidden />
      ) : downloading ? (
        <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
      ) : (
        <Download className="h-5 w-5" aria-hidden />
      )}
    </button>
  );
}
