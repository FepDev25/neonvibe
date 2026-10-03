/**
 * Maps `items` through `fn` with at most `limit` promises in flight, preserving
 * the input order. Used to avoid firing an unbounded number of requests at once
 * (e.g. resolving every track id of a very large queue).
 */
export async function mapWithConcurrency<T, R>(
  items: readonly T[],
  limit: number,
  fn: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const results = new Array<R>(items.length);
  if (items.length === 0) {
    return results;
  }
  const workers = Math.max(1, Math.min(limit, items.length));
  let next = 0;

  const run = async () => {
    while (true) {
      const index = next++;
      if (index >= items.length) {
        return;
      }
      results[index] = await fn(items[index], index);
    }
  };

  await Promise.all(Array.from({ length: workers }, run));
  return results;
}
