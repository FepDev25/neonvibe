import { apiClient } from './client';
import type { ScanStatus } from '@/types';

/** Scanner status snapshot (admin-only; 403 for non-admins). */
export async function getScanStatus(): Promise<ScanStatus> {
  const { data } = await apiClient.get<ScanStatus>('/admin/scan/status');
  return data;
}

/** Triggers a manual library scan (admin-only; returns 202). */
export async function triggerScan(): Promise<void> {
  await apiClient.post('/admin/scan');
}
