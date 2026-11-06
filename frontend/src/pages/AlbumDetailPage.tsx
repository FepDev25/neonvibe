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
import AlbumEditButton from '@/components/AlbumEditButton';
import DownloadButton from '@/components/DownloadButton';
import RadioButton from '@/components/RadioButton';
import PageHero from '@/components/PageHero';
import CollectionActions from '@/components/CollectionActions';
import HeroMenu from '@/components/HeroMenu';
import Skeleton from '@/components/Skeleton';
import EmptyState from '@/components/EmptyState';
import Button from '@/components/Button';
import TrackSortSelect from '@/components/TrackSortSelect';
import { sortTracks, type TrackSortKey } from '@/utils/trackSort';
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
  const [sort, setSort] = useState<TrackSortKey>('default');
  const tracks = tracksQuery.data ?? [];
  const sortedTracks = useMemo(() => sortTracks(tracks, sort), [tracks, sort]);
  const queue = useMemo(() => tracksToPlayerQueue(sortedTracks), [sortedTracks]);

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
        <Link to="/library" className="text-sm text-accent-text">
          ← Volver a la biblioteca
        </Link>
      </div>
    );
  }

  const album = albumQuery.data;
  // The reserved Singles album has no artist: every row shows its own artist.
  const isSingles = album.name.toLowerCase() === 'singles' && !album.artist;
  const totalSeconds = tracks.reduce((acc, t) => acc + (t.duration_seconds ?? 0), 0);

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/library"
        className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-accent-text"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Biblioteca
      </Link>

      <PageHero
        kind={isSingles ? 'Singles' : 'Álbum'}
        title={album.name}
        cover={
          <AlbumCover
            seed={`${album.name}-${album.artist ?? ''}`}
            alt={`Carátula de ${album.name}`}
            src={albumCoverUrl(album.id, coverVersion)}
            className="h-40 w-40 rounded-2xl sm:h-48 sm:w-48"
          >
            <Disc3 className="h-12 w-12 text-white/80" aria-hidden />
          </AlbumCover>
        }
        meta={
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {album.artist && <span className="font-medium text-text">{album.artist}</span>}
            {album.year && <span>{album.year}</span>}
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
        }
        actions={<CollectionActions queue={queue} />}
        menu={
          <HeroMenu label="Acciones del álbum">
            <DownloadButton tracks={queue} />
            {sortedTracks.length > 0 && <RadioButton trackId={sortedTracks[0].id} />}
            <CoverUploadButton
              onUpload={(file) => uploadAlbumCover(album.id, file)}
              onUploaded={handleCoverUploaded}
            />
            <AlbumEditButton album={album} />
          </HeroMenu>
        }
      />

      {tracks.length > 1 && (
        <div className="flex justify-end">
          <TrackSortSelect
            id="album-track-sort"
            value={sort}
            onChange={setSort}
            includeAlbum={false}
            defaultLabel="Orden del álbum"
          />
        </div>
      )}

      <div className="flex flex-col gap-1">
        {tracksQuery.isPending ? (
          Array.from({ length: album.track_count || 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full rounded-xl" />
          ))
        ) : tracks.length === 0 ? (
          <EmptyState
            icon={<Disc3 className="h-10 w-10" aria-hidden />}
            title="Este álbum no tiene pistas disponibles"
            description="Puede que los archivos se hayan movido o estén fuera de la biblioteca."
            action={
              <Link to="/library">
                <Button variant="secondary">Volver a la biblioteca</Button>
              </Link>
            }
          />
        ) : (
          sortedTracks.map((track, index) => (
            <TrackRow
              key={track.id}
              track={track}
              number={track.track_number ?? index + 1}
              queue={queue}
              showArtist={isSingles}
              showAlbum={false}
            />
          ))
        )}
      </div>
    </div>
  );
}
