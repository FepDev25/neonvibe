import { useQuery } from '@tanstack/react-query';
import {
  getStatsHours,
  getStatsOverview,
  getStatsTimeline,
  getStatsTop,
  type StatsRangeParam,
  type StatsTopTypeParam,
} from '@/api/stats';

/** Thin TanStack Query wrappers for the personal stats endpoints. */
export function useStatsOverview(range: StatsRangeParam) {
  return useQuery({
    queryKey: ['stats', 'overview', range],
    queryFn: () => getStatsOverview(range),
  });
}

export function useStatsTop(type: StatsTopTypeParam, range: StatsRangeParam, limit = 10) {
  return useQuery({
    queryKey: ['stats', 'top', type, range, limit],
    queryFn: () => getStatsTop(type, range, limit),
  });
}

export function useStatsTimeline(range: StatsRangeParam, tz: string, bucket: 'day' | 'week' | 'month' = 'day') {
  return useQuery({
    queryKey: ['stats', 'timeline', range, bucket, tz],
    queryFn: () => getStatsTimeline(range, tz, bucket),
  });
}

export function useStatsHours(range: StatsRangeParam, tz: string) {
  return useQuery({
    queryKey: ['stats', 'hours', range, tz],
    queryFn: () => getStatsHours(range, tz),
  });
}
