import { useState } from 'react';
import { Plus } from 'lucide-react';
import { useCreatePlaylist, usePlaylists } from '@/hooks/usePlaylists';
import PlaylistCard from '@/components/PlaylistCard';
import PlaylistForm from '@/components/PlaylistForm';
import Button from '@/components/Button';
import Skeleton from '@/components/Skeleton';

/**
 * Playlists listing (own + public) with a "new playlist" flow.
 */
export default function PlaylistsPage() {
  const { data: playlists, isPending } = usePlaylists();
  const createPlaylist = useCreatePlaylist();
  const [formOpen, setFormOpen] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="neon-text text-2xl font-bold">Playlists</h1>
        <Button size="sm" onClick={() => setFormOpen(true)}>
          <Plus className="h-4 w-4" aria-hidden />
          Nueva
        </Button>
      </div>

      {isPending ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex flex-col gap-2 p-2">
              <Skeleton className="aspect-square w-full rounded-xl" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      ) : !playlists || playlists.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <p className="text-text-muted">Todavía no tienes playlists.</p>
          <Button onClick={() => setFormOpen(true)}>Crear tu primera playlist</Button>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {playlists.map((playlist) => (
            <PlaylistCard key={playlist.id} playlist={playlist} />
          ))}
        </div>
      )}

      <PlaylistForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title="Nueva playlist"
        submitLabel="Crear"
        onSubmit={(values) => {
          void createPlaylist.mutateAsync(values).then(() => setFormOpen(false));
        }}
      />
    </div>
  );
}
