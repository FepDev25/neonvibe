import { apiClient } from './client';

/** Admin-only YouTube importer (backend: /api/v1/admin/upload/youtube). */

export type YouTubeJobState =
  | 'QUEUED'
  | 'DOWNLOADING'
  | 'TAGGING'
  | 'INGESTING'
  | 'SUCCEEDED'
  | 'FAILED';

export interface YouTubeStatus {
  enabled: boolean;
  available: boolean;
  version?: string | null;
  js_runtime?: string | null;
  js_runtime_available: boolean;
  message?: string | null;
}

export interface YouTubeImportJob {
  job_id: string;
  state: YouTubeJobState;
  stage: string;
  url: string;
  title?: string | null;
  artist?: string | null;
  album_id?: number | null;
  track_id?: number | null;
  error?: string | null;
  started_at?: string | null;
  finished_at?: string | null;
}

export async function getYouTubeStatus(): Promise<YouTubeStatus> {
  const { data } = await apiClient.get<YouTubeStatus>('/admin/upload/youtube/status');
  return data;
}

export async function startYouTubeImport(url: string): Promise<YouTubeImportJob> {
  const { data } = await apiClient.post<YouTubeImportJob>('/admin/upload/youtube', { url });
  return data;
}

export async function getYouTubeImportJob(jobId: string): Promise<YouTubeImportJob> {
  const { data } = await apiClient.get<YouTubeImportJob>(
    `/admin/upload/youtube/jobs/${encodeURIComponent(jobId)}`,
  );
  return data;
}
