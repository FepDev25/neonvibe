/**
 * Shared domain types mirroring the backend DTOs (see backend/src/main/java/
 * com/neonvibe/dto). Field names are snake_case to match Jackson's
 * `SNAKE_CASE` naming strategy (verified against the real API).
 */

export interface User {
  id: string;
  email: string;
  name: string;
  avatar_url?: string;
}

export interface Track {
  id: number;
  file_path?: string;
  title: string;
  artist?: string;
  album?: string;
  album_artist?: string;
  year?: number;
  genre?: string;
  track_number?: number;
  disc_number?: number;
  duration_seconds?: number;
  bitrate?: number;
  format?: string;
  mime_type?: string;
  has_lyrics?: boolean;
  cover_art_path?: string;
  is_available?: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface Album {
  id: number;
  name: string;
  artist?: string;
  year?: number;
  genre?: string;
  cover_art_path?: string;
  created_at?: string;
  track_count: number;
}

export interface Artist {
  id: number;
  name: string;
  created_at?: string;
}

export interface AuthResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
}

export type FavoriteEntityType = 'TRACK' | 'ALBUM' | 'ARTIST';

export interface Favorite {
  id: number;
  entity_type: FavoriteEntityType;
  entity_id: number;
  created_at?: string;
}

export interface PlaylistTrackRef {
  id: number;
  track_id: number;
  position: number;
}

export interface Playlist {
  id: number;
  name: string;
  description?: string;
  is_public: boolean;
  cover_art_path?: string;
  owner_id: string;
  created_at?: string;
  updated_at?: string;
  tracks: PlaylistTrackRef[];
}

export interface PlaylistDetail {
  id: number;
  name: string;
  description?: string;
  is_public: boolean;
  cover_art_path?: string;
  owner_id: string;
  created_at?: string;
  updated_at?: string;
  tracks: Track[];
}

export type RepeatMode = 'NONE' | 'ALL' | 'ONE';

export interface PlayQueue {
  id?: number;
  current_track_id: number | null;
  position_seconds: number;
  shuffle_enabled: boolean;
  repeat_mode: RepeatMode;
  tracks_order: number[];
  updated_at?: string;
}

export interface PlayHistory {
  id: number;
  track_id: number;
  played_at: string;
  completed: boolean;
  duration_listened_seconds?: number;
}

/** Player state broadcast by the backend over WebSocket (PLAYER_SYNC). */
export interface PlayerSyncMessage {
  track_id: number | null;
  position_seconds: number;
  is_playing: boolean;
  timestamp: number;
  queue_id: number | null;
}

/** Queue state broadcast by the backend over WebSocket (QUEUE_UPDATED). */
export interface QueueUpdateMessage {
  user_id: string;
  tracks_order: number[];
  current_track_id: number | null;
}

/**
 * Spring Data `Page<T>` as serialized by the backend (snake_case).
 */
export interface Page<T> {
  content: T[];
  pageable: {
    page_number: number;
    page_size: number;
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  last: boolean;
  total_pages: number;
  total_elements: number;
  size: number;
  number: number;
  first: boolean;
  number_of_elements: number;
  empty: boolean;
}
