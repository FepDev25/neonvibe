import { ArrowUpDown } from 'lucide-react';
import { cn } from '@/utils/cn';

export interface SortOption {
  value: string;
  label: string;
}

interface SortSelectProps {
  id: string;
  value: string;
  options: SortOption[];
  onChange: (value: string) => void;
  /** Accessible name and visible label on wide screens. */
  label?: string;
  className?: string;
}

/** Compact, accessible sort control shared by every sortable list. */
export default function SortSelect({
  id,
  value,
  options,
  onChange,
  label = 'Ordenar por',
  className,
}: SortSelectProps) {
  return (
    <div className={cn('flex items-center gap-2 text-xs text-text-muted', className)}>
      <ArrowUpDown className="h-3.5 w-3.5 shrink-0" aria-hidden />
      <label htmlFor={id} className="hidden sm:inline">
        {label}
      </label>
      <select
        id={id}
        value={value}
        aria-label={label}
        onChange={(event) => onChange(event.target.value)}
        className="h-9 max-w-[13rem] rounded-xl border border-border bg-surface px-2 text-xs text-text focus:border-accent-text focus:outline-none"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}
