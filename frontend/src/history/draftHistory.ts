import type { ScenarioContent } from '../api/types';
import { useScenarioStore } from '../scenario/scenarioStore';
import { recordEdit, useHistoryStore } from './historyStore';

/** A short description of what changed between two drafts, for the undo button. */
export function describeDraftChange(before: ScenarioContent, after: ScenarioContent): string {
  const lists: [keyof ScenarioContent, string, string][] = [
    ['proposedMicroservices', 'Propose microservice', 'Remove proposed microservice'],
    ['proposedConnections', 'Propose connection', 'Remove proposed connection'],
    ['proposedLinks', 'Propose spec link', 'Remove proposed link'],
    ['addedEndpoints', 'Propose endpoint', 'Remove proposed endpoint'],
    ['addedFields', 'Propose field', 'Remove proposed field'],
    ['connectionChanges', 'Propose connection change', 'Undo connection change'],
    ['notes', 'Add draft note', 'Delete draft note'],
  ];
  for (const [key, added, removed] of lists) {
    const a = (before[key] as unknown[] | undefined)?.length ?? 0;
    const b = (after[key] as unknown[] | undefined)?.length ?? 0;
    if (b > a) return added;
    if (b < a) return removed;
  }
  if (before.removals !== after.removals) return 'Propose removal';
  if (before.positions !== after.positions) return 'Move';
  return 'Draft edit';
}

/**
 * Draft Scenario Mode keeps its own history of draft snapshots (spec 004 FR-010): undo never touches the baseline.
 * Entering or leaving draft mode, or opening another scenario, starts a fresh history. Returns the unsubscribe.
 */
export function installDraftHistory(): () => void {
  return useScenarioStore.subscribe((state, prev) => {
    if (state.isDraftMode !== prev.isDraftMode || state.scenarioId !== prev.scenarioId) {
      useHistoryStore.getState().reset();
      return;
    }
    if (!state.isDraftMode || state.draft === prev.draft) return;
    const before = prev.draft;
    const after = state.draft;
    const apply = (draft: ScenarioContent) => useScenarioStore.setState({ draft, dirty: true });
    recordEdit({ label: describeDraftChange(before, after), undo: () => apply(before), redo: () => apply(after) });
  });
}
