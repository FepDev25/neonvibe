import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Toaster from './Toaster';
import { useToastStore } from '@/stores/toastStore';

describe('Toaster', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  it('renders nothing without toasts', () => {
    const { container } = render(<Toaster />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the message and runs the undo action', async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    useToastStore.getState().show('«Song» quitada de la playlist', {
      actionLabel: 'Deshacer',
      onAction,
      duration: 0,
    });

    render(<Toaster />);
    expect(screen.getByRole('status')).toHaveTextContent('«Song» quitada de la playlist');

    await user.click(screen.getByRole('button', { name: 'Deshacer' }));

    expect(onAction).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('can be dismissed with the close button', async () => {
    const user = userEvent.setup();
    useToastStore.getState().show('Aviso', { duration: 0 });
    render(<Toaster />);

    await user.click(screen.getByRole('button', { name: 'Cerrar aviso' }));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
