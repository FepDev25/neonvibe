import { X } from 'lucide-react';
import { useToastStore } from '@/stores/toastStore';

/**
 * Renders the active toasts above the player/tabs. Undo actions dismiss the
 * toast immediately; the destructive operation is already committed and the
 * action callback restores it.
 */
export default function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);

  if (toasts.length === 0) {
    return null;
  }

  return (
    <div
      aria-live="polite"
      aria-atomic="false"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(9rem+env(safe-area-inset-bottom))] z-[70] flex flex-col items-center gap-2 px-4 lg:bottom-28"
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          role="status"
          className="pointer-events-auto flex w-full max-w-sm items-center gap-2 rounded-xl border border-border bg-surface-raised px-3 py-2 shadow-xl"
        >
          <p className="min-w-0 flex-1 text-sm text-text">{toast.message}</p>
          {toast.actionLabel && (
            <button
              type="button"
              onClick={() => {
                toast.onAction?.();
                dismiss(toast.id);
              }}
              className="min-h-[44px] shrink-0 rounded-lg px-2 text-sm font-semibold text-accent-text hover:bg-surface-alt"
            >
              {toast.actionLabel}
            </button>
          )}
          <button
            type="button"
            onClick={() => dismiss(toast.id)}
            aria-label="Cerrar aviso"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-muted hover:bg-surface-alt"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
      ))}
    </div>
  );
}
