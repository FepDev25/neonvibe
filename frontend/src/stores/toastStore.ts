import { create } from 'zustand';

export interface ToastOptions {
  /** Label for the action button (e.g. "Deshacer"). */
  actionLabel?: string;
  onAction?: () => void;
  /** Auto-dismiss delay in ms. `0` keeps it until dismissed. Default 6000. */
  duration?: number;
}

export interface Toast {
  id: number;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  duration: number;
}

interface ToastState {
  toasts: Toast[];
  show: (message: string, options?: ToastOptions) => number;
  dismiss: (id: number) => void;
}

let nextId = 1;

/**
 * Minimal toast store (B4 undo base, reused by later blocks). Toasts auto-expire;
 * `duration: 0` keeps them until the user acts or closes them.
 */
export const useToastStore = create<ToastState>()((set, get) => ({
  toasts: [],

  show: (message, options = {}) => {
    const id = nextId++;
    const duration = options.duration ?? 6000;
    set((state) => ({
      toasts: [
        ...state.toasts,
        { id, message, actionLabel: options.actionLabel, onAction: options.onAction, duration },
      ],
    }));
    if (duration > 0) {
      setTimeout(() => get().dismiss(id), duration);
    }
    return id;
  },

  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));
