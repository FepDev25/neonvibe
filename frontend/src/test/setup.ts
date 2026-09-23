import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Globals are disabled in the Vitest config, so Testing Library's automatic
// cleanup does not run on its own. Unmount React trees after each test.
afterEach(() => {
  cleanup();
});
