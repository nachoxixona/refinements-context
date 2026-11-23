import { ConfirmDialog, Modal } from '../ui/Modal';

/** The FR-013 conflicts: replace a microservice's spec, or explain that a spec is linked elsewhere. */
export function LinkConflictDialog({
  conflict,
  microserviceName,
  message,
  onReplace,
  onClose,
}: {
  conflict: string;
  microserviceName: string;
  message: string;
  onReplace: () => void;
  onClose: () => void;
}) {
  if (conflict === 'MICROSERVICE_HAS_CARD') {
    return (
      <ConfirmDialog
        title="Replace the current spec?"
        message={
          <p>
            <strong>{microserviceName}</strong> already has a spec. Replace it? The current spec stays on the canvas,
            unlinked.
          </p>
        }
        confirmLabel="Replace"
        onConfirm={onReplace}
        onCancel={onClose}
      />
    );
  }
  return (
    <Modal
      title="Spec already linked"
      onClose={onClose}
      footer={
        <button type="button" className="primary" onClick={onClose}>
          OK
        </button>
      }
    >
      <p>{message}</p>
    </Modal>
  );
}
