/**
 * Merges class names, filtering out falsy values.
 * A minimal stand-in for `clsx`/`tailwind-merge` to avoid extra deps.
 */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}
