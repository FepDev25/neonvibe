import { apiClient } from './client';
import type { HistoryEntry, Page } from '@/types';

/** Paged playback history, newest first (enriched with track metadata). */
export async function getHistory(page: number, size = 30): Promise<Page<HistoryEntry>> {
  const { data } = await apiClient.get<Page<HistoryEntry>>('/history', {
    params: { page, size },
  });
  return data;
}
