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
});
