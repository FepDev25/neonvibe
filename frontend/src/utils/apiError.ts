import axios from 'axios';

interface ApiErrorBody {
  message?: string;
  error?: string;
}

/**
 * Extracts a user-facing message from an API failure.
 *
 * `statusMessages` maps HTTP statuses to specific copy (e.g. 403 -> "admin
 * only"); otherwise the backend `message` is used, falling back to `fallback`.
 */
export function apiErrorMessage(
  error: unknown,
  fallback: string,
  statusMessages: Record<number, string> = {},
): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    if (status && statusMessages[status]) {
      return statusMessages[status];
    }
    const message = (error.response?.data as ApiErrorBody | undefined)?.message;
    if (message) {
      return message;
    }
  }
  return fallback;
}
