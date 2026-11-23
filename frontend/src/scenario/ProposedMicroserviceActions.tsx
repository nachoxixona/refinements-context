import { useState } from 'react';
import { normalizeLabel } from '../canvas/normalize';
import { useCanvasView } from '../canvas/useCanvasView';
import { isMicroservice } from '../canvas/viewModel';
import { FloatingMenu } from '../ui/FloatingMenu';
import { ConfirmDialog, Modal } from '../ui/Modal';
import { useScenarioStore } from './scenarioStore';

/** The menu of a proposed microservice in a draft: edit its name and description, or remove the proposal. */
export function ProposedMicroserviceActions({
  tempKey,
  name,
  description,
  team,
  anchor,
  onClose,
}: {
  tempKey: string;
  name: string;
  description: string | null;
  team?: string | null;
  anchor: HTMLElement | null;
  onClose: () => void;
}) {
  const remove = useScenarioStore((s) => s.removeProposedMicroservice);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  return (
    <FloatingMenu anchor={anchor} onClose={onClose}>
      <button type="button" className="menu-item" onClick={() => setEditing(true)}>
        Edit name or description
      </button>
      <button type="button" className="menu-item danger" onClick={() => setConfirming(true)}>
        Remove proposal
      </button>
      <button type="button" className="menu-item" onClick={onClose}>
        Close
      </button>
      {editing && (
        <ProposedMicroserviceEditor
          tempKey={tempKey}
          name={name}
          description={description}
          team={team ?? null}
          onClose={() => {
            setEditing(false);
            onClose();
          }}
        />
      )}
      {confirming && (
        <ConfirmDialog
          title="Remove proposed microservice"
          message={<p>Remove the proposed microservice "{name}" and its proposed connections from this draft?</p>}
          confirmLabel="Remove"
          danger
          onCancel={() => setConfirming(false)}
          onConfirm={() => {
            remove(tempKey);
            onClose();
          }}
        />
      )}
    </FloatingMenu>
  );
}

/** Edits a proposed microservice; the name must stay unique among baseline and proposed microservices. */
export function ProposedMicroserviceEditor({
  tempKey,
  name: initialName,
  description: initialDescription,
  team: initialTeam = null,
  onClose,
}: {
  tempKey: string;
  name: string;
  description: string | null;
  team?: string | null;
  onClose: () => void;
}) {
  const [team, setTeam] = useState(initialTeam ?? '');
  const update = useScenarioStore((s) => s.updateProposedMicroservice);
  const { view } = useCanvasView();
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription ?? '');
  const clash = view.nodes.some(
    (n) => isMicroservice(n.data) && n.id !== tempKey && normalizeLabel(n.data.name) === normalizeLabel(name),
  );
  const save = () => {
    update(tempKey, { name: name.trim(), description: description.trim() === '' ? null : description, team: team.trim() === '' ? null : team });
    onClose();
  };
  return (
    <Modal
      title={`Edit proposed ${initialName}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="primary" disabled={name.trim() === '' || clash} onClick={save}>
            Save
          </button>
        </>
      }
    >
      <label className="stack">
        Name
        <input aria-label="Proposed microservice name" value={name} maxLength={120} autoFocus onChange={(e) => setName(e.target.value)} />
      </label>
      {clash && (
        <div className="form-error" role="alert">
          A microservice named "{name.trim()}" already exists.
        </div>
      )}
      <label className="stack">
        Description (optional)
        <textarea aria-label="Proposed microservice description" value={description} maxLength={2000} rows={3} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <label className="stack">
        Team (optional)
        <input aria-label="Proposed microservice team" value={team} maxLength={120} onChange={(e) => setTeam(e.target.value)} />
      </label>
    </Modal>
  );
}
