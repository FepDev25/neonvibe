import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import IconButton from './IconButton';

describe('IconButton', () => {
  it('exposes an accessible name and a 48px touch target', () => {
    render(
      <IconButton label="Reproducir">
        <span aria-hidden>▶</span>
      </IconButton>,
    );

    const button = screen.getByRole('button', { name: 'Reproducir' });
    expect(button).toHaveClass('h-12', 'w-12');
  });

  it('is keyboard reachable and does not suppress the global focus ring', async () => {
    const user = userEvent.setup();
    render(
      <IconButton label="Siguiente">
        <span aria-hidden>⏭</span>
      </IconButton>,
    );

    await user.tab();
    const button = screen.getByRole('button', { name: 'Siguiente' });
    expect(button).toHaveFocus();
    expect(button).not.toHaveClass('focus:outline-none');
  });

  it('fires onClick when activated', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <IconButton label="Pausar" onClick={onClick}>
        <span aria-hidden>⏸</span>
      </IconButton>,
    );

    await user.click(screen.getByRole('button', { name: 'Pausar' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
