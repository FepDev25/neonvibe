import { useInfiniteQuery } from '@tanstack/react-query';
import { getHistory } from '@/api/history';

/** Paged playback history (newest first). */
export function useInfiniteHistory(size = 30) {
  return useInfiniteQuery({
    queryKey: ['history', size],
    queryFn: ({ pageParam }) => getHistory(pageParam, size),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.number + 1),
  });
}
