import { apiClient } from './client';

export interface PushKey {
  public_key: string;
  configured: boolean;
}

/** VAPID public key + whether the server has Web Push configured. */
export async function getPushKey(): Promise<PushKey> {
  const { data } = await apiClient.get<PushKey>('/push/public-key');
  return data;
}

/** Registers (or refreshes) a browser subscription with the backend. */
export async function subscribePush(subscription: PushSubscriptionJSON): Promise<void> {
  await apiClient.post('/push/subscribe', subscription);
}

/** Removes a subscription by endpoint. */
export async function unsubscribePush(endpoint: string): Promise<void> {
  await apiClient.post('/push/unsubscribe', { endpoint });
}

/** Sends a one-off test notification to the user's devices. */
export async function sendTestPush(): Promise<{ sent: number }> {
  const { data } = await apiClient.post<{ sent: number }>('/push/test');
  return data;
}
