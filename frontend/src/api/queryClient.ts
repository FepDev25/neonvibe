import { QueryClient } from '@tanstack/react-query';

/**
 * App-wide QueryClient. Exported so non-React modules (e.g. the favorites
 * store) can invalidate queries after mutations.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      staleTime: 60_000,
      retry: 1,
    },
    mutations: {
      onError: (error) => {
        console.warn('[mutation] request failed', error);
      },
    },
  },
});
