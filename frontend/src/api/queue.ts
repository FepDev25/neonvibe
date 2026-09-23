import { apiClient } from './client';
import type { PlayHistory, PlayQueue, RepeatMode } from '@/types';

export interface QueuePayload {
  current_track_id?: number | null;
  position_seconds?: number;
  shuffle_enabled?: boolean;
  repeat_mode?: RepeatMode;
  tracks_order: number[];
}

/**
 * Per-user play queue and history endpoints.
 */

export async function getQueue(): Promise<PlayQueue | null> {
  const { data } = await apiClient.get<PlayQueue>('/queue');
  return data;
}

export async function updateQueue(payload: QueuePayload): Promise<PlayQueue> {
  const { data } = await apiClient.put<PlayQueue>('/queue', payload);
  return data;
}

export interface HistoryPayload {
  track_id: number;
  completed?: boolean;
  duration_listened_seconds?: number;
}

export async function recordHistory(payload: HistoryPayload): Promise<PlayHistory> {
  const { data } = await apiClient.post<PlayHistory>('/history', payload);
  return data;
}
