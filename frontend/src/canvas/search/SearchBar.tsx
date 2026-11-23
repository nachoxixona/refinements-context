import { useMemo, useState } from 'react';
import { useReactFlow } from '@xyflow/react';
import { useCanvasStore } from '../canvasStore';
import { useCanvasView } from '../useCanvasView';

const MAX_RESULTS = 30;

/** Search across spec cards with jump-to (FR-030 – FR-032). */
export function SearchBar() {
  const query = useCanvasStore((s) => s.searchQuery);
  const mode = useCanvasStore((s) => s.searchMode);
  const setSearch = useCanvasStore((s) => s.setSearch);
  const expand = useCanvasStore((s) => s.toggleEndpoint);
  const expanded = useCanvasStore((s) => s.expandedEndpoints);
  const { search } = useCanvasView();
  const rf = useReactFlow();
  const [open, setOpen] = useState(false);

  const results = useMemo(() => search.hits.slice(0, MAX_RESULTS), [search.hits]);
  const active = query.trim() !== '';

  const jump = (nodeId: string, endpointKey?: string, ownerId?: string | null) => {
    // a card collapsed to its "API" badge is hidden: focus its microservice instead
    const hidden = rf.getNode(nodeId)?.hidden;
    const focus = hidden && ownerId ? ownerId : nodeId;
    void rf.fitView({ nodes: [{ id: focus }], duration: 400, maxZoom: 1.1, padding: 0.4 });
    if (endpointKey && !expanded[endpointKey]) expand(endpointKey);
    setOpen(false);
  };

  return (
    <div className="search" onBlur={(e) => !e.currentTarget.contains(e.relatedTarget) && setOpen(false)}>
      <div className="search-mode" role="radiogroup" aria-label="Search mode">
        {(['field', 'name'] as const).map((m) => (
          <button key={m} type="button" role="radio" aria-checked={mode === m} className={mode === m ? 'active' : ''} onClick={() => setSearch(query, m)}>
            {m === 'field' ? 'Field' : 'Name'}
          </button>
        ))}
      </div>
      <input
        type="search"
        aria-label={mode === 'field' ? 'Search fields and endpoints' : 'Search microservices, specs and endpoints'}
        placeholder={mode === 'field' ? 'Field or endpoint, e.g. customerId' : 'Microservice, spec or endpoint'}
        value={query}
        onFocus={() => setOpen(true)}
        onChange={(e) => {
          setSearch(e.target.value);
          setOpen(true);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setSearch('');
            (e.target as HTMLInputElement).blur();
          }
        }}
      />
      {open && active && (
        <div className="search-results" role="listbox">
          {results.length === 0 ? (
            <div className="search-empty">No matches</div>
          ) : (
            results.map((h, i) => (
              <button key={i} type="button" role="option" aria-selected={false} className="search-result" onClick={() => jump(h.nodeId, h.endpointKey, h.ownerId)}>
                <span className="result-service">{h.cardTitle ?? h.ownerName}</span>
                <span className={h.ownerName ? 'result-owner' : 'result-owner unlinked'}>
                  {h.cardTitle === null ? 'microservice' : (h.ownerName ?? 'unlinked')}
                </span>
                {h.method && (
                  <span className="result-endpoint">
                    {h.method} {h.path}
                  </span>
                )}
                {h.fieldPath && <span className="result-field">{h.fieldPath}</span>}
                {h.direction && <span className={`hit-tag hit-${h.direction.toLowerCase()}`}>{h.direction}</span>}
              </button>
            ))
          )}
          {search.hits.length > MAX_RESULTS && <div className="search-more">{search.hits.length - MAX_RESULTS} more…</div>}
        </div>
      )}
    </div>
  );
}
