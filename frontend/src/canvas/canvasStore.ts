import { create } from 'zustand';

export type HighlightMode = 'none' | 'blast' | 'search' | 'flow' | 'walkthrough' | 'team';

/** A flow walkthrough in progress (spec 004 FR-005): the flow and the index of the current step. */
export interface Walkthrough {
  flowId: string;
  index: number;
}
export type SearchMode = 'field' | 'name';
export type SidePanel = 'none' | 'connection' | 'dangling' | 'scenarios' | 'proposals' | 'flows';

/** Callbacks the canvas registers so nodes can reach layout state without passing functions in node data. */
export interface CanvasActions {
  toggleCardCollapsed: (cardId: string) => void;
  /**
   * Applies baseline positions and card collapse states and saves them (used by undo/redo, spec 004 FR-008).
   * Not recorded in the history.
   */
  applyLayout: (patch: { positions?: Record<string, { x: number; y: number }>; collapse?: Record<string, boolean> }) => void;
}

/** Transient canvas UI state. Nothing here is domain state, and nothing is persisted. */
export interface CanvasState {
  selectedNodeId: string | null;
  /** id of the selected connection edge (`connection:{id}` or `proposed:{id}`) */
  selectedEdgeId: string | null;
  highlightMode: HighlightMode;
  /** the flow highlighted from the legend (FR-051) */
  selectedFlowId: string | null;
  searchQuery: string;
  searchMode: SearchMode;
  /** endpointKey → its field list is expanded */
  expandedEndpoints: Record<string, true>;
  /** card node id → its endpoint list is collapsed */
  collapsedNodes: Record<string, true>;
  /** card node id → show all endpoints even above the row cap */
  showAllEndpoints: Record<string, true>;
  presentationMode: boolean;
  panel: SidePanel;
  actions: CanvasActions;
  walkthrough: Walkthrough | null;
  /** a note being written, not saved until it has text (spec 004 edge cases) */
  pendingNote: { position: { x: number; y: number }; kind: 'NOTE' | 'DECISION' | 'QUESTION' } | null;
  /** normalized team name highlighted by the team filter (spec 004 FR-015) */
  teamFilter: string | null;

  selectNode: (id: string | null) => void;
  selectEdge: (id: string | null) => void;
  selectFlow: (id: string | null) => void;
  clearHighlight: () => void;
  setSearch: (query: string, mode?: SearchMode) => void;
  toggleEndpoint: (endpointKey: string) => void;
  toggleNodeCollapsed: (nodeId: string) => void;
  toggleShowAll: (nodeId: string) => void;
  setPresentationMode: (on: boolean) => void;
  openPanel: (panel: SidePanel) => void;
  setActions: (actions: CanvasActions) => void;
  startWalkthrough: (flowId: string) => void;
  /** moves to `index`, clamped by the caller to the flow's steps */
  setWalkthroughStep: (index: number) => void;
  endWalkthrough: () => void;
  setTeamFilter: (team: string | null) => void;
  setPendingNote: (note: CanvasState['pendingNote']) => void;
}

function toggle(record: Record<string, true>, key: string): Record<string, true> {
  const next = { ...record };
  if (next[key]) delete next[key];
  else next[key] = true;
  return next;
}

const noActions: CanvasActions = { toggleCardCollapsed: () => undefined, applyLayout: () => undefined };

export const useCanvasStore = create<CanvasState>()((set) => ({
  selectedNodeId: null,
  selectedEdgeId: null,
  highlightMode: 'none',
  selectedFlowId: null,
  searchQuery: '',
  searchMode: 'field',
  expandedEndpoints: {},
  collapsedNodes: {},
  showAllEndpoints: {},
  presentationMode: false,
  panel: 'none',
  actions: noActions,
  walkthrough: null,
  teamFilter: null,
  pendingNote: null,

  selectNode: (id) =>
    set(() =>
      id === null
        ? { selectedNodeId: null, highlightMode: 'none' }
        : { selectedNodeId: id, highlightMode: 'blast', searchQuery: '', walkthrough: null, teamFilter: null },
    ),
  selectEdge: (id) => set(() => ({ selectedEdgeId: id, panel: id === null ? 'none' : 'connection' })),
  selectFlow: (id) =>
    set((s) =>
      id === null || s.selectedFlowId === id
        ? { selectedFlowId: null, highlightMode: s.highlightMode === 'flow' ? 'none' : s.highlightMode }
        : { selectedFlowId: id, highlightMode: 'flow', selectedNodeId: null, searchQuery: '', walkthrough: null, teamFilter: null },
    ),
  clearHighlight: () =>
    set((s) => ({
      selectedNodeId: null,
      highlightMode: 'none',
      selectedFlowId: null,
      searchQuery: '',
      selectedEdgeId: null,
      walkthrough: null,
      teamFilter: null,
      panel: s.panel === 'connection' ? 'none' : s.panel,
    })),
  setSearch: (query, mode) =>
    set((s) => ({
      searchQuery: query,
      searchMode: mode ?? s.searchMode,
      highlightMode: query.trim() === '' ? 'none' : 'search',
      selectedNodeId: query.trim() === '' ? s.selectedNodeId : null,
    })),
  toggleEndpoint: (key) => set((s) => ({ expandedEndpoints: toggle(s.expandedEndpoints, key) })),
  toggleNodeCollapsed: (key) => set((s) => ({ collapsedNodes: toggle(s.collapsedNodes, key) })),
  toggleShowAll: (key) => set((s) => ({ showAllEndpoints: toggle(s.showAllEndpoints, key) })),
  setPresentationMode: (presentationMode) => set(() => ({ presentationMode })),
  openPanel: (panel) => set(() => ({ panel })),
  setActions: (actions) => set(() => ({ actions })),
  startWalkthrough: (flowId) =>
    set(() => ({
      walkthrough: { flowId, index: 0 },
      highlightMode: 'walkthrough',
      selectedFlowId: null,
      selectedNodeId: null,
      searchQuery: '',
      teamFilter: null,
    })),
  setWalkthroughStep: (index) => set((s) => (s.walkthrough ? { walkthrough: { ...s.walkthrough, index } } : {})),
  endWalkthrough: () =>
    set((s) => ({ walkthrough: null, highlightMode: s.highlightMode === 'walkthrough' ? 'none' : s.highlightMode })),
  setPendingNote: (pendingNote) => set(() => ({ pendingNote })),
  setTeamFilter: (team) =>
    set((s) =>
      team === null
        ? { teamFilter: null, highlightMode: s.highlightMode === 'team' ? 'none' : s.highlightMode }
        : { teamFilter: team, highlightMode: 'team', selectedNodeId: null, selectedFlowId: null, searchQuery: '', walkthrough: null },
    ),
}));
