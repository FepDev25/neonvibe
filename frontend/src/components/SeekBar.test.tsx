import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SeekBar from './SeekBar';

describe('SeekBar', () => {
  it('commits the seek when the pointer is released outside the input', () => {
    const onSeek = vi.fn();
    const { container } = render(<SeekBar progress={0} duration={100} onSeek={onSeek} />);
    const input = container.querySelector('input')!;

    fireEvent.pointerDown(input);
    fireEvent.change(input, { target: { value: '42' } });
    // Released over the document, not the slider: the seek must still commit.
    fireEvent.pointerUp(window);

    expect(onSeek).toHaveBeenCalledWith(42);
  });

  it('exposes the played fraction as a --fill percentage on the range', () => {
    const { container } = render(<SeekBar progress={25} duration={100} onSeek={() => {}} />);
    const input = container.querySelector('input')!;

    expect(input.style.getPropertyValue('--fill')).toBe('25%');
  });
});
