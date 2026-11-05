import { useCallback } from 'react';
import { usePlayerStore, type PlayerTrack } from '@/stores/playerStore';

/**
 * Play/shuffle helpers for a collection (album, artist, playlist, favorites).
 *
 * `play` starts at the first track in order. `shufflePlay` starts at a random
 * track and turns shuffle on without toggling it off if it was already active.
 */
export function useCollectionPlayback(queue: PlayerTrack[]) {
  const playTrack = usePlayerStore((s) => s.playTrack);
  const toggleShuffle = usePlayerStore((s) => s.toggleShuffle);

  const play = useCallback(() => {
    if (queue.length === 0) {
      return;
    }
    playTrack(queue[0], queue);
  }, [playTrack, queue]);

  const shufflePlay = useCallback(() => {
    if (queue.length === 0) {
      return;
    }
    const start = Math.floor(Math.random() * queue.length);
    playTrack(queue[start], queue);
    if (!usePlayerStore.getState().shuffle) {
      toggleShuffle();
    }
  }, [playTrack, queue, toggleShuffle]);

  return { play, shufflePlay, disabled: queue.length === 0 };
}
