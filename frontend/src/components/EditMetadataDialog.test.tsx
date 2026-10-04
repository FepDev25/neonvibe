import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import EditMetadataDialog, { type MetadataField } from './EditMetadataDialog';

const FIELDS: MetadataField[] = [
  { key: 'title', label: 'Título', required: true },
  { key: 'year', label: 'Año', type: 'number' },
];

function renderDialog(props: Partial<React.ComponentProps<typeof EditMetadataDialog>> = {}) {
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined);
  const onClose = props.onClose ?? vi.fn();
  render(
    <EditMetadataDialog
      open
      title="Editar"
      fields={FIELDS}
      initial={{ title: 'Old Title', year: '2000' }}
      onSubmit={onSubmit}
      onClose={onClose}
      {...props}
    />,
  );
  return { onSubmit, onClose };
}

describe('EditMetadataDialog', () => {
  it('submits the edited values and closes on success', async () => {
    const { onSubmit, onClose } = renderDialog();

    fireEvent.change(screen.getByLabelText(/Título/), { target: { value: 'New Title' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ title: 'New Title', year: '2000' }),
    );
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it('disables submit when a required field is blank', () => {
    renderDialog({ initial: { title: '   ', year: '2000' } });

    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled();
  });

  it('keeps the dialog open and shows the error when submit rejects', async () => {
    const onSubmit = vi.fn().mockRejectedValue({
      isAxiosError: true,
      response: { status: 403, data: { message: 'Admin privileges required' } },
    });
    const { onClose } = renderDialog({ onSubmit });

    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('administradores');
    expect(onClose).not.toHaveBeenCalled();
  });
});
