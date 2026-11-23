import { useState } from 'react';
import { useCanvas, useCreateMicroservice, useDeleteConnection, useReassignConnection } from '../api/queries';
import type { Connection, MicroserviceNodeData } from '../api/types';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';

/** Connections whose microservice was deleted: review, delete, reassign or recreate (FR-027). Never auto-deleted. */
export function DanglingConnectionsPanel() {
  const { data: canvas } = useCanvas();
  const dangling = canvas?.danglingConnections ?? [];
  const microservices = (canvas?.nodes ?? [])
    .filter((n) => n.type === 'microservice')
    .map((n) => n.data as MicroserviceNodeData)
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <div className="panel-body">
      <h3 className="panel-title">Dangling connections</h3>
      <p className="muted">These connections lost a microservice. They are kept until you decide what to do.</p>
      {dangling.length === 0 && <p className="muted">None. Every connection connects two microservices.</p>}
      <ul className="plain-list">
        {dangling.map((f) => (
          <DanglingRow key={f.id} connection={f} microservices={microservices} />
        ))}
      </ul>
    </div>
  );
}

function DanglingRow({ connection, microservices }: { connection: Connection; microservices: MicroserviceNodeData[] }) {
  const del = useDeleteConnection();
  const reassign = useReassignConnection();
  const create = useCreateMicroservice();
  const [choosing, setChoosing] = useState(false);
  const missingSource = connection.sourceId == null;
  const missing = missingSource ? connection.sourceName : connection.targetName;
  const onError = (e: unknown) => toast.error(messageOf(e));

  return (
    <li className="list-row dangling-row">
      <span className="grow">
        <strong>{connection.name}</strong>: <span className={missingSource ? 'missing' : ''}>{connection.sourceName}</span> →{' '}
        <span className={!missingSource || connection.targetId == null ? 'missing' : ''}>{connection.targetName}</span>
      </span>
      <button type="button" onClick={() => create.mutate({ name: missing }, { onSuccess: () => toast.success(`${missing} recreated`), onError })}>
        Recreate "{missing}"
      </button>
      <button type="button" onClick={() => setChoosing((c) => !c)}>
        Reassign…
      </button>
      <button type="button" className="danger" onClick={() => del.mutate(connection.id, { onSuccess: () => toast.success('Connection deleted'), onError })}>
        Delete
      </button>
      {choosing && (
        <select
          aria-label={`Reassign ${connection.name}`}
          defaultValue=""
          onChange={(e) => {
            const id = e.target.value;
            reassign.mutate(missingSource ? { id: connection.id, sourceId: id } : { id: connection.id, targetId: id }, {
              onSuccess: () => toast.success('Connection reassigned'),
              onError,
            });
          }}
        >
          <option value="" disabled>
            New {missingSource ? 'source' : 'target'}
          </option>
          {microservices.map((m) => (
            <option key={m.microserviceId} value={m.microserviceId}>
              {m.name}
            </option>
          ))}
        </select>
      )}
    </li>
  );
}
