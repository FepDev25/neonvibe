import UploadCard from '@/components/UploadCard';

/**
 * Dedicated page to upload new albums/tracks (admin). The heavy lifting
 * (organize by artist/album, write tags, ingest) happens server-side.
 */
export default function UploadPage() {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="neon-text text-2xl font-bold">Subir música</h1>
        <p className="text-sm text-text-muted">
          Añade canciones o un ZIP; se organizan en la biblioteca por artista y álbum.
        </p>
      </div>

      <UploadCard />

      <div className="rounded-2xl border border-border bg-surface p-4 text-sm text-text-muted">
        <p className="font-medium text-text">¿Lotes grandes?</p>
        <p className="mt-1">
          Copia los archivos en{' '}
          <code className="rounded bg-surface-alt px-1">/srv/Music/incoming</code> por
          SFTP/rsync y el servidor los organiza solo. La subida web está limitada a
          ~100&nbsp;MB por petición (Cloudflare).
        </p>
      </div>
    </div>
  );
}
