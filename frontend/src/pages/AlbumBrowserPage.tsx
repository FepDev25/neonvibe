import { useInfiniteAlbums } from '@/hooks/useLibrary';
import AlbumCard from '@/components/AlbumCard';
import LoadMore from '@/components/LoadMore';
import Skeleton from '@/components/Skeleton';

/**
 * Dedicated albums browser (`/albums`), same data as the Library tab but as a
 * standalone route (per AGENTS.md route list).
 */
export default function AlbumBrowserPage() {
  const query = useInfiniteAlbums({ size: 20 });
  const albums = query.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <h1 className="neon-text text-2xl font-bold">Álbumes</h1>

      {query.isPending ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="flex flex-col gap-2 p-2">
              <Skeleton className="aspect-square w-full rounded-xl" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      ) : albums.length === 0 ? (
        <p className="py-8 text-center text-sm text-text-muted">No hay álbumes todavía.</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {albums.map((album) => (
            <AlbumCard key={album.id} album={album} />
          ))}
        </div>
      )}

      <LoadMore query={query} />
    </div>
  );
}
