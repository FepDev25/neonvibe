import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import Button from './Button';
import { cn } from '@/utils/cn';

interface PlaylistFormValues {
  name: string;
  description: string;
  isPublic: boolean;
}

interface PlaylistFormProps {
  open: boolean;
  /** Pre-filled values when editing an existing playlist. */
  initial?: Partial<PlaylistFormValues>;
  title?: string;
  submitLabel?: string;
  onClose: () => void;
  onSubmit: (values: PlaylistFormValues) => void;
}

/**
 * Modal form to create/edit a playlist (name, description, public toggle).
 * Controlled by the parent; the parent runs the mutation on submit.
 */
export default function PlaylistForm({
  open,
  initial,
  title = 'Nueva playlist',
  submitLabel = 'Guardar',
  onClose,
  onSubmit,
}: PlaylistFormProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isPublic, setIsPublic] = useState(false);

  useEffect(() => {
    if (open) {
      setName(initial?.name ?? '');
      setDescription(initial?.description ?? '');
      setIsPublic(initial?.isPublic ?? false);
    }
  }, [open, initial]);

  if (!open) {
    return null;
  }

  const handleSubmit = () => {
    if (!name.trim()) {
      return;
    }
    onSubmit({ name: name.trim(), description: description.trim(), isPublic });
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
        className="w-full max-w-md rounded-2xl border border-border bg-surface p-5 safe-bottom"
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

        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium text-text">Nombre</span>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Mi playlist"
              autoFocus
              className="h-11 rounded-xl border border-border bg-surface-alt px-3 text-text placeholder:text-text-muted focus:border-neon-cyan focus:outline-none focus:ring-1 focus:ring-neon-cyan"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium text-text">Descripción</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Opcional"
              rows={2}
              className="rounded-xl border border-border bg-surface-alt px-3 py-2 text-text placeholder:text-text-muted focus:border-neon-cyan focus:outline-none focus:ring-1 focus:ring-neon-cyan"
            />
          </label>

          <button
            type="button"
            onClick={() => setIsPublic((v) => !v)}
            aria-pressed={isPublic}
            className={cn(
              'flex items-center justify-between rounded-xl border px-4 py-3 text-sm font-medium transition-colors',
              isPublic
                ? 'border-neon-cyan bg-neon-cyan/10 text-neon-cyan'
                : 'border-border bg-surface-alt text-text',
            )}
          >
            <span>Playlist pública</span>
            <span className="text-xs text-text-muted">{isPublic ? 'Visible para todos' : 'Solo tú'}</span>
          </button>

          <div className="mt-2 flex justify-end gap-2">
            <Button variant="ghost" onClick={onClose}>
              Cancelar
            </Button>
            <Button onClick={handleSubmit} disabled={!name.trim()}>
              {submitLabel}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
