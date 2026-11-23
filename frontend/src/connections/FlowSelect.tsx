import type { Flow } from '../api/types';
import { FLOW_COLORS } from '../canvas/edges/connectionStyle';

/** Picks a connection's flow (FR-050); "none" leaves it ungrouped. */
export function FlowSelect({
  flows,
  value,
  onChange,
  disabled,
}: {
  flows: readonly Flow[];
  value: string | null;
  onChange: (flowId: string | null) => void;
  disabled?: boolean;
}) {
  const current = flows.find((g) => g.id === value);
  return (
    <label className="stack">
      Flow
      <span className="row">
        <span className="flow-swatch" style={{ background: current ? FLOW_COLORS[current.color] : 'transparent' }} aria-hidden="true" />
        <select aria-label="Connection flow" value={value ?? ''} disabled={disabled} onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}>
          <option value="">none</option>
          {flows.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </select>
      </span>
      {flows.length === 0 && <span className="muted">Create flows from the legend to color related connections.</span>}
    </label>
  );
}
