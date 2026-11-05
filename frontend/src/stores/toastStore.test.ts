import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useToastStore } from './toastStore';

describe('toastStore', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
    vi.useFakeTimers();
  });

  it('adds and dismisses toasts', () => {
    const id = useToastStore.getState().show('Canción quitada', { duration: 0 });

    expect(useToastStore.getState().toasts).toHaveLength(1);
    expect(useToastStore.getState().toasts[0]).toMatchObject({
      id,
      message: 'Canción quitada',
    });

    useToastStore.getState().dismiss(id);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('auto-dismisses after the duration', () => {
    useToastStore.getState().show('Temporal', { duration: 1000 });
    expect(useToastStore.getState().toasts).toHaveLength(1);

    vi.advanceTimersByTime(1000);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('keeps duration 0 toasts until dismissed', () => {
    useToastStore.getState().show('Persistente', { duration: 0 });
    vi.advanceTimersByTime(60_000);
    expect(useToastStore.getState().toasts).toHaveLength(1);
  });

  it('keeps the action callback for undo', () => {
    const onAction = vi.fn();
    useToastStore.getState().show('Quitada', {
      actionLabel: 'Deshacer',
      onAction,
      duration: 0,
    });

    const toast = useToastStore.getState().toasts[0];
    toast.onAction?.();
    expect(onAction).toHaveBeenCalledTimes(1);
  });
});
