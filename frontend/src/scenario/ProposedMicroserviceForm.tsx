import { useState } from 'react';
import type { HttpMethod, ProposedEndpoint, SimpleField } from '../api/types';
import { useCanvasStore } from '../canvas/canvasStore';
import { kindLabel } from '../canvas/edges/connectionStyle';
import { useCanvasView } from '../canvas/useCanvasView';
import { isCard, isMicroservice } from '../canvas/viewModel';
import { nodeIdOf } from './overlay';
import { ProposedMicroserviceEditor } from './ProposedMicroserviceActions';
import { useScenarioStore } from './scenarioStore';

const METHODS: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

/** "customerId:string, amount:number" → fields. */
export function parseFieldList(text: string): SimpleField[] {
  return text
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '')
    .map((s) => {
      const [name, type] = s.split(':').map((p) => p.trim());
      return type ? { name: name ?? s, type } : { name: name ?? s };
    });
}

function EndpointInputs({ onAdd, label }: { onAdd: (ep: ProposedEndpoint) => void; label: string }) {
  const [method, setMethod] = useState<HttpMethod>('GET');
  const [path, setPath] = useState('/');
  const [inputs, setInputs] = useState('');
  const [outputs, setOutputs] = useState('');
  const valid = path.trim().startsWith('/');
  return (
    <div className="endpoint-form">
      <div className="row">
        <select aria-label="Method" value={method} onChange={(e) => setMethod(e.target.value as HttpMethod)}>
          {METHODS.map((m) => (
            <option key={m}>{m}</option>
          ))}
        </select>
        <input aria-label="Path" value={path} onChange={(e) => setPath(e.target.value)} placeholder="/points" />
      </div>
      <input aria-label="Input fields" value={inputs} onChange={(e) => setInputs(e.target.value)} placeholder="inputs: customerId:string, points:integer" />
      <input aria-label="Output fields" value={outputs} onChange={(e) => setOutputs(e.target.value)} placeholder="outputs: tier:string" />
      <button
        type="button"
        disabled={!valid}
        onClick={() => {
          onAdd({ method, path: path.trim(), inputs: parseFieldList(inputs), outputs: parseFieldList(outputs) });
          setPath('/');
          setInputs('');
          setOutputs('');
        }}
      >
        {label}
      </button>
    </div>
  );
}

/** Everything a draft proposes, with add and remove (FR-037, FR-037a). */
export function ProposedMicroserviceForm() {
  const draft = useScenarioStore((s) => s.draft);
  const addMs = useScenarioStore((s) => s.addProposedMicroservice);
  const removeMs = useScenarioStore((s) => s.removeProposedMicroservice);
  const addEndpoint = useScenarioStore((s) => s.addEndpoint);
  const removeAdded = useScenarioStore((s) => s.removeAddedEndpoint);
  const removeCardEndpoint = useScenarioStore((s) => s.removeProposedCardEndpoint);
  const removeConnection = useScenarioStore((s) => s.removeProposedConnection);
  const removeLink = useScenarioStore((s) => s.removeProposedLink);
  const removeField = useScenarioStore((s) => s.removeField);
  const toggleRemoval = useScenarioStore((s) => s.toggleRemoval);
  const clearChange = useScenarioStore((s) => s.clearConnectionChange);
  const selected = useCanvasStore((s) => s.selectedNodeId);
  const { view, baseline } = useCanvasView();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [team, setTeam] = useState('');
  const updateMs = useScenarioStore((s) => s.updateProposedMicroservice);
  const [withCard, setWithCard] = useState(false);
  const [endpoints, setEndpoints] = useState<ProposedEndpoint[]>([]);
  const [editing, setEditing] = useState<string | null>(null);
  const editingMs = draft.proposedMicroservices.find((p) => p.tempKey === editing);

  const selectedNode = view.nodes.find((n) => n.id === selected);
  const selectedCard = selectedNode && isCard(selectedNode.data) && !selectedNode.data.removed ? selectedNode.data : null;
  const msName = (ref: string) => {
    const n = view.nodes.find((x) => x.id === nodeIdOf(ref));
    return n && isMicroservice(n.data) ? n.data.name : ref;
  };
  const cardTitle = (cardId: string) => {
    const n = baseline.nodes.find((x) => x.id === `card:${cardId}`);
    return n && isCard(n.data) ? n.data.title : cardId;
  };
  const connectionName = (connectionId: string) => baseline.edges.find((e) => e.data.connectionId === connectionId)?.data.name ?? connectionId;
  const r = draft.removals;
  const empty =
    draft.proposedMicroservices.length + draft.proposedLinks.length + draft.proposedConnections.length + draft.addedEndpoints.length +
      draft.addedFields.length + r.connections.length + r.microservices.length + r.links.length + draft.connectionChanges.length ===
    0;

  return (
    <div className="panel-body">
      <h3 className="panel-title">New proposed microservice</h3>
      <input aria-label="Proposed microservice name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Loyalty" />
      <input aria-label="Proposed microservice description" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="description (optional)" />
      <input aria-label="Proposed microservice team" value={team} maxLength={120} onChange={(e) => setTeam(e.target.value)} placeholder="team (optional)" />
      <label className="row">
        <input type="checkbox" checked={withCard} onChange={(e) => setWithCard(e.target.checked)} /> with a proposed spec card
      </label>
      {withCard && (
        <>
          {endpoints.length > 0 && (
            <ul className="plain-list">
              {endpoints.map((ep, i) => (
                <li key={i} className="list-row">
                  <span className="grow">
                    {ep.method} {ep.path}
                  </span>
                  <button type="button" className="icon-button" aria-label="Remove endpoint" onClick={() => setEndpoints(endpoints.filter((_, j) => j !== i))}>
                    ✕
                  </button>
                </li>
              ))}
            </ul>
          )}
          <EndpointInputs label="Add endpoint to the new card" onAdd={(ep) => setEndpoints([...endpoints, ep])} />
        </>
      )}
      <button
        type="button"
        className="primary"
        disabled={name.trim() === ''}
        onClick={() => {
          const key = addMs(name, description.trim() === '' ? null : description, withCard ? { title: `${name.trim()} API`, endpoints } : undefined);
          if (team.trim()) updateMs(key, { team });
          setName('');
          setDescription('');
          setTeam('');
          setEndpoints([]);
          setWithCard(false);
        }}
      >
        Add proposed microservice
      </button>

      <h3 className="panel-title">Proposed endpoint on a spec card</h3>
      {selectedCard ? (
        <>
          <p>
            Selected: <strong>{selectedCard.title}</strong>
          </p>
          <EndpointInputs label={`Add endpoint to ${selectedCard.title}`} onAdd={(ep) => addEndpoint(selectedCard.keyPrefix, ep)} />
        </>
      ) : (
        <p className="muted">Click a spec card on the canvas to add an endpoint to it.</p>
      )}

      <h3 className="panel-title">In this draft</h3>
      <p className="muted">
        Draw a connection between microservices to propose it. Use a microservice's or connection's menu to propose a removal or change, and
        drop an unlinked card on a microservice to propose a link. To propose a field, zoom in, expand an endpoint and use{' '}
        <strong>+ Add field</strong>.
      </p>
      {empty && <p className="muted">Nothing yet.</p>}
      <ul className="plain-list">
        {draft.proposedMicroservices.map((p) => (
          <li key={p.tempKey} className="list-row">
            <span className="grow" title={p.description ?? undefined}>
              <span className="proposed-tag">PROPOSED</span> {p.name}
              {p.card ? ` (card, ${p.card.endpoints.length} endpoints)` : ''}
            </span>
            <button type="button" aria-label={`Edit proposed ${p.name}`} onClick={() => setEditing(p.tempKey)}>
              Edit
            </button>
            <button type="button" onClick={() => removeMs(p.tempKey)}>
              Remove
            </button>
          </li>
        ))}
        {draft.proposedMicroservices.flatMap((p) =>
          (p.card?.endpoints ?? []).map((ep, i) => (
            <li key={`${p.tempKey}-${i}`} className="list-row">
              <span className="grow">
                + {ep.method} {ep.path} on {p.name}
              </span>
              <button type="button" onClick={() => removeCardEndpoint(p.tempKey, i)}>
                Remove
              </button>
            </li>
          )),
        )}
        {draft.addedEndpoints.map((a, i) => (
          <li key={`ae-${i}`} className="list-row">
            <span className="grow">
              + {a.endpoint.method} {a.endpoint.path} on {cardTitle(a.cardId)}
            </span>
            <button type="button" onClick={() => removeAdded(i)}>
              Remove
            </button>
          </li>
        ))}
        {draft.addedFields.map((f, i) => (
          <li key={`af-${i}`} className="list-row">
            <span className="grow">
              + {f.direction === 'IN' ? 'request' : 'response'} field <code>{f.field.name}</code>
              {f.field.type ? `: ${f.field.type}` : ''} on {f.endpointKey.slice(f.cardId.length + 1)} ({cardTitle(f.cardId)})
            </span>
            <button type="button" onClick={() => removeField(f.cardId, f.endpointKey, f.direction, f.field.name)}>
              Remove
            </button>
          </li>
        ))}
        {draft.proposedLinks.map((l) => (
          <li key={`pl-${l.cardId}`} className="list-row">
            <span className="grow">
              link {cardTitle(l.cardId)} → {msName(l.microservice)}
            </span>
            <button type="button" onClick={() => removeLink(l.cardId)}>
              Remove
            </button>
          </li>
        ))}
        {draft.proposedConnections.map((f) => (
          <li key={f.id} className="list-row">
            <span className="grow">
              connection <strong>{f.name}</strong> ({kindLabel(f.kind)}): {msName(f.source)} → {msName(f.target)}
            </span>
            <button type="button" onClick={() => removeConnection(f.id)}>
              Remove
            </button>
          </li>
        ))}
        {r.microservices.map((id) => (
          <li key={`rm-${id}`} className="list-row removal">
            <span className="grow">
              remove microservice <s>{msName(id)}</s>
            </span>
            <button type="button" onClick={() => toggleRemoval('microservices', id)}>
              Undo
            </button>
          </li>
        ))}
        {r.links.map((id) => (
          <li key={`rl-${id}`} className="list-row removal">
            <span className="grow">unlink the spec of {msName(id)}</span>
            <button type="button" onClick={() => toggleRemoval('links', id)}>
              Undo
            </button>
          </li>
        ))}
        {r.connections.map((id) => (
          <li key={`rf-${id}`} className="list-row removal">
            <span className="grow">
              remove connection <s>{connectionName(id)}</s>
            </span>
            <button type="button" onClick={() => toggleRemoval('connections', id)}>
              Undo
            </button>
          </li>
        ))}
        {draft.connectionChanges.map((c) => (
          <li key={`fc-${c.connectionId}`} className="list-row">
            <span className="grow">
              change connection <strong>{connectionName(c.connectionId)}</strong>
              {c.name ? ` → ${c.name}` : ''}
              {c.kind ? ` (kind ${c.kind === 'NONE' ? 'none' : c.kind.toLowerCase()})` : ''}
            </span>
            <button type="button" onClick={() => clearChange(c.connectionId)}>
              Undo
            </button>
          </li>
        ))}
      </ul>
      {editingMs && (
        <ProposedMicroserviceEditor
          tempKey={editingMs.tempKey}
          name={editingMs.name}
          description={editingMs.description ?? null}
          team={editingMs.team ?? null}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  );
}
