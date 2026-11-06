import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import TrackSortSelect from './TrackSortSelect';

describe('TrackSortSelect', () => {
  it('shows the default label and notifies changes', () => {
    const onChange = vi.fn();
    render(
      <TrackSortSelect
        id="album-sort"
        value="default"
        onChange={onChange}
        defaultLabel="Orden del álbum"
      />,
    );

    expect(screen.getByRole('option', { name: 'Orden del álbum' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Ordenar por'), {
      target: { value: 'title-desc' },
    });
    expect(onChange).toHaveBeenCalledWith('title-desc');
  });

  it('hides the album options inside a single album', () => {
    render(
      <TrackSortSelect id="album-sort" value="default" onChange={vi.fn()} />,
    );

    expect(screen.queryByRole('option', { name: 'Álbum (A–Z)' })).not.toBeInTheDocument();
  });

  it('shows album options where the list can mix albums', () => {
    render(
      <TrackSortSelect id="playlist-sort" value="default" onChange={vi.fn()} includeAlbum />,
    );

    expect(screen.getByRole('option', { name: 'Álbum (A–Z)' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Álbum (Z–A)' })).toBeInTheDocument();
  });
});
