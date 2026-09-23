import { useEffect, useState } from 'react';
import { Download, Check, Loader2 } from 'lucide-react';
import { useOfflineStore } from '@/offline/offlineStore';
import Button from './Button';
import type { PlayerTrack } from '@/stores/playerStore';

interface DownloadButtonProps {
  tracks: PlayerTrack[];
}

/**
 * Batch offline download for an album/playlist. Shows: not downloaded,
 * downloading (with progress), or downloaded (with delete).
 */
export default function DownloadButton({ tracks }: DownloadButtonProps) {
  const ensureLoaded = useOfflineStore((s) => s.ensureLoaded);
  const statuses = useOfflineStore((s) => s.statuses);
  const progress = useOfflineStore((s) => s.progress);
  const downloadBatch = useOfflineStore((s) => s.downloadBatch);
  const removeTrack = useOfflineStore((s) => s.removeTrack);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void ensureLoaded();
  }, [ensureLoaded]);

  if (tracks.length === 0) {
    return null;
  }

  const allDone = tracks.every((t) => statuses[t.id] === 'done');
  const downloading = tracks.some((t) => statuses[t.id] === 'downloading');
  const avg =
    tracks.reduce((acc, t) => acc + (progress[t.id] ?? 0), 0) / Math.max(1, tracks.length);
  const pct = Math.round(avg * 100);

  const handleDownload = async () => {
    setBusy(true);
    await downloadBatch(tracks);
    setBusy(false);
  };

  const handleDelete = async () => {
    setBusy(true);
    await Promise.all(tracks.map((t) => removeTrack(t.id)));
    setBusy(false);
  };

  if (allDone) {
    return (
      <Button variant="secondary" size="sm" onClick={handleDelete} disabled={busy}>
        <Check className="h-4 w-4 text-neon-cyan" aria-hidden />
        Descargado
      </Button>
    );
  }

  if (downloading || busy) {
    return (
      <Button variant="secondary" size="sm" disabled>
        {pct > 0 ? (
          <Download className="h-4 w-4" aria-hidden />
        ) : (
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
        )}
        {pct > 0 ? `${pct}%` : 'Descargando…'}
      </Button>
    );
  }

  return (
    <Button variant="secondary" size="sm" onClick={handleDownload}>
      <Download className="h-4 w-4" aria-hidden />
      Descargar
    </Button>
  );
}
