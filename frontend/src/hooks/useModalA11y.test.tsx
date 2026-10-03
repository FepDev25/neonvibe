import { useRef } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useModalA11y } from './useModalA11y';

function Modal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useModalA11y(open, onClose, ref);
  if (!open) {
    return null;
  }
  return (
    <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label="test">
      <button type="button">first</button>
      <button type="button">last</button>
    </div>
  );
}

describe('useModalA11y', () => {
  it('closes on Escape', () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} />);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('moves focus into the dialog on open', () => {
    render(<Modal open onClose={() => {}} />);

    expect(document.activeElement).toBe(screen.getByText('first'));
  });

  it('traps Tab from the last focusable back to the first', () => {
    render(<Modal open onClose={() => {}} />);
    const last = screen.getByText('last');
    last.focus();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));

    expect(document.activeElement).toBe(screen.getByText('first'));
  });

  it('does nothing while closed', () => {
    const onClose = vi.fn();
    render(<Modal open={false} onClose={onClose} />);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(onClose).not.toHaveBeenCalled();
  });
});
