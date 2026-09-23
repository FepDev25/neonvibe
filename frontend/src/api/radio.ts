import { apiClient } from './client';
import type { Track } from '@/types';

export async function getRadioSeed(trackId: number, size = 20): Promise<Track[]> {
  const { data } = await apiClient.get<Track[]>('/radio/seed', {
    params: { track_id: trackId, size },
  });
  return data;
}
