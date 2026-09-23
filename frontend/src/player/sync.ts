import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '@/stores/authStore';
import type { PlayerSyncMessage, QueueUpdateMessage } from '@/types';

export type SyncInbound =
  | { type: 'PLAYER_SYNC'; payload: PlayerSyncMessage }
  | { type: 'QUEUE_UPDATED'; payload: QueueUpdateMessage };

export type SyncHandler = (message: SyncInbound) => void;

/**
 * STOMP-over-SockJS client for the player sync channel (/topic/sync/{userId}).
 *
 * The module owns the connection (singleton) and has no dependency on the
 * player store: inbound messages are forwarded to a handler registered by the
 * app boot, and outbound actions are plain publishes.
 *
 * Echo suppression: every action carries an `originator` client id (stable per
 * tab via sessionStorage). The server echoes it back in the broadcasts, so each
 * client can ignore its own echoes without time-based windows — real actions
 * from other tabs are never swallowed.
 *
 * Token handling: the SockJS factory reads the JWT fresh on every (re)connect,
 * so a re-login with a new token reconnects correctly. Full refresh-token
 * rotation is pending (prod OAuth flow).
 */
let client: Client | null = null;
let messageHandler: SyncHandler | null = null;
let started = false;
let subscribedUser: string | null = null;

function clientId(): string {
  const key = 'neonvibe-client-id';
  let id = sessionStorage.getItem(key);
  if (!id) {
    id = `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    try {
      sessionStorage.setItem(key, id);
    } catch {
      /* storage unavailable */
    }
  }
  return id;
}

function wsUrl(): string {
  const token = useAuthStore.getState().token;
  return `/ws${token ? `?token=${encodeURIComponent(token)}` : ''}`;
}

function isPlayerSync(b: unknown): b is PlayerSyncMessage {
  const r = b as Record<string, unknown>;
  return (
    typeof r === 'object' && r != null &&
    'track_id' in r && 'is_playing' in r && 'position_seconds' in r
  );
}

function isQueueUpdate(b: unknown): b is QueueUpdateMessage {
  const r = b as Record<string, unknown>;
  return typeof r === 'object' && r != null && 'tracks_order' in r && 'user_id' in r;
}

export function setSyncHandler(handler: SyncHandler) {
  messageHandler = handler;
}

export function sendPlayerAction(
  action: 'PLAY' | 'PAUSE' | 'SEEK' | 'NEXT' | 'PREV',
  positionSeconds?: number,
) {
  if (!client?.connected) {
    return;
  }
  client.publish({
    destination: `/app/player/${action.toLowerCase()}`,
    body: JSON.stringify({ action, position_seconds: positionSeconds ?? 0, originator: clientId() }),
  });
}

export function sendQueueUpdate(tracksOrder: number[], currentTrackId: number | null) {
  if (!client?.connected) {
    return;
  }
  client.publish({
    destination: '/app/queue/update',
    body: JSON.stringify({
      tracks_order: tracksOrder,
      current_track_id: currentTrackId,
      originator: clientId(),
    }),
  });
}

function handleFrame(frame: IMessage) {
  let body: unknown;
  try {
    body = JSON.parse(frame.body);
  } catch {
    return;
  }
  if (typeof body !== 'object' || body === null) {
    return;
  }
  const b = body as Record<string, unknown>;
  if (b.originator === clientId()) {
    return; // own echo
  }
  if (isPlayerSync(body)) {
    messageHandler?.({ type: 'PLAYER_SYNC', payload: body });
  } else if (isQueueUpdate(body)) {
    messageHandler?.({ type: 'QUEUE_UPDATED', payload: body });
  }
}

function deactivate() {
  started = false;
  subscribedUser = null;
  const c = client;
  client = null;
  if (c) {
    void c.deactivate();
  }
}

/**
 * Connects the sync channel. Idempotent; reads the JWT and user id from the
 * auth store at call time and re-reads the token on every reconnect.
 */
export function connectSync() {
  if (started) {
    return;
  }
  const token = useAuthStore.getState().token;
  const userId = useAuthStore.getState().user?.id;
  if (!token || !userId) {
    return;
  }
  started = true;
  subscribedUser = userId;

  client = new Client({
    webSocketFactory: () => new SockJS(wsUrl()),
    reconnectDelay: 4000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    onConnect: () => {
      const currentUser = useAuthStore.getState().user?.id;
      if (currentUser && subscribedUser === currentUser) {
        client?.subscribe(`/topic/sync/${currentUser}`, handleFrame);
      }
    },
    onWebSocketClose: () => {
      if (!useAuthStore.getState().token) {
        deactivate();
      }
    },
  });
  client.activate();
}

/** Tears down the connection (used on logout/HMR). */
export function disconnectSync() {
  deactivate();
}
