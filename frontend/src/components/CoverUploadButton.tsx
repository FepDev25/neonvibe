import { useEffect, useRef, useState } from 'react';
import { ImagePlus, Check, Loader2 } from 'lucide-react';
import { cn } from '@/utils/cn';
import Button from './Button';

/** Matches CoverController.MAX_UPLOAD_BYTES (10 MB). */
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

const ALLOWED_MIME_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

type Status = 'idle' | 'uploading' | 'success' | 'error';

interface CoverUploadButtonProps {
  /** Uploads the picked file. Kept entity-agnostic so album and artist detail
   * share this component. */
  onUpload: (file: File) => Promise<unknown>;
  /** Called after a successful upload (e.g. to invalidate queries/cache-bust). */
  onUploaded?: () => void;
  label?: string;
  /** Icon-only rendering, for overlaying on top of the cover/avatar. */
  compact?: boolean;
  className?: string;
}

/**
 * Manual cover upload: a labelled file input (hidden) plus a status-aware
 * button. Validates MIME type and size client-side before hitting the API.
 */
export default function CoverUploadButton({
  onUpload,
  onUploaded,
  label = 'Subir carátula',
  compact = false,
  className,
}: CoverUploadButtonProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<Status>('idle');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (status !== 'success') return;
    const timer = setTimeout(() => setStatus('idle'), 2500);
    return () => clearTimeout(timer);
  }, [status]);

  const openPicker = () => {
    if (status === 'uploading') return;
    setError(null);
    inputRef.current?.click();
  };

  const handleChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const input = event.target;
    const file = input.files?.[0];
    // Allow re-selecting the same file after an error.
    input.value = '';
    if (!file) return;

    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      setStatus('error');
      setError('Formato no permitido. Usa PNG, JPEG o WebP.');
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setStatus('error');
      setError('La imagen supera el límite de 10 MB.');
      return;
    }

    setStatus('uploading');
    setError(null);
    try {
      await onUpload(file);
      setStatus('success');
      onUploaded?.();
    } catch {
      setStatus('error');
      setError('No se pudo subir la carátula. Inténtalo de nuevo.');
    }
  };

  const icon =
    status === 'uploading' ? (
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
    ) : status === 'success' ? (
      <Check className="h-4 w-4 text-neon-cyan" aria-hidden />
    ) : (
      <ImagePlus className="h-4 w-4" aria-hidden />
    );

  return (
    <div className={cn('flex flex-col items-start gap-1', className)}>
      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp"
        aria-label="Archivo de carátula"
        className="sr-only"
        onChange={(e) => void handleChange(e)}
      />
      <Button
        type="button"
        variant="secondary"
        size="sm"
        aria-label={label}
        title={label}
        disabled={status === 'uploading'}
        onClick={openPicker}
        className={cn(compact && 'h-9 w-9 rounded-full p-0')}
      >
        {icon}
        {!compact && (
          <span>
            {status === 'uploading'
              ? 'Subiendo…'
              : status === 'success'
                ? 'Subida'
                : label}
          </span>
        )}
      </Button>
      {error && (
        <p role="alert" className="max-w-[12rem] text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
