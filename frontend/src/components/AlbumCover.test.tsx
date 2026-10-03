import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AlbumCover from './AlbumCover';

describe('AlbumCover', () => {
  it('falls back to the placeholder when the image fails', () => {
    const { container } = render(<AlbumCover seed="a" alt="A" src="/cover/1" />);

    fireEvent.error(container.querySelector('img')!);

    expect(container.querySelector('img')).toBeNull();
  });

  it('retries a different src after the previous one failed', () => {
    const { container, rerender } = render(<AlbumCover seed="a" alt="A" src="/cover/1" />);
    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('img')).toBeNull();

    rerender(<AlbumCover seed="a" alt="A" src="/cover/2" />);

    expect(container.querySelector('img')).not.toBeNull();
  });
});
