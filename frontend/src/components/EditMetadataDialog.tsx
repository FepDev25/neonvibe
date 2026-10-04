import { useEffect, useRef, useState } from 'react';
import { Loader2, X } from 'lucide-react';
import Button from './Button';
import { useModalA11y } from '@/hooks/useModalA11y';
import { apiErrorMessage } from '@/utils/apiError';

export interface MetadataField {
  key: string;
  label: string;
  type?: 'text' | 'number';
  placeholder?: string;
  required?: boolean;
}

interface EditMetadataDialogProps {
  open: boolean;
  title: string;
  fields: MetadataField[];
  /** Current values as strings; missing keys start empty. */
  initial: Record<string, string>;
  submitLabel?: string;
  /** Persists the edited values. Reject to surface an error and keep it open. */
  onSubmit: (values: Record<string, string>) => Promise<void>;
  onClose: () => void;
}

/**
 * Generic metadata edit dialog driven by a field list. Writes back to the
 * server on submit (which persists to the audio file) and stays open showing
 * the error if it fails.
 */
export default function EditMetadataDialog({
  open,
  title,
  fields,
  initial,
  submitLabel = 'Guardar',
  onSubmit,
  onClose,
}: EditMetadataDialogProps) {
  const [values, setValues] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const wasOpen = useRef(false);
  useModalA11y(open, onClose, dialogRef);

  // Seed the form only on the closed -> open transition, so parent re-renders
  // (e.g. while submitting) don't wipe what the user typed.
  useEffect(() => {
    if (open && !wasOpen.current) {
      setValues({ ...initial });
      setError(null);
      setSubmitting(false);
    }
    wasOpen.current = open;
  }, [open, initial]);

  if (!open) {
    return null;
  }

  const missingRequired = fields.some((f) => f.required && !values[f.key]?.trim());

  const handleSubmit = async () => {
    if (missingRequired) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(values);
      onClose();
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo guardar la metadata.', {
        403: 'Solo los administradores pueden editar la metadata.',
      }));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 p-4 sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        ref={dialogRef}
        tabIndex={-1}
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-2xl border border-border bg-surface p-5 safe-bottom outline-none"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="neon-text text-lg font-bold">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="flex h-9 w-9 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void handleSubmit();
          }}
        >
          {fields.map((field) => (
            <label key={field.key} className="flex flex-col gap-1">
              <span className="text-sm font-medium text-text">
                {field.label}
                {field.required && <span className="text-neon-cyan"> *</span>}
              </span>
              <input
                type={field.type ?? 'text'}
                value={values[field.key] ?? ''}
                onChange={(e) => setValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                placeholder={field.placeholder}
                autoFocus={field.required}
                className="h-11 rounded-xl border border-border bg-surface-alt px-3 text-text placeholder:text-text-muted focus:border-neon-cyan focus:outline-none focus:ring-1 focus:ring-neon-cyan"
              />
            </label>
          ))}

          {error && (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          )}

          <p className="text-xs text-text-muted">
            Se escriben los tags en el archivo original del servidor.
          </p>

          <div className="mt-1 flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
              Cancelar
            </Button>
            <Button type="submit" disabled={submitting || missingRequired}>
              {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              {submitting ? 'Guardando…' : submitLabel}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
