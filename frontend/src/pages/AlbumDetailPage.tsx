import { useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Disc3, ArrowLeft, Calendar, Clock } from 'lucide-react';
import { useAlbum, useAlbumTracks } from '@/hooks/useLibrary';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import { albumCoverUrl, uploadAlbumCover } from '@/api/cover';
import AlbumCover from '@/components/AlbumCover';
import TrackRow from '@/components/TrackRow';
import CoverUploadButton from '@/components/CoverUploadButton';
import DownloadButton from '@/components/DownloadButton';
import RadioButton from '@/components/RadioButton';
import Skeleton from '@/components/Skeleton';
import { formatTrackCount, formatTotalDuration } from '@/utils/format';

/**
 * Album detail: cover, metadata and the tracklist ordered by track_number.
 */
export default function AlbumDetailPage() {
  const { id } = useParams<{ id: string }>();
  const albumId = Number(id);
  const albumQuery = useAlbum(albumId);
  const tracksQuery = useAlbumTracks(albumId);
  const queryClient = useQueryClient();
  const [coverVersion, setCoverVersion] = useState<number | undefined>(undefined);
  const tracks = tracksQuery.data ?? [];
  const queue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

  const handleCoverUploaded = () => {
    void queryClient.invalidateQueries({ queryKey: ['album', albumId] });
    void queryClient.invalidateQueries({ queryKey: ['album', albumId, 'tracks'] });
    void queryClient.invalidateQueries({ queryKey: ['albums'] });
    setCoverVersion(Date.now());
  };

  if (albumQuery.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-40" />
        <div className="flex gap-4">
          <Skeleton className="h-36 w-36 rounded-2xl" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-4 w-1/3" />
          </div>
        </div>
      </div>
    );
  }

  if (albumQuery.isError || !albumQuery.data) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-text-muted">No se encontró el álbum.</p>
        <Link to="/library" className="text-sm text-neon-cyan">
          ← Volver a la biblioteca
        </Link>
      </div>
    );
  }

  const album = albumQuery.data;
  const totalSeconds = tracks.reduce((acc, t) => acc + (t.duration_seconds ?? 0), 0);

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/library"
        className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-neon-cyan"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Biblioteca
      </Link>

      <div className="flex gap-4">
        <AlbumCover
          seed={`${album.name}-${album.artist ?? ''}`}
          alt={`Carátula de ${album.name}`}
          src={albumCoverUrl(album.id, coverVersion)}
          className="h-32 w-32 shrink-0 rounded-2xl sm:h-40 sm:w-40"
        >
          <Disc3 className="h-12 w-12 text-white/80" aria-hidden />
        </AlbumCover>
        <div className="flex min-w-0 flex-col justify-end gap-1">
          <h1 className="neon-text text-2xl font-bold sm:text-3xl">{album.name}</h1>
          {album.artist && (
            <p className="text-sm text-text-muted">
              <span className="font-medium text-text">{album.artist}</span>
              {album.year ? ` · ${album.year}` : ''}
            </p>
          )}
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-muted">
            <span className="inline-flex items-center gap-1">
              <Clock className="h-3.5 w-3.5" aria-hidden />
              {formatTrackCount(album.track_count)}
            </span>
            {totalSeconds > 0 && (
              <span className="inline-flex items-center gap-1">
                <Calendar className="h-3.5 w-3.5" aria-hidden />
                {formatTotalDuration(totalSeconds)}
              </span>
            )}
            {album.genre && <span>{album.genre}</span>}
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <DownloadButton tracks={queue} />
            {tracks.length > 0 && <RadioButton trackId={tracks[0].id} />}
            <CoverUploadButton
              onUpload={(file) => uploadAlbumCover(album.id, file)}
              onUploaded={handleCoverUploaded}
            />
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-1">
        {tracksQuery.isPending ? (
          Array.from({ length: album.track_count || 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full rounded-xl" />
          ))
        ) : (
          tracks.map((track) => (
            <TrackRow key={track.id} track={track} number={track.track_number} queue={queue} />
          ))
        )}
      </div>
    </div>
  );
}
