import { Search, X } from 'lucide-react';
import { cn } from '@/utils/cn';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  /** Accessible label (visually hidden). Also used as the default placeholder. */
  label?: string;
  placeholder?: string;
  id?: string;
  className?: string;
}

/**
 * Reusable search box for list surfaces. Controlled, with a leading icon and a
 * clear button. The native `<input type="search">` cancel affordance is hidden
 * so the styling stays consistent across browsers.
 */
export default function SearchInput({
  value,
  onChange,
  label = 'Buscar',
  placeholder,
  id = 'search-input',
  className,
}: SearchInputProps) {
  return (
    <div className={cn('relative', className)}>
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <Search
        className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-muted"
        aria-hidden
      />
      <input
        id={id}
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder ?? label}
        className={cn(
          'h-11 w-full rounded-xl border border-border bg-surface pl-10 pr-10 text-text',
          'placeholder:text-text-muted focus:border-neon-cyan focus:outline-none focus:ring-1 focus:ring-neon-cyan',
          '[&::-webkit-search-cancel-button]:appearance-none',
        )}
      />
      {value.length > 0 && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label="Limpiar búsqueda"
          className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full text-text-muted transition-colors hover:text-text"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      )}
    </div>
  );
}
