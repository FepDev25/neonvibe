import { apiClient } from './client';

export type StatsRangeParam = '7d' | '30d' | '90d' | 'all';
export type StatsTopTypeParam = 'tracks' | 'albums' | 'artists' | 'genres';

export interface StatsOverview {
  total_plays: number;
  listened_seconds: number;
  completed_plays: number;
  distinct_tracks: number;
  distinct_artists: number;
  distinct_albums: number;
}

export interface StatsTopItem {
  id: number | null;
  name: string;
  subtitle: string;
  plays: number;
  listened_seconds: number;
}

export interface StatsPoint {
  period: string;
  plays: number;
  listened_seconds: number;
}

export interface StatsTimeline {
  bucket: string;
  points: StatsPoint[];
}

export interface StatsHour {
  hour: number;
  plays: number;
}

export async function getStatsOverview(range: StatsRangeParam): Promise<StatsOverview> {
  const { data } = await apiClient.get<StatsOverview>('/stats/overview', { params: { range } });
  return data;
}

export async function getStatsTop(
  type: StatsTopTypeParam,
  range: StatsRangeParam,
  limit = 10,
): Promise<StatsTopItem[]> {
  const { data } = await apiClient.get<StatsTopItem[]>('/stats/top', {
    params: { type, range, limit },
  });
  return data;
}

export async function getStatsTimeline(
  range: StatsRangeParam,
  tz: string,
  bucket: 'day' | 'week' | 'month' = 'day',
): Promise<StatsTimeline> {
  const { data } = await apiClient.get<StatsTimeline>('/stats/timeline', {
    params: { range, bucket, tz },
  });
  return data;
}

export async function getStatsHours(range: StatsRangeParam, tz: string): Promise<StatsHour[]> {
  const { data } = await apiClient.get<StatsHour[]>('/stats/hours', { params: { range, tz } });
  return data;
}
