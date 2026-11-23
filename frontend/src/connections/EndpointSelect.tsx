import { endpointMatchKey } from '../canvas/viewModel';

/**
 * Picks the target's endpoint a connection uses (FR-054). Values compare like the backend (method plus
 * normalized path), so `/orders/{id}` matches `/orders/{orderId}`; only a value the spec really lacks is shown
 * as missing (FR-055).
 */
export function EndpointSelect({
  options,
  value,
  onChange,
  disabled,
}: {
  /** the target card's endpoints, as `METHOD /path` */
  options: readonly string[];
  value: string | null;
  onChange: (endpoint: string | null) => void;
  disabled?: boolean;
}) {
  const match = value === null ? undefined : options.find((o) => endpointMatchKey(o) === endpointMatchKey(value));
  const missing = value !== null && match === undefined;
  const selected = match ?? value ?? '';
  return (
    <label className="stack">
      Endpoint on the target (optional)
      <select aria-label="Connection endpoint" value={selected} disabled={disabled} onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}>
        <option value="">none</option>
        {missing && <option value={value}>⚠ {value} (not in the spec)</option>}
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
      {options.length === 0 && <span className="muted">The target has no spec card; link one to pick its endpoints.</span>}
    </label>
  );
}
