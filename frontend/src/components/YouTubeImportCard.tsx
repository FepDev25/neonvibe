import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardPaste,
  ExternalLink,
  Loader2,
  RotateCcw,
  Youtube,
} from 'lucide-react';
import {
  getYouTubeImportJob,
  getYouTubeStatus,
  startYouTubeImport,
  type YouTubeImportJob,
  type YouTubeJobState,
} from '@/api/youtube';
import { apiErrorMessage } from '@/utils/apiError';
import Button from './Button';
import Card from './Card';

const YOUTUBE_HOSTS = new Set([
  'youtube.com',
  'www.youtube.com',
  'm.youtube.com',
  'music.youtube.com',
  'youtu.be',
  'www.youtu.be',
]);

function looksLikeYouTube(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && YOUTUBE_HOSTS.has(url.hostname.toLowerCase());
  } catch {
    return false;
  }
}

function isTerminal(state?: YouTubeJobState): boolean {
  return state === 'SUCCEEDED' || state === 'FAILED';
}

/**
 * Admin card to import a YouTube video as a single: paste a link, the server
 * downloads it with yt-dlp, tags it and files it under the «Singles» album.
 * The import runs asynchronously in the backend and is polled here.
 */
export default function YouTubeImportCard() {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [jobId, setJobId] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  const statusQuery = useQuery({
    queryKey: ['youtube-status'],
    queryFn: getYouTubeStatus,
    retry: false,
    staleTime: 60_000,
  });

  const start = useMutation({
    mutationFn: startYouTubeImport,
    onSuccess: (job) => {
      setJobId(job.job_id);
      setUrl('');
      setLocalError(null);
    },
  });

  const jobQuery = useQuery({
    queryKey: ['youtube-job', jobId],
    queryFn: () => getYouTubeImportJob(jobId as string),
    enabled: jobId != null,
    refetchInterval: (query) => {
      const current: YouTubeImportJob | undefined = query.state.data;
      return isTerminal(current?.state) ? false : 1500;
    },
  });

  const job = jobQuery.data;
  const busy = job != null && !isTerminal(job.state);
  const available = statusQuery.data?.available === true;

  useEffect(() => {
    if (job?.state !== 'SUCCEEDED') {
      return;
    }
    void queryClient.invalidateQueries({ queryKey: ['albums'] });
    void queryClient.invalidateQueries({ queryKey: ['tracks'] });
    void queryClient.invalidateQueries({ queryKey: ['artists'] });
    void queryClient.invalidateQueries({ queryKey: ['history'] });
  }, [job?.state, queryClient]);

  const paste = async () => {
    try {
      const text = await navigator.clipboard?.readText?.();
      if (text) {
        setUrl(text.trim());
        setLocalError(null);
      }
    } catch {
      // Clipboard permission denied: the user can still paste manually.
    }
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const value = url.trim();
    if (!looksLikeYouTube(value)) {
      setLocalError('Pega un enlace de YouTube válido (https://youtube.com/… o https://youtu.be/…).');
      return;
    }
    setLocalError(null);
    setJobId(null);
    start.mutate(value);
  };

  const reset = () => {
    setJobId(null);
    setUrl('');
    setLocalError(null);
    start.reset();
  };

  const error =
    localError ??
    (start.error
      ? apiErrorMessage(start.error, 'No se pudo iniciar la importación.', {
          409: 'La importación no está disponible o ya hay una en curso.',
        })
      : null) ??
    (jobQuery.error
      ? apiErrorMessage(jobQuery.error, 'No se pudo consultar el estado de la importación.')
      : null);

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <Youtube className="h-6 w-6 text-neon-pink" aria-hidden />
        <div>
          <p className="font-semibold">Importar desde YouTube</p>
          <p className="text-sm text-text-muted">
            Pega un enlace y se descargará como single en la biblioteca.
          </p>
        </div>
      </div>

      {statusQuery.isPending ? (
        <p className="flex items-center gap-2 text-sm text-text-muted">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          Comprobando yt-dlp…
        </p>
      ) : !available ? (
        <div className="rounded-xl bg-surface-alt p-3 text-sm text-text-muted">
          <p className="text-text">
            {statusQuery.data?.message ?? 'La importación no está disponible en el servidor.'}
          </p>
          <p className="mt-1 text-xs">
            Instala <code className="rounded bg-surface px-1">yt-dlp</code> y un runtime
            JavaScript (Deno). Ver <code className="rounded bg-surface px-1">docs/DEPLOY.md</code>.
          </p>
        </div>
      ) : (
        <>
          {statusQuery.data?.message && (
            <p className="flex items-start gap-2 text-xs text-warning-text">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
              {statusQuery.data.message}
            </p>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-2 sm:flex-row">
            <label className="sr-only" htmlFor="youtube-url">
              Enlace de YouTube
            </label>
            <input
              id="youtube-url"
              type="url"
              inputMode="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://www.youtube.com/watch?v=…"
              disabled={busy}
              className="min-h-11 flex-1 rounded-xl border border-border bg-surface-alt px-3 text-sm text-text placeholder:text-text-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-text disabled:opacity-60"
            />
            <div className="flex gap-2">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => void paste()}
                disabled={busy}
              >
                <ClipboardPaste className="h-4 w-4" aria-hidden />
                Pegar
              </Button>
              <Button type="submit" size="sm" disabled={busy || url.trim() === ''}>
                {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
                Importar
              </Button>
            </div>
          </form>

          {busy && (
            <p role="status" aria-live="polite" className="flex items-center gap-2 text-sm text-text-muted">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
              {job?.stage}
            </p>
          )}

          {job?.state === 'SUCCEEDED' && (
            <div role="status" className="flex flex-col gap-2 rounded-xl bg-surface-alt p-3 text-sm">
              <p className="flex items-start gap-2 text-text">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-accent-text" aria-hidden />
                <span>
                  «{job.title}»{job.artist ? ` de ${job.artist}` : ''} añadido a Singles.
                </span>
              </p>
              <div className="flex flex-wrap items-center gap-2">
                {job.album_id != null && (
                  <Link
                    to={`/album/${job.album_id}`}
                    className="inline-flex items-center gap-1 text-sm font-medium text-accent-text hover:underline"
                  >
                    Ver Singles
                    <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                  </Link>
                )}
                <Button variant="secondary" size="sm" onClick={reset}>
                  <RotateCcw className="h-4 w-4" aria-hidden />
                  Importar otro
                </Button>
              </div>
            </div>
          )}

          {job?.state === 'FAILED' && (
            <div role="alert" className="flex items-start gap-2 rounded-xl bg-surface-alt p-3 text-sm">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-text" aria-hidden />
              <div className="min-w-0">
                <p className="text-text">{job.error ?? 'No se pudo importar el vídeo.'}</p>
                <Button variant="secondary" size="sm" className="mt-2" onClick={reset}>
                  <RotateCcw className="h-4 w-4" aria-hidden />
                  Reintentar
                </Button>
              </div>
            </div>
          )}

          {error && (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          )}
        </>
      )}
    </Card>
  );
}
