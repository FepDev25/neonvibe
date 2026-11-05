import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import PageHero from './PageHero';

describe('PageHero', () => {
  it('renders the section label, title, cover, metadata and actions', () => {
    render(
      <PageHero
        kind="Álbum"
        title="Infestissumam"
        cover={<img alt="Carátula" src="/cover.png" />}
        meta={<span>Ghost · 2013</span>}
        description="Un disco de estudio."
        actions={<button type="button">Reproducir</button>}
        menu={<button type="button">Más acciones</button>}
      />,
    );

    expect(screen.getByText('Álbum')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Infestissumam' })).toBeInTheDocument();
    expect(screen.getByAltText('Carátula')).toBeInTheDocument();
    expect(screen.getByText('Ghost · 2013')).toBeInTheDocument();
    expect(screen.getByText('Un disco de estudio.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Más acciones' })).toBeInTheDocument();
  });

  it('omits optional rows when there is no metadata or actions', () => {
    render(<PageHero kind="Artista" title="Ghost" cover={<span>cover</span>} />);

    expect(screen.getByRole('heading', { level: 1, name: 'Ghost' })).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
