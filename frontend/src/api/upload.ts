import { apiClient } from './client';

export interface UploadResult {
  processed: number;
  failed: number;
  errors: string[];
}

/**
 * Uploads audio files (and/or a ZIP) for the server to organize into the
 * library. Admin-only. Uses a long timeout: a full album can take a while to
 * transfer and ingest.
 */
export async function uploadTracks(files: File[]): Promise<UploadResult> {
  const form = new FormData();
  files.forEach((file) => form.append('files', file));
  const { data } = await apiClient.post<UploadResult>('/admin/upload', form, {
    timeout: 30 * 60 * 1000,
  });
  return data;
}
