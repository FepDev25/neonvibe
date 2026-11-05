import { useQuery } from '@tanstack/react-query';
import { getScanStatus } from '@/api/admin';

/**
 * Whether the signed-in user can use admin endpoints.
 *
 * There is no `admin` flag in the session payload, so this probes the existing
 * scanner status endpoint: admins get 200, everyone else gets 403. The query is
 * shared by the sidebar, Settings and ScannerCard through the same cache key, so
 * it only hits the network once per session.
 */
export function useIsAdmin(): boolean {
  const status = useQuery({
    queryKey: ['scanStatus'],
    queryFn: getScanStatus,
    retry: false,
    staleTime: 5 * 60_000,
  });
  return status.isSuccess;
}
