import { useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useArtist, useArtistAlbums, useArtistTracks } from '@/hooks/useLibrary';
import { tracksToPlayerQueue } from '@/stores/playerStore';
import { artistCoverUrl } from '@/api/cover';
import AlbumCard from '@/components/AlbumCard';
import AlbumCover from '@/components/AlbumCover';
import TrackRow from '@/components/TrackRow';
import RadioButton from '@/components/RadioButton';
import Skeleton from '@/components/Skeleton';

/**
 * Artist detail: avatar header, discography (albums) and top tracks.
 */
export default function ArtistDetailPage() {
  const { id } = useParams<{ id: string }>();
  const artistId = Number(id);
  const artistQuery = useArtist(artistId);
  const albumsQuery = useArtistAlbums(artistId);
  const tracksQuery = useArtistTracks(artistId);
  const tracks = tracksQuery.data ?? [];
  const queue = useMemo(() => tracksToPlayerQueue(tracks), [tracks]);

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
        <Link to="/library" className="text-sm text-neon-cyan">
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
        className="inline-flex w-fit items-center gap-1.5 text-sm text-text-muted hover:text-neon-cyan"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Biblioteca
      </Link>

      <div className="flex flex-col items-center gap-3 py-2 text-center">
        <AlbumCover
          seed={artist.name}
          alt={`Avatar de ${artist.name}`}
          src={artistCoverUrl(artist.id)}
          className="h-28 w-28 rounded-full"
        >
          <span className="text-4xl font-bold text-white/90">
            {artist.name.trim().charAt(0).toUpperCase() || '?'}
          </span>
        </AlbumCover>
        <div>
          <h1 className="neon-text text-2xl font-bold sm:text-3xl">{artist.name}</h1>
          {albums.length > 0 && (
            <p className="text-sm text-text-muted">
              {albums.length === 1 ? '1 álbum' : `${albums.length} álbumes`}
            </p>
          )}
          {tracks.length > 0 && (
            <div className="mt-2">
              <RadioButton trackId={tracks[0].id} />
            </div>
          )}
        </div>
      </div>

      {albums.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-bold text-text">Álbumes</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
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
            {tracks.map((track) => (
              <TrackRow key={track.id} track={track} queue={queue} />
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
