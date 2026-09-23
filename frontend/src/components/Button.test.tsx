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
});
