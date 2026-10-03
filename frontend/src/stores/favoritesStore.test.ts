import { beforeEach, describe, expect, it } from 'vitest';
import { useFavoritesStore } from './favoritesStore';

describe('favoritesStore', () => {
  beforeEach(() => {
    useFavoritesStore.setState({ byKey: {}, loaded: false, pending: {} });
  });

  it('reset clears the loaded set so another user reloads their own', () => {
    useFavoritesStore.setState({
      byKey: { 'TRACK:1': 5 },
      loaded: true,
      pending: { 'TRACK:1': true },
    });

    useFavoritesStore.getState().reset();

    const state = useFavoritesStore.getState();
    expect(state.byKey).toEqual({});
    expect(state.loaded).toBe(false);
    expect(state.pending).toEqual({});
  });
});
