interface HighlightTextProps {
  text?: string | null;
  /** Term to highlight (case-insensitive). */
  term?: string;
  className?: string;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Highlights every case-insensitive occurrence of `term` inside `text` using a
 * themed `<mark>`. Used by search results without changing the surrounding
 * typography/truncation.
 */
export default function HighlightText({ text, term, className }: HighlightTextProps) {
  if (!text) {
    return null;
  }
  const needle = term?.trim();
  if (!needle) {
    return <span className={className}>{text}</span>;
  }

  const parts = text.split(new RegExp(`(${escapeRegExp(needle)})`, 'gi'));
  return (
    <span className={className}>
      {parts.map((part, index) =>
        part.toLowerCase() === needle.toLowerCase() ? (
          <mark
            key={index}
            className="rounded bg-accent-soft px-0.5 text-accent-text"
          >
            {part}
          </mark>
        ) : (
          <span key={index}>{part}</span>
        ),
      )}
    </span>
  );
}
