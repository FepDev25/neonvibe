import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Button from './Button';

describe('Button', () => {
  it('renders its children', () => {
    render(<Button>Reproducir</Button>);
    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeInTheDocument();
  });

  it('defaults to type="button" so it never submits forms', () => {
    render(<Button>Enviar</Button>);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={onClick}>Play</Button>);

    await user.click(screen.getByRole('button', { name: 'Play' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not fire when disabled', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <Button disabled onClick={onClick}>
        Play
      </Button>,
    );

    await user.click(screen.getByRole('button', { name: 'Play' }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('keeps a >= 44px touch target in every size', () => {
    const { rerender } = render(<Button size="sm">Pequeño</Button>);
    expect(screen.getByRole('button')).toHaveClass('h-11');

    rerender(<Button size="lg">Grande</Button>);
    expect(screen.getByRole('button')).toHaveClass('h-12');
  });

  it('does not suppress the global focus ring', async () => {
    const user = userEvent.setup();
    render(<Button>Reproducir</Button>);

    await user.tab();
    const button = screen.getByRole('button', { name: 'Reproducir' });
    expect(button).toHaveFocus();
    expect(button).not.toHaveClass('focus:outline-none');
  });
});
