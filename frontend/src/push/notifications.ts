import { getPushKey, subscribePush, unsubscribePush } from '@/api/push';

/**
 * Native notifications via Web Push. The service worker is registered by
 * vite-plugin-pwa; here we only manage the subscription lifecycle and sync it
 * with the backend.
 */

/** Whether this browser can receive Web Push notifications. */
export function pushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/** Decodes a base64url VAPID key into the bytes `subscribe()` expects. */
function urlBase64ToUint8Array(base64: string) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(normalized);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}

async function registration(): Promise<ServiceWorkerRegistration> {
  return navigator.serviceWorker.ready;
}

/**
 * Requests permission and subscribes this device. Returns false when the
 * browser can't, the server isn't configured, or the user denies permission.
 */
export async function enablePush(): Promise<boolean> {
  if (!pushSupported()) {
    return false;
  }
  const { public_key: publicKey, configured } = await getPushKey();
  if (!configured || !publicKey) {
    return false;
  }
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    return false;
  }
  const reg = await registration();
  const existing = await reg.pushManager.getSubscription();
  const subscription =
    existing ??
    (await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    }));
  await subscribePush(subscription.toJSON());
  return true;
}

/** Unsubscribes this device and drops it from the backend (best effort). */
export async function disablePush(): Promise<void> {
  if (!pushSupported()) {
    return;
  }
  const reg = await registration();
  const subscription = await reg.pushManager.getSubscription();
  if (!subscription) {
    return;
  }
  await unsubscribePush(subscription.endpoint).catch(() => undefined);
  await subscription.unsubscribe().catch(() => undefined);
}

/** Whether this device currently has an active push subscription. */
export async function isPushEnabled(): Promise<boolean> {
  if (!pushSupported()) {
    return false;
  }
  const reg = await registration();
  return (await reg.pushManager.getSubscription()) != null;
}
