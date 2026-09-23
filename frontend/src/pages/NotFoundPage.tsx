import { Link } from 'react-router-dom';
import { Ghost } from 'lucide-react';
import Button from '@/components/Button';

/**
 * 404 page with neon styling.
 */
export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center gap-4 py-16 text-center">
      <Ghost className="h-16 w-16 text-neon-pink neon-glow" aria-hidden />
      <h1 className="neon-text text-5xl font-extrabold">404</h1>
      <p className="max-w-sm text-text-muted">
        Esa página flota en la cyberspace pero no existe en NeonVibe.
      </p>
      <Link to="/">
        <Button variant="primary">Volver al inicio</Button>
      </Link>
    </div>
  );
}
