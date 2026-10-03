import { apiClient } from './client';

export interface TranscodeStatus {
  available: boolean;
  qualities: string[];
}

/** Whether the server can transcode, and the supported quality preset ids. */
export async function getTranscodeStatus(): Promise<TranscodeStatus> {
  const { data } = await apiClient.get<TranscodeStatus>('/transcode/status');
  return data;
}
