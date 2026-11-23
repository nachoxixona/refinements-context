import { useMemo } from 'react';
import { useCanvas } from '../api/queries';
import { applyOverlay } from '../scenario/overlay';
import { useScenarioStore } from '../scenario/scenarioStore';
import { useCanvasStore } from './canvasStore';
import { blastRadius } from './highlight/blastRadius';
import type { HighlightInput } from './highlight/highlightState';
import { fieldSearch } from './search/fieldSearch';
import { baselineView, EMPTY_VIEW, isCard, isMicroservice } from './viewModel';
import { normalizeLabel } from './normalize';
import { flowSteps } from '../walkthrough/steps';

/**
 * What the canvas shows: the baseline, or baseline ⊕ draft in Draft Scenario Mode, with the active
 * highlight computed over it (blast radius and search include proposed elements in draft mode).
 */
export function useCanvasView() {
  const canvasQuery = useCanvas();
  const canvas = canvasQuery.data;
  const isDraft = useScenarioStore((s) => s.isDraftMode);
  const draft = useScenarioStore((s) => s.draft);
  const orphans = useScenarioStore((s) => s.orphans);
  const mode = useCanvasStore((s) => s.highlightMode);
  const selectedNodeId = useCanvasStore((s) => s.selectedNodeId);
  const query = useCanvasStore((s) => s.searchQuery);
  const searchMode = useCanvasStore((s) => s.searchMode);
  const selectedFlowId = useCanvasStore((s) => s.selectedFlowId);
  const walkthrough = useCanvasStore((s) => s.walkthrough);
  const teamFilter = useCanvasStore((s) => s.teamFilter);

  const baseline = useMemo(() => (canvas ? baselineView(canvas) : EMPTY_VIEW), [canvas]);
  const view = useMemo(() => (isDraft ? applyOverlay(baseline, draft, orphans) : baseline), [isDraft, baseline, draft, orphans]);

  const search = useMemo(() => fieldSearch(view.nodes, query, searchMode), [view.nodes, query, searchMode]);

  const highlight: HighlightInput = useMemo(() => {
    if (mode === 'blast' && selectedNodeId) {
      const node = view.nodes.find((n) => n.id === selectedNodeId);
      if (node) {
        // a docked card stands for its microservice
        const focus = isCard(node.data) && node.data.ownerId ? node.data.ownerId : node.id;
        return { mode: 'blast', selectedId: focus, blast: blastRadius(focus, view.edges) };
      }
    }
    if (mode === 'search' && query.trim() !== '') return { mode: 'search', search };
    if (mode === 'flow' && selectedFlowId) {
      // a flow's connections and the microservices they connect (FR-051)
      const edgeIds = new Set<string>();
      const nodeIds = new Set<string>();
      for (const e of view.edges) {
        if (e.data.flowId === selectedFlowId && !e.data.removed) {
          edgeIds.add(e.id);
          nodeIds.add(e.source);
          nodeIds.add(e.target);
        }
      }
      return { mode: 'flow', flowId: selectedFlowId, edgeIds, nodeIds };
    }
    if (mode === 'walkthrough' && walkthrough) {
      const steps = flowSteps(view.edges, walkthrough.flowId);
      const index = Math.min(walkthrough.index, Math.max(steps.length - 1, 0));
      const current = steps[index];
      const visitedEdgeIds = new Set<string>();
      const visitedNodeIds = new Set<string>();
      for (const s of steps.slice(0, index)) {
        s.edgeIds.forEach((id) => visitedEdgeIds.add(id));
        s.nodeIds.forEach((id) => visitedNodeIds.add(id));
      }
      return {
        mode: 'walkthrough',
        currentEdgeIds: new Set(current?.edgeIds ?? []),
        currentNodeIds: new Set(current?.nodeIds ?? []),
        visitedEdgeIds,
        visitedNodeIds,
      };
    }
    if (mode === 'team' && teamFilter) {
      const nodeIds = new Set<string>();
      for (const n of view.nodes) {
        if (isMicroservice(n.data) && n.data.team && normalizeLabel(n.data.team) === teamFilter) nodeIds.add(n.id);
      }
      return { mode: 'team', nodeIds };
    }
    return { mode: 'none' };
  }, [mode, selectedNodeId, selectedFlowId, walkthrough, teamFilter, view, query, search]);

  return { canvasQuery, canvas, baseline, view, highlight, search, isDraft };
}
