import { useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useArtist, useArtistAlbums, useArtistTracks } from '@/hooks/useLibrary';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import { artistCoverUrl, uploadArtistCover } from '@/api/cover';
import AlbumCard from '@/components/AlbumCard';
import AlbumCover from '@/components/AlbumCover';
import TrackRow from '@/components/TrackRow';
import CoverUploadButton from '@/components/CoverUploadButton';
import ArtistEditButton from '@/components/ArtistEditButton';
import RadioButton from '@/components/RadioButton';
import PageHero from '@/components/PageHero';
import CollectionActions from '@/components/CollectionActions';
import HeroMenu from '@/components/HeroMenu';
import Skeleton from '@/components/Skeleton';
import { formatTrackCount } from '@/utils/format';
import { initials } from '@/utils/initials';

/**
 * Artist detail: avatar header, discography (albums) and top tracks.
 */
export default function ArtistDetailPage() {
  const { id } = useParams<{ id: string }>();
  const artistId = Number(id);
  const artistQuery = useArtist(artistId);
  const albumsQuery = useArtistAlbums(artistId);
  const tracksQuery = useArtistTracks(artistId);
  const queryClient = useQueryClient();
  const [coverVersion, setCoverVersion] = useState<number | undefined>(undefined);
  const tracks = tracksQuery.data ?? [];
  const queue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

  const handleCoverUploaded = () => {
    void queryClient.invalidateQueries({ queryKey: ['artist', artistId] });
    void queryClient.invalidateQueries({ queryKey: ['artist', artistId, 'albums'] });
    void queryClient.invalidateQueries({ queryKey: ['artist', artistId, 'tracks'] });
    void queryClient.invalidateQueries({ queryKey: ['artists'] });
    setCoverVersion(Date.now());
  };

  if (artistQuery.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-40" />
        <div className="flex flex-col items-center gap-3 py-4">
          <Skeleton className="h-24 w-24 rounded-full" />
          <Skeleton className="h-6 w-48" />
        </div>
      </div>
    );
  }

  if (artistQuery.isError || !artistQuery.data) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-text-muted">No se encontró el artista.</p>
        <Link to="/library" className="text-sm text-accent-text">
          ← Volver a la biblioteca
        </Link>
      </div>
    );
  }

  const artist = artistQuery.data;
  const albums = albumsQuery.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/library"
        className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-accent-text"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Biblioteca
      </Link>

      <PageHero
        kind="Artista"
        title={artist.name}
        cover={
          <AlbumCover
            seed={artist.name}
            alt={`Avatar de ${artist.name}`}
            src={artistCoverUrl(artist.id, coverVersion)}
            className="h-40 w-40 rounded-full sm:h-48 sm:w-48"
          >
            <span className="text-5xl font-bold text-white/90">{initials(artist.name)}</span>
          </AlbumCover>
        }
        meta={
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {albums.length > 0 && (
              <span>{albums.length === 1 ? '1 álbum' : `${albums.length} álbumes`}</span>
            )}
            {tracks.length > 0 && <span>{formatTrackCount(tracks.length)}</span>}
          </div>
        }
        actions={<CollectionActions queue={queue} />}
        menu={
          <HeroMenu label="Acciones del artista">
            {tracks.length > 0 && <RadioButton trackId={tracks[0].id} />}
            <CoverUploadButton
              onUpload={(file) => uploadArtistCover(artist.id, file)}
              onUploaded={handleCoverUploaded}
            />
            <ArtistEditButton artist={artist} />
          </HeroMenu>
        }
      />

      {albums.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-bold text-text">Álbumes</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
            {albums.map((album) => (
              <AlbumCard key={album.id} album={album} />
            ))}
          </div>
        </section>
      )}

      {tracks.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-bold text-text">Canciones</h2>
          <div className="flex flex-col gap-1">
            {tracks.map((track, index) => (
              <TrackRow
                key={track.id}
                track={track}
                number={index + 1}
                queue={queue}
                showArtist={false}
              />
            ))}
          </div>
        </section>
      )}

      {albums.length === 0 && tracks.length === 0 && (
        <p className="py-8 text-center text-sm text-text-muted">
          Sin canciones de este artista todavía.
        </p>
      )}
    </div>
  );
}
