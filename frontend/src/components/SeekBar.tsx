import { useEffect, useRef, useState } from 'react';
import { formatDuration } from '@/utils/format';
import { cn } from '@/utils/cn';

interface SeekBarProps {
  progress: number;
  duration: number;
  onSeek: (seconds: number) => void;
  className?: string;
}

/**
 * Neon-styled seek bar. While dragging, the thumb follows the pointer via local
 * state; the seek is committed on release so it never fights timeupdate.
 */
export default function SeekBar({ progress, duration, onSeek, className }: SeekBarProps) {
  const [value, setValue] = useState(progress);
  const dragging = useRef(false);
  const valueRef = useRef(progress);
  const max = duration > 0 ? duration : 0;

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    if (!dragging.current) {
      setValue(progress);
    }
  }, [progress]);

  // Commit on any pointer release, not just one over the input: releasing the
  // thumb outside the slider used to leave `dragging` stuck and drop the seek.
  useEffect(() => {
    const onPointerUp = () => {
      if (!dragging.current) {
        return;
      }
      dragging.current = false;
      onSeek(valueRef.current);
    };
    window.addEventListener('pointerup', onPointerUp);
    return () => window.removeEventListener('pointerup', onPointerUp);
  }, [onSeek]);

  return (
    <div className={cn('flex items-center gap-2', className)}>
      <span className="w-10 shrink-0 text-right text-[10px] tabular-nums text-text-muted">
        {formatDuration(Math.floor(dragging.current ? value : progress))}
      </span>
      <input
        type="range"
        min={0}
        max={max || 1}
        step={0.5}
        value={Math.min(value, max || 0)}
        disabled={max <= 0}
        aria-label="Progreso de reproducción"
        onPointerDown={() => {
          dragging.current = true;
        }}
        onChange={(e) => {
          setValue(Number(e.target.value));
        }}
        onPointerCancel={() => {
          dragging.current = false;
          setValue(progress);
        }}
        onKeyUp={(e) => {
          if (
            e.key === 'ArrowLeft' ||
            e.key === 'ArrowRight' ||
            e.key === 'Home' ||
            e.key === 'End' ||
            e.key === 'Enter'
          ) {
            onSeek(Number((e.target as HTMLInputElement).value));
          }
        }}
        className="neon-range h-1 flex-1 cursor-pointer appearance-none rounded-full bg-surface-alt accent-neon-cyan"
      />
      <span className="w-10 shrink-0 text-right text-[10px] tabular-nums text-text-muted">
        {formatDuration(Math.floor(max))}
      </span>
    </div>
  );
}
