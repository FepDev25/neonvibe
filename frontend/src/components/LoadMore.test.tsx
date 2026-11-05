import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoadMore from './LoadMore';

class MockIntersectionObserver {
  observe = vi.fn();
  disconnect = vi.fn();
  unobserve = vi.fn();
}

function makeQuery(overrides: Partial<Parameters<typeof LoadMore>[0]['query']> = {}) {
  return {
    hasNextPage: true,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
    ...overrides,
  };
}

describe('LoadMore', () => {
  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
  });

  it('renders a centered button instead of a full-width bar', async () => {
    const user = userEvent.setup();
    const query = makeQuery();
    render(<LoadMore query={query} />);

    const button = screen.getByRole('button', { name: 'Cargar más' });
    expect(button).not.toHaveClass('w-full');
    expect(button.parentElement).toHaveClass('justify-center');

    await user.click(button);
    expect(query.fetchNextPage).toHaveBeenCalledTimes(1);
  });

  it('renders nothing when there are no more pages', () => {
    const { container } = render(<LoadMore query={makeQuery({ hasNextPage: false })} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows loading placeholders while the next page is fetched', () => {
    const { container } = render(
      <LoadMore query={makeQuery({ isFetchingNextPage: true })} />,
    );
    expect(container.querySelectorAll('.animate-pulse')).toHaveLength(3);
  });
});
