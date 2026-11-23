import { useCanvasStore } from '../canvas/canvasStore';
import { normalizeLabel } from '../canvas/normalize';
import { isMicroservice, type GraphView } from '../canvas/viewModel';

/** Emphasizes one team's microservices, their cards and the connections between them (spec 004 FR-015). */
export function TeamFilter({ view }: { view: GraphView }) {
  const teamFilter = useCanvasStore((s) => s.teamFilter);
  const setTeamFilter = useCanvasStore((s) => s.setTeamFilter);
  const teams = new Map<string, string>();
  for (const n of view.nodes) {
    if (isMicroservice(n.data) && n.data.team) {
      const key = normalizeLabel(n.data.team);
      if (!teams.has(key)) teams.set(key, n.data.team);
    }
  }
  if (teams.size === 0) return null;
  const sorted = [...teams.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  return (
    <label className="team-filter row">
      Team
      <select aria-label="Team filter" value={teamFilter ?? ''} onChange={(e) => setTeamFilter(e.target.value === '' ? null : e.target.value)}>
        <option value="">all teams</option>
        {sorted.map(([key, label]) => (
          <option key={key} value={key}>
            {label}
          </option>
        ))}
      </select>
    </label>
  );
}
