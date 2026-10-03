import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SearchInput from './SearchInput';

describe('SearchInput', () => {
  it('calls onChange as the user types', () => {
    const onChange = vi.fn();
    render(<SearchInput value="" onChange={onChange} label="Buscar álbumes" />);

    fireEvent.change(screen.getByLabelText('Buscar álbumes'), {
      target: { value: 'floyd' },
    });

    expect(onChange).toHaveBeenCalledWith('floyd');
  });

  it('shows a clear button only with a value and clears on click', () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <SearchInput value="" onChange={onChange} label="Buscar" />,
    );

    expect(screen.queryByLabelText('Limpiar búsqueda')).not.toBeInTheDocument();

    rerender(<SearchInput value="pink" onChange={onChange} label="Buscar" />);
    fireEvent.click(screen.getByLabelText('Limpiar búsqueda'));

    expect(onChange).toHaveBeenCalledWith('');
  });
});
