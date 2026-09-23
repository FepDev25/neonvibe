import { apiClient } from './client';

export interface LyricsResponse {
  track_id: number;
  synced: boolean;
  lyrics: string | null;
  source: string | null;
}

export async function getLyrics(trackId: number): Promise<LyricsResponse> {
  const { data } = await apiClient.get<LyricsResponse>(`/tracks/${trackId}/lyrics`);
  return data;
}
