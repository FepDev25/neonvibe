import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Loader2, Radar } from 'lucide-react';
import { getScanStatus, triggerScan } from '@/api/admin';
import { setScannerHandler } from '@/player/sync';
import Button from './Button';
import Card from './Card';

/**
 * Admin card to trigger a library scan and watch its progress. Hidden when the
 * endpoint is not accessible (non-admin gets 403), so it never shows for regular
 * users. Live updates arrive over the WebSocket scanner topic.
 */
export default function ScannerCard() {
  const queryClient = useQueryClient();
  const status = useQuery({
    queryKey: ['scanStatus'],
    queryFn: getScanStatus,
    // Poll only while a scan is running; WS events also invalidate this query.
    refetchInterval: (query) => (query.state.data?.running ? 2000 : false),
    retry: false,
  });
  const scan = useMutation({
    mutationFn: triggerScan,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scanStatus'] }),
  });

  useEffect(() => {
    setScannerHandler(() => {
      void queryClient.invalidateQueries({ queryKey: ['scanStatus'] });
    });
    return () => setScannerHandler(null);
  }, [queryClient]);

  if (status.isError) {
    return null;
  }

  const data = status.data;
  const running = data?.running ?? false;

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <Radar className="h-6 w-6 text-neon-cyan" aria-hidden />
        <div>
          <p className="font-semibold">Escáner</p>
          <p className="text-sm text-text-muted">
            {running
              ? 'Escaneando la biblioteca…'
              : 'Revisa toda la biblioteca. La carpeta de entrada se organiza sola.'}
          </p>
        </div>
      </div>

      {data && (
        <dl className="grid grid-cols-3 gap-2 text-center text-xs">
          <div className="rounded-xl bg-surface-alt py-2">
            <dt className="text-text-muted">Revisados</dt>
            <dd className="text-sm font-semibold text-text">{data.total_scanned}</dd>
          </div>
          <div className="rounded-xl bg-surface-alt py-2">
            <dt className="text-text-muted">Añadidos</dt>
            <dd className="text-sm font-semibold text-neon-cyan">{data.processed}</dd>
          </div>
          <div className="rounded-xl bg-surface-alt py-2">
            <dt className="text-text-muted">Fallos</dt>
            <dd className="text-sm font-semibold text-text">{data.failed}</dd>
          </div>
        </dl>
      )}

      <Button
        variant="secondary"
        size="sm"
        className="self-start"
        disabled={running || scan.isPending}
        onClick={() => scan.mutate()}
      >
        {running || scan.isPending ? (
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
        ) : (
          <Radar className="h-4 w-4" aria-hidden />
        )}
        {running ? 'Escaneando…' : 'Escanear ahora'}
      </Button>
    </Card>
  );
}
