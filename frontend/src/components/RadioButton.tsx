import { useState } from 'react';
import { Radio, Loader2 } from 'lucide-react';
import { getRadioSeed } from '@/api/radio';
import { tracksToPlayerQueue, usePlayerStore } from '@/stores/playerStore';
import Button from './Button';

interface RadioButtonProps {
  trackId: number;
  label?: string;
  size?: 'sm' | 'md';
  variant?: 'secondary' | 'ghost';
}

/**
 * Starts a similarity radio from a seed track: fetches the radio queue and
 * plays it immediately.
 */
export default function RadioButton({ trackId, label = 'Radio', size = 'sm', variant = 'secondary' }: RadioButtonProps) {
  const playTrack = usePlayerStore((s) => s.playTrack);
  const [busy, setBusy] = useState(false);

  const start = async () => {
    setBusy(true);
    try {
      const tracks = await getRadioSeed(trackId, 20);
      if (tracks.length > 0) {
        const queue = tracksToPlayerQueue(tracks);
        playTrack(queue[0], queue);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button variant={variant} size={size} onClick={() => void start()} disabled={busy}>
      {busy ? (
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      ) : (
        <Radio className="h-4 w-4" aria-hidden />
      )}
      {label}
    </Button>
  );
}
