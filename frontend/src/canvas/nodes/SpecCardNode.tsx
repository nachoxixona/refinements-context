import { Fragment, memo, useEffect, useMemo, useRef, useState } from 'react';
import { useStore, useUpdateNodeInternals, type NodeProps } from '@xyflow/react';
import { useShallow } from 'zustand/react/shallow';
import { useCanvasStore } from '../canvasStore';
import { MAX_ROWS_COLLAPSED, zoomTier } from '../zoomTier';
import { fieldHitKey } from '../search/fieldSearch';
import type { ViewEndpoint, ViewField } from '../viewModel';
import { useScenarioStore } from '../../scenario/scenarioStore';
import { CardActions } from '../../cards/CardActions';
import { MethodBadge } from './MethodBadge';
import type { CardConnectionNode } from './nodeTypes';

const tierSelector = (s: { transform: [number, number, number] }) => zoomTier(s.transform[2]);

const FIELD_TYPES = ['string', 'integer', 'number', 'boolean', 'object', 'array'];

/** Inline form to propose a field (draft mode only). */
function AddFieldForm({
  onAdd,
  onCancel,
}: {
  onAdd: (name: string, type: string) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [type, setType] = useState('string');
  const submit = () => {
    if (name.trim() !== '') onAdd(name.trim(), type);
  };
  return (
    <div className="add-field-form nodrag" onClick={(e) => e.stopPropagation()}>
      <input
        aria-label="New field name"
        autoFocus
        placeholder="fieldName"
        value={name}
        onChange={(e) => setName(e.target.value.replace(/[.\s[\]]/g, ''))}
        onKeyDown={(e) => {
          e.stopPropagation();
          if (e.key === 'Enter') submit();
          if (e.key === 'Escape') onCancel();
        }}
      />
      <select aria-label="New field type" value={type} onChange={(e) => setType(e.target.value)}>
        {FIELD_TYPES.map((t) => (
          <option key={t}>{t}</option>
        ))}
      </select>
      <button type="button" className="primary" disabled={name.trim() === ''} onClick={submit}>
        Add
      </button>
      <button type="button" aria-label="Cancel adding field" onClick={onCancel}>
        ✕
      </button>
    </div>
  );
}

function FieldList({
  title,
  fields,
  prefix,
  endpointKey,
  searchFields,
  draftMode,
}: {
  title: 'IN' | 'OUT';
  fields: ViewField[];
  prefix: string;
  endpointKey: string;
  searchFields?: ReadonlyMap<string, 'IN' | 'OUT'>;
  draftMode: boolean;
}) {
  const [adding, setAdding] = useState(false);
  /** path of the object/array field a nested field is being added to */
  const [addingInto, setAddingInto] = useState<string | null>(null);
  const addField = useScenarioStore((s) => s.addField);
  const removeField = useScenarioStore((s) => s.removeField);
  return (
    <div className="field-section">
      <div className="field-section-title">{title === 'IN' ? 'IN · request' : 'OUT · response'}</div>
      {fields.length === 0 && !adding ? (
        <div className="field-empty">none</div>
      ) : (
        <ul className="field-list">
          {fields.map((f, i) => {
            const depth = (f.path.match(/\./g) ?? []).length;
            const hit = searchFields?.get(fieldHitKey(endpointKey, f.path));
            const container = f.type === 'object' || f.type === 'array';
            const childPrefix = f.type === 'array' ? `${f.path}[]` : f.path;
            return (
              <Fragment key={`${f.path}|${f.variant ?? ''}|${i}`}>
                <li
                  className={`field-row${hit ? ' field-hit' : ''}${f.proposed ? ' field-proposed' : ''}${f.orphaned ? ' field-orphaned' : ''}`}
                  style={{ paddingLeft: 6 + depth * 12 }}
                  title={f.path}
                >
                  {f.proposed && (
                    <span className="proposed-plus" aria-label="proposed field">
                      +
                    </span>
                  )}
                  <span className="field-name">{f.name}</span>
                  <span className="field-type">
                    {f.type}
                    {f.format ? `(${f.format})` : ''}
                    {f.required ? ' *' : ''}
                    {f.variant !== null && f.variant !== undefined ? ` ⟨${f.variant}⟩` : ''}
                    {f.truncated ? ' …' : ''}
                  </span>
                  {!f.proposed && <span className="field-loc">{f.location.toLowerCase()}</span>}
                  {hit && <span className={`hit-tag hit-${hit.toLowerCase()}`}>{hit}</span>}
                  {container && draftMode && !f.truncated && (
                    <button
                      type="button"
                      className="icon-button field-add-child nodrag"
                      aria-label={`Add field inside ${f.path}`}
                      title={`Add a field inside ${f.name}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        setAddingInto(f.path);
                      }}
                    >
                      +
                    </button>
                  )}
                  {f.proposed && draftMode && (
                    <button
                      type="button"
                      className="icon-button field-remove nodrag"
                      aria-label={`Remove proposed field ${f.name}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        removeField(prefix, endpointKey, title, f.name);
                      }}
                    >
                      ✕
                    </button>
                  )}
                </li>
                {addingInto === f.path && (
                  <li className="field-row" style={{ paddingLeft: 6 + (depth + 1) * 12 }}>
                    <AddFieldForm
                      onCancel={() => setAddingInto(null)}
                      onAdd={(name, type) => {
                        addField(prefix, endpointKey, title, { name: `${childPrefix}.${name}`, type });
                        setAddingInto(null);
                      }}
                    />
                  </li>
                )}
              </Fragment>
            );
          })}
        </ul>
      )}
      {draftMode &&
        (adding ? (
          <AddFieldForm
            onCancel={() => setAdding(false)}
            onAdd={(name, type) => {
              addField(prefix, endpointKey, title, { name, type });
              setAdding(false);
            }}
          />
        ) : (
          <button
            type="button"
            className="add-field nodrag"
            aria-label={`Add ${title === 'IN' ? 'request' : 'response'} field to ${endpointKey}`}
            onClick={(e) => {
              e.stopPropagation();
              setAdding(true);
            }}
          >
            + Add field
          </button>
        ))}
    </div>
  );
}

function EndpointRow({
  ep,
  prefix,
  expandable,
  expanded,
  onToggle,
  searchFields,
  draftMode,
}: {
  ep: ViewEndpoint;
  prefix: string;
  expandable: boolean;
  expanded: boolean;
  onToggle: () => void;
  searchFields?: ReadonlyMap<string, 'IN' | 'OUT'>;
  draftMode: boolean;
}) {
  return (
    <li
      className={`endpoint${ep.proposed ? ' endpoint-proposed' : ''}${ep.orphaned ? ' endpoint-orphaned' : ''}`}
    >
      <div className="endpoint-row">
        {ep.proposed && (
          <span className="proposed-plus" aria-label="proposed endpoint">
            +
          </span>
        )}
        <MethodBadge method={ep.method} />
        <span className="endpoint-path" title={ep.summary ?? ep.path}>
          {ep.path}
        </span>
        {ep.orphaned && <span title="References a baseline spec that no longer exists">⚠</span>}
        {expandable && (
          <button
            type="button"
            className="chevron nodrag"
            aria-expanded={expanded}
            aria-label={expanded ? `Collapse ${ep.method} ${ep.path}` : `Expand ${ep.method} ${ep.path}`}
            onClick={onToggle}
          >
            {expanded ? '▾' : '▸'}
          </button>
        )}
      </div>
      {expandable && expanded && (
        <div className="endpoint-fields">
          <FieldList
            title="IN"
            fields={ep.inputs}
            prefix={prefix}
            endpointKey={ep.endpointKey}
            searchFields={searchFields}
            draftMode={draftMode}
          />
          <FieldList
            title="OUT"
            fields={ep.outputs}
            prefix={prefix}
            endpointKey={ep.endpointKey}
            searchFields={searchFields}
            draftMode={draftMode}
          />
        </div>
      )}
    </li>
  );
}


function formatTime(iso: string | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * A spec card with zoom-dependent detail: < 0.45 title + endpoint count, 0.45–0.9 endpoint rows with method
 * badges, ≥ 0.9 expandable request/response fields. Visibly distinct from microservice nodes (FR-015).
 */
function SpecCardNodeImpl({ id, data }: NodeProps<CardConnectionNode>) {
  const tier = useStore(tierSelector);
  const updateNodeInternals = useUpdateNodeInternals();
  const endpointKeys = useMemo(() => data.endpoints.map((e) => e.endpointKey), [data.endpoints]);
  const expanded = useCanvasStore(useShallow((s) => endpointKeys.filter((k) => s.expandedEndpoints[k])));
  const collapsed = useCanvasStore((s) => Boolean(s.collapsedNodes[id]));
  const showAll = useCanvasStore((s) => Boolean(s.showAllEndpoints[id]));
  const toggleEndpoint = useCanvasStore((s) => s.toggleEndpoint);
  const toggleCollapsed = useCanvasStore((s) => s.toggleNodeCollapsed);
  const toggleShowAll = useCanvasStore((s) => s.toggleShowAll);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);

  const expandedSet = useMemo(() => new Set(expanded), [expanded]);
  const visible = showAll ? data.endpoints : data.endpoints.slice(0, MAX_ROWS_COLLAPSED);
  const hidden = data.endpoints.length - visible.length;

  useEffect(() => {
    updateNodeInternals(id);
  }, [id, tier, collapsed, showAll, expanded, data.endpoints, updateNodeInternals]);

  const failed = data.baseline?.lastSyncStatus === 'FAILED';
  const classes = ['card-node', `hl-${data.highlight}`, `tier-${tier}`];
  if (data.ownerId) classes.push('card-docked');
  if (data.proposed || data.linkProposed) classes.push('card-proposed');
  if (data.removed) classes.push('card-removed');
  const canEditFields = data.draftMode && !data.removed;

  return (
    <div className={classes.join(' ')} data-testid={`node-${id}`}>
      <div className="node-header card-header">
        <span className="card-kind" aria-hidden="true">
          API
        </span>
        {(data.proposed || data.linkProposed) && <span className="proposed-tag">{data.proposed ? 'PROPOSED' : 'PROPOSED LINK'}</span>}
        <span className="node-title" title={data.title}>
          {data.title}
        </span>
        {data.apiVersion && tier !== 'low' && <span className="node-version">v{data.apiVersion}</span>}
        {failed && (
          <span className="sync-failed" title={data.baseline?.lastSyncAt ? `Last attempt ${data.baseline.lastSyncAt}` : ''}>
            sync failed
          </span>
        )}
        {tier !== 'low' && data.endpoints.length > 0 && (
          <button type="button" className="chevron nodrag" aria-label={collapsed ? 'Show endpoints' : 'Hide endpoints'} onClick={() => toggleCollapsed(id)}>
            {collapsed ? '▸' : '▾'}
          </button>
        )}
        {data.baseline && (
          <button
            type="button"
            ref={menuButton}
            className="node-menu-button nodrag"
            aria-label={`Actions for ${data.title}`}
            onClick={(e) => {
              e.stopPropagation();
              setMenuOpen((o) => !o);
            }}
          >
            ⋯
          </button>
        )}
      </div>
      {tier !== 'low' && data.baseline && (
        <div className="card-source" title={data.baseline.sourceUrl ?? 'pasted'}>
          {data.baseline.sourceType === 'URL' ? data.baseline.sourceUrl : 'pasted'} · {formatTime(data.baseline.lastSyncAt)}
          {!data.ownerId && <span className="unlinked-tag"> · unlinked</span>}
        </div>
      )}
      {menuOpen && data.baseline && data.cardId && (
        <CardActions
          cardId={data.cardId}
          title={data.title}
          sourceType={data.baseline.sourceType}
          ownerId={data.ownerId}
          draftMode={data.draftMode}
          anchor={menuButton.current}
          onClose={() => setMenuOpen(false)}
        />
      )}
      {tier === 'low' || collapsed ? (
        <div className="node-summary">
          {data.endpoints.length === 0 ? 'no endpoints' : `${data.endpoints.length} endpoint${data.endpoints.length === 1 ? '' : 's'}`}
        </div>
      ) : data.endpoints.length === 0 ? (
        <div className="node-empty">No endpoints in this spec</div>
      ) : (
        <>
          <ul className={`endpoint-list${data.endpoints.length > MAX_ROWS_COLLAPSED ? ' endpoint-list-scroll nowheel' : ''}`}>
            {visible.map((ep) => (
              <EndpointRow
                key={ep.endpointKey + (ep.proposed ? '+' : '')}
                ep={ep}
                prefix={data.keyPrefix}
                draftMode={canEditFields}
                expandable={tier === 'high'}
                expanded={expandedSet.has(ep.endpointKey)}
                onToggle={() => toggleEndpoint(ep.endpointKey)}
                searchFields={data.searchFields}
              />
            ))}
          </ul>
          {data.endpoints.length > MAX_ROWS_COLLAPSED && (
            <button type="button" className="show-all nodrag" onClick={() => toggleShowAll(id)}>
              {showAll ? 'Show fewer' : `Show all (${hidden} more)`}
            </button>
          )}
        </>
      )}
    </div>
  );
}

export const SpecCardNode = memo(SpecCardNodeImpl);
