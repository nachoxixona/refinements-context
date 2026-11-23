import { useState } from 'react';
import { ApiError } from '../api/client';
import { useCreateMicroservice, useUpdateMicroservice } from '../api/queries';
import { Modal } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';

export interface Ownership {
  team: string | null;
  repositoryUrl: string | null;
  docsUrl: string | null;
}

/** An absolute http(s) URL, like the backend's rule (spec 004 data-model.md §2). */
export function isHttpUrl(value: string): boolean {
  try {
    const u = new URL(value.trim());
    return (u.protocol === 'http:' || u.protocol === 'https:') && u.hostname !== '';
  } catch {
    return false;
  }
}

/** Create, rename or describe a microservice and record its ownership (FR-010, FR-011, spec 004 FR-013). */
export function MicroserviceForm({
  existing,
  onClose,
  onCreated,
}: {
  existing?: { id: string; name: string; description: string | null } & Partial<Ownership>;
  onClose: () => void;
  onCreated?: (id: string) => void;
}) {
  const create = useCreateMicroservice();
  const update = useUpdateMicroservice();
  const [name, setName] = useState(existing?.name ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [team, setTeam] = useState(existing?.team ?? '');
  const [repositoryUrl, setRepositoryUrl] = useState(existing?.repositoryUrl ?? '');
  const [docsUrl, setDocsUrl] = useState(existing?.docsUrl ?? '');
  const badUrl = [repositoryUrl, docsUrl].some((u) => u.trim() !== '' && !isHttpUrl(u));
  const [error, setError] = useState<string | null>(null);
  const pending = create.isPending || update.isPending;

  const submit = () => {
    setError(null);
    const desc = description.trim() === '' ? null : description;
    const own: Ownership = {
      team: team.trim() === '' ? null : team.trim(),
      repositoryUrl: repositoryUrl.trim() === '' ? null : repositoryUrl.trim(),
      docsUrl: docsUrl.trim() === '' ? null : docsUrl.trim(),
    };
    const onError = (e: unknown) =>
      setError(e instanceof ApiError && e.conflict === 'DUPLICATE_NAME' ? `A microservice named "${name.trim()}" already exists.` : messageOf(e));
    if (existing) {
      const patch: { name?: string; description?: string | null } & Partial<Ownership> = {};
      if (name.trim() !== existing.name) patch.name = name.trim();
      if (desc !== existing.description) patch.description = desc;
      if (own.team !== (existing.team ?? null)) patch.team = own.team;
      if (own.repositoryUrl !== (existing.repositoryUrl ?? null)) patch.repositoryUrl = own.repositoryUrl;
      if (own.docsUrl !== (existing.docsUrl ?? null)) patch.docsUrl = own.docsUrl;
      if (Object.keys(patch).length === 0) {
        onClose();
        return;
      }
      update.mutate({ id: existing.id, patch }, { onSuccess: () => onClose(), onError });
    } else {
      create.mutate(
        { name: name.trim(), description: desc, ...own },
        {
          onSuccess: (r) => {
            if (r.reattachedConnectionIds.length > 0) {
              toast.success(`${r.microservice.name} created; ${r.reattachedConnectionIds.length} dangling connection(s) re-attached`);
            }
            onCreated?.(r.microservice.id);
            onClose();
          },
          onError,
        },
      );
    }
  };

  return (
    <Modal
      title={existing ? `Edit ${existing.name}` : 'New microservice'}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="primary" disabled={name.trim() === '' || pending || badUrl} onClick={submit}>
            {existing ? 'Save' : 'Create'}
          </button>
        </>
      }
    >
      <label className="stack">
        Name
        <input aria-label="Microservice name" value={name} maxLength={120} autoFocus onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && name.trim() && submit()} />
      </label>
      <label className="stack">
        Description (optional)
        <textarea aria-label="Microservice description" value={description} maxLength={2000} rows={3} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <fieldset className="ownership">
        <legend>Ownership (optional)</legend>
        <label className="stack">
          Team
          <input aria-label="Microservice team" value={team} maxLength={120} list="known-teams" placeholder="Payments" onChange={(e) => setTeam(e.target.value)} />
        </label>
        <label className="stack">
          Repository URL
          <input aria-label="Repository URL" type="url" value={repositoryUrl} maxLength={2048} placeholder="https://github.com/acme/billing" onChange={(e) => setRepositoryUrl(e.target.value)} />
        </label>
        <label className="stack">
          Documentation URL
          <input aria-label="Documentation URL" type="url" value={docsUrl} maxLength={2048} placeholder="https://docs.acme.dev/billing" onChange={(e) => setDocsUrl(e.target.value)} />
        </label>
        {badUrl && <p className="note warning">Links must start with http:// or https://.</p>}
      </fieldset>
      {error && (
        <div className="form-error" role="alert">
          {error}
        </div>
      )}
    </Modal>
  );
}
