import { useState, type ReactNode } from 'react';
import { Pencil } from 'lucide-react';
import Button from './Button';
import EditMetadataDialog, { type MetadataField } from './EditMetadataDialog';
import { cn } from '@/utils/cn';

interface MetadataEditButtonProps {
  title: string;
  fields: MetadataField[];
  initial: Record<string, string>;
  /** Persists the edited values (writes back to the file server-side). */
  onSave: (values: Record<string, string>) => Promise<void>;
  /** Accessible label / visible button text. */
  label: string;
  /** Icon-only circular button (used inside dense track rows). */
  compact?: boolean;
  className?: string;
  icon?: ReactNode;
}

/**
 * Pencil trigger that opens {@link EditMetadataDialog}. Kept generic so track,
 * album and artist share the same UI; each caller supplies its fields and
 * persistence callback.
 */
export default function MetadataEditButton({
  title,
  fields,
  initial,
  onSave,
  label,
  compact = false,
  className,
  icon,
}: MetadataEditButtonProps) {
  const [open, setOpen] = useState(false);
  const glyph = icon ?? <Pencil className="h-4 w-4" aria-hidden />;

  return (
    <>
      {compact ? (
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label={label}
          title={label}
          className={cn(
            'flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-text-muted transition-colors hover:text-neon-cyan',
            className,
          )}
        >
          {glyph}
        </button>
      ) : (
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => setOpen(true)}
          aria-label={label}
          title={label}
          className={className}
        >
          {glyph}
          <span>{label}</span>
        </Button>
      )}

      <EditMetadataDialog
        open={open}
        title={title}
        fields={fields}
        initial={initial}
        onSubmit={onSave}
        onClose={() => setOpen(false)}
      />
    </>
  );
}
