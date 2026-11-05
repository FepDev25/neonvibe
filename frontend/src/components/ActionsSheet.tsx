import { useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { useModalA11y } from '@/hooks/useModalA11y';

/** Shared row style for actions inside a bottom sheet. */
export const ACTION_ROW =
  'flex min-h-[48px] w-full items-center gap-3 rounded-xl px-3 text-left text-sm text-text transition-colors hover:bg-surface-alt disabled:cursor-not-allowed disabled:opacity-40';

interface ActionsSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
}

/**
 * Generic mobile-first action sheet used by track rows. On desktop it becomes a
 * centered dialog; the action rows are supplied by each row type.
 */
export default function ActionsSheet({
  open,
  onClose,
  title,
  description,
  children,
}: ActionsSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null);
  useModalA11y(open, onClose, sheetRef);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-overlay sm:items-center"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        ref={sheetRef}
        tabIndex={-1}
        className="flex max-h-[75vh] w-full max-w-md flex-col rounded-t-2xl border border-border bg-surface safe-bottom outline-none sm:rounded-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 className="truncate text-base font-bold text-text" title={title}>
              {title}
            </h2>
            {description && (
              <p className="truncate text-xs text-text-muted">{description}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-2 [&_button]:w-full [&_button]:justify-start">
          {children}
        </div>
      </div>
    </div>
  );
}
