import { useRef, useState } from 'react';
import { UploadCloud, X, Loader2, CheckCircle2, AlertTriangle } from 'lucide-react';
import { uploadTracks, type UploadResult } from '@/api/upload';
import { apiErrorMessage } from '@/utils/apiError';
import Button from './Button';
import Card from './Card';
import { cn } from '@/utils/cn';

const ACCEPT = 'audio/*,.mp3,.flac,.m4a,.aac,.ogg,.wav,.zip';

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Admin card to upload new albums/tracks. Accepts several audio files or a ZIP;
 * the server organizes them into the library (writes tags to each file and
 * ingests them).
 */
export default function UploadCard() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<UploadResult | null>(null);

  const addFiles = (list: FileList | null) => {
    if (!list || list.length === 0) {
      return;
    }
    setFiles((prev) => [...prev, ...Array.from(list)]);
    setResult(null);
    setError(null);
  };

  const removeAt = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpload = async () => {
    if (files.length === 0 || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await uploadTracks(files));
      setFiles([]);
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudieron subir los archivos.', {
        403: 'Solo los administradores pueden subir música.',
        413: 'Los archivos superan el tamaño máximo permitido.',
      }));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <UploadCloud className="h-6 w-6 text-neon-cyan" aria-hidden />
        <div>
          <p className="font-semibold">Subir música</p>
          <p className="text-sm text-text-muted">
            Añade canciones o un ZIP; se organizan en la biblioteca.
          </p>
        </div>
      </div>

      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          addFiles(e.dataTransfer.files);
        }}
        className={cn(
          'flex flex-col items-center gap-2 rounded-xl border border-dashed px-4 py-6 text-center transition-colors',
          dragging ? 'border-neon-cyan bg-neon-cyan/5' : 'border-border',
        )}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={ACCEPT}
          aria-label="Archivos de audio"
          className="sr-only"
          onChange={(e) => {
            addFiles(e.target.files);
            e.target.value = '';
          }}
        />
        <Button variant="secondary" size="sm" onClick={() => inputRef.current?.click()} disabled={busy}>
          Elegir archivos
        </Button>
        <p className="text-xs text-text-muted">
          o arrastra aquí MP3, FLAC, M4A, AAC, OGG, WAV o un ZIP
        </p>
      </div>

      {files.length > 0 && (
        <ul className="flex flex-col gap-1">
          {files.map((file, index) => (
            <li
              key={`${file.name}-${index}`}
              className="flex items-center justify-between gap-2 rounded-lg bg-surface-alt px-3 py-2 text-sm"
            >
              <span className="min-w-0 flex-1 truncate text-text">{file.name}</span>
              <span className="shrink-0 text-xs text-text-muted">{formatSize(file.size)}</span>
              <button
                type="button"
                onClick={() => removeAt(index)}
                aria-label={`Quitar ${file.name}`}
                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-text-muted hover:text-neon-pink"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p role="alert" className="text-sm text-red-400">
          {error}
        </p>
      )}

      {result && (
        <div className="flex items-start gap-2 text-sm">
          {result.failed > 0 ? (
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-neon-yellow" aria-hidden />
          ) : (
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-neon-cyan" aria-hidden />
          )}
          <div className="min-w-0">
            <p className="text-text">
              {result.processed} añadida{result.processed === 1 ? '' : 's'}
              {result.failed > 0 ? `, ${result.failed} con error` : ''}.
            </p>
            {result.errors.length > 0 && (
              <ul className="mt-1 list-inside list-disc text-xs text-text-muted">
                {result.errors.slice(0, 5).map((message) => (
                  <li key={message} className="truncate">
                    {message}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      <Button
        className="self-start"
        onClick={handleUpload}
        disabled={busy || files.length === 0}
      >
        {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
        {busy ? 'Subiendo…' : `Subir ${files.length > 0 ? `(${files.length})` : ''}`}
      </Button>
    </Card>
  );
}
