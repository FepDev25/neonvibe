import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/push', () => ({
  getPushKey: vi.fn(),
  subscribePush: vi.fn(),
  unsubscribePush: vi.fn(),
}));

import { getPushKey, subscribePush, unsubscribePush } from '@/api/push';
import { disablePush, enablePush, pushSupported } from './notifications';

function installPushGlobals(
  registration: unknown,
  permission: NotificationPermission = 'granted',
) {
  Object.defineProperty(window, 'PushManager', {
    value: function PushManager() {},
    configurable: true,
  });
  Object.defineProperty(window, 'Notification', {
    value: { requestPermission: vi.fn().mockResolvedValue(permission) },
    configurable: true,
  });
  Object.defineProperty(navigator, 'serviceWorker', {
    value: { ready: Promise.resolve(registration) },
    configurable: true,
  });
}

describe('push notifications', () => {
  afterEach(() => {
    delete (window as { PushManager?: unknown }).PushManager;
    delete (navigator as { serviceWorker?: unknown }).serviceWorker;
    vi.clearAllMocks();
  });

  it('is unsupported without the browser APIs (jsdom default)', () => {
    expect(pushSupported()).toBe(false);
  });

  it('subscribes and registers with the backend when enabled', async () => {
    const subscription = {
      endpoint: 'https://ep',
      toJSON: () => ({ endpoint: 'https://ep', keys: { p256dh: 'k', auth: 'a' } }),
    };
    const registration = {
      pushManager: {
        getSubscription: vi.fn().mockResolvedValue(null),
        subscribe: vi.fn().mockResolvedValue(subscription),
      },
    };
    installPushGlobals(registration);
    vi.mocked(getPushKey).mockResolvedValue({ public_key: 'BPublicKey', configured: true });
    vi.mocked(subscribePush).mockResolvedValue(undefined);

    await expect(enablePush()).resolves.toBe(true);
    expect(registration.pushManager.subscribe).toHaveBeenCalled();
    expect(subscribePush).toHaveBeenCalledWith({
      endpoint: 'https://ep',
      keys: { p256dh: 'k', auth: 'a' },
    });
  });

  it('does not subscribe when the server is not configured', async () => {
    installPushGlobals({});
    vi.mocked(getPushKey).mockResolvedValue({ public_key: '', configured: false });

    await expect(enablePush()).resolves.toBe(false);
    expect(subscribePush).not.toHaveBeenCalled();
  });

  it('unsubscribes locally and on the backend', async () => {
    const subscription = {
      endpoint: 'https://ep',
      unsubscribe: vi.fn().mockResolvedValue(true),
    };
    installPushGlobals({ pushManager: { getSubscription: vi.fn().mockResolvedValue(subscription) } });
    vi.mocked(unsubscribePush).mockResolvedValue(undefined);

    await disablePush();

    expect(unsubscribePush).toHaveBeenCalledWith('https://ep');
    expect(subscription.unsubscribe).toHaveBeenCalled();
  });
});
