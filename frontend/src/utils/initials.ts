/**
 * Consistent user initials for avatars (header, drawer, settings).
 *
 * Uses the first and last word: "David F." -> "DF", "Felipe" -> "FE". A single
 * letter was the source of the "D" vs "DF" inconsistency in the QA audit.
 */
export function initials(name?: string | null): string {
  if (!name) {
    return '?';
  }
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }
  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase();
}
