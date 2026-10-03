import { MemoryRouter } from 'react-router-dom';
import { render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage';
import { useAuthStore } from '@/stores/authStore';

interface GoogleStub {
  accounts: {
    id: {
      initialize: ReturnType<typeof vi.fn>;
      renderButton: ReturnType<typeof vi.fn>;
    };
  };
}

/**
 * Regression: the Google button must be rendered only after the GIS script has
 * loaded AND React has committed the button container. Calling renderButton
 * before the container existed left the login page with no sign-in button.
 */
describe('LoginPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'test-client.apps.googleusercontent.com');
    useAuthStore.setState({ isAuthenticated: false, user: null, token: null, refreshToken: null });
    delete (window as unknown as { google?: unknown }).google;
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    delete (window as unknown as { google?: unknown }).google;
  });

  it('renders the Google button after the GIS script loads', async () => {
    const created: HTMLScriptElement[] = [];
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const element = originalCreateElement(tag);
      if (tag === 'script') {
        created.push(element as HTMLScriptElement);
      }
      return element;
    });

    const renderButton = vi.fn();
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    expect(created.length).toBeGreaterThan(0);

    // The GIS library becomes available when the script finishes loading.
    (window as unknown as { google: GoogleStub }).google = {
      accounts: { id: { initialize: vi.fn(), renderButton } },
    };
    created[0].onload?.(new Event('load'));

    await waitFor(() => expect(renderButton).toHaveBeenCalled());
    // The button container must have been mounted when renderButton ran.
    expect(renderButton.mock.calls[0][0]).not.toBeNull();
  });
});
