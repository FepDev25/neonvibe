import { useEffect, type RefObject } from 'react';

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/**
 * Accessibility for modal dialogs: moves focus into the dialog on open, traps
 * Tab/Shift+Tab inside it, closes on Escape and restores focus to the element
 * that was focused before opening.
 *
 * @param open      whether the dialog is mounted/visible
 * @param onClose   called on Escape
 * @param container ref to the dialog element (must be focusable, `tabIndex={-1}`)
 */
export function useModalA11y(
  open: boolean,
  onClose: () => void,
  container: RefObject<HTMLElement | null>,
): void {
  useEffect(() => {
    if (!open) {
      return;
    }
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const root = container.current;

    const focusables = (): HTMLElement[] => {
      if (!root) {
        return [];
      }
      return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    };

    // Only steal focus if something inside is not already focused (e.g. an
    // input with autoFocus has already claimed it).
    if (!root?.contains(document.activeElement)) {
      (focusables()[0] ?? root)?.focus();
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }
      const items = focusables();
      if (items.length === 0) {
        event.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !root?.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      previouslyFocused?.focus?.();
    };
  }, [open, onClose, container]);
}
