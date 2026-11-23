import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  applyNodeChanges,
  Background,
  Controls,
  MarkerType,
  MiniMap,
  Panel,
  ReactFlow,
  useNodesInitialized,
  useReactFlow,
  useStore,
  type Connection,
  type EdgeTypes,
  type IsValidConnection,
  type NodeChange,
  type NodeTypes,
  type OnConnectEnd,
  type OnNodesChange,
} from '@xyflow/react';
import { ApiError } from '../api/client';
import { useCreateConnection, useSaveLayout } from '../api/queries';
import type { XYPosition } from '../api/types';
import { NewConnectionPrompt, type NewConnectionValues } from '../connections/NewConnectionPrompt';
import { useLinkWithConflicts } from '../microservices/useLinkCard';
import { useScenarioStore } from '../scenario/scenarioStore';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';
import { useCanvasStore } from './canvasStore';
import { bundle } from './edges/bundle';
import type { ConnectionRfEdge } from './edges/edgeTypes';
import { ConnectionEdge } from './edges/ConnectionEdge';
import { connectionColor } from './edges/connectionStyle';
import { parallelOffsets } from './edges/parallelOffsets';
import { edgeHighlight, NodeDataCache, nodeHighlight } from './highlight/highlightState';
import { autoArrange, columnRightOf, placeUnpositioned, type LayoutNode, type Positions } from './layout/elkLayout';
import { HighlightSummary } from './HighlightSummary';
import { Legend } from './Legend';
import { WalkthroughBar } from '../walkthrough/WalkthroughBar';
import { TeamFilter } from '../microservices/TeamFilter';
import { recordEdit, resolveId } from '../history/historyStore';

/** `ms:{id}` keys of recreated microservices follow their new ids (spec 004 FR-009). */
function remapPositions(positions: Positions): Positions {
  const out: Positions = {};
  for (const [key, pos] of Object.entries(positions)) {
    const m = /^(ms|card):(.*)$/.exec(key);
    out[m ? `${m[1]}:${resolveId(m[2]!)}` : key] = pos;
  }
  return out;
}
import { MicroserviceNode } from './nodes/MicroserviceNode';
import type { CanvasConnectionNode, CardUiData, MicroserviceUiData, NoteConnectionNode, NoteUiData } from './nodes/nodeTypes';
import { NoteNode } from '../notes/NoteNode';
import { useUpdateNote } from '../api/queries';
import { SpecCardNode } from './nodes/SpecCardNode';
import { useCanvasView } from './useCanvasView';
import { isCard, isMicroservice, targetEndpoints, type ViewNode } from './viewModel';
import { zoomTier } from './zoomTier';

const nodeTypes: NodeTypes = { microservice: MicroserviceNode, specCard: SpecCardNode, note: NoteNode };
const edgeTypes: EdgeTypes = { connection: ConnectionEdge };

const tierSelector = (s: { transform: [number, number, number] }) => zoomTier(s.transform[2]);

function samePos(a: XYPosition | undefined, b: XYPosition | undefined) {
  return a !== undefined && b !== undefined && a.x === b.x && a.y === b.y;
}

const isMicroserviceNodeId = (id: string) => id.startsWith('ms:') || id.startsWith('proposed:');

/** Gap between a microservice and its docked card. */
const DOCK_GAP = 6;

export function ArchitectureCanvas() {
  const { canvas, canvasQuery, view, highlight, isDraft } = useCanvasView();
  const tier = useStore(tierSelector);
  const selectedEdgeId = useCanvasStore((s) => s.selectedEdgeId);
  const selectNode = useCanvasStore((s) => s.selectNode);
  const selectEdge = useCanvasStore((s) => s.selectEdge);
  const clearHighlight = useCanvasStore((s) => s.clearHighlight);
  const setActions = useCanvasStore((s) => s.setActions);
  const draftPositions = useScenarioStore((s) => s.draft.positions);
  const setDraftPosition = useScenarioStore((s) => s.setPosition);
  const addProposedConnection = useScenarioStore((s) => s.addProposedConnection);
  const addProposedLink = useScenarioStore((s) => s.addProposedLink);
  const createConnection = useCreateConnection();
  const saveLayout = useSaveLayout();
  const { link, dialog: linkDialog } = useLinkWithConflicts();

  /** Positions changed locally (drags, placement) that may not be saved yet; top-level nodes only. */
  const [localPositions, setLocalPositions] = useState<Positions>({});
  const [collapsedCards, setCollapsedCards] = useState<string[] | null>(null);
  const [preview, setPreview] = useState<Positions | null>(null);
  const [rfNodes, setRfNodes] = useState<CanvasConnectionNode[]>([]);
  const [newConnection, setNewConnection] = useState<{ source: string; target: string } | null>(null);
  const dataCache = useRef(new NodeDataCache());
  const layoutVersion = useRef(0);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const placing = useRef<string | null>(null);
  const nodesInitialized = useNodesInitialized();
  const rf = useReactFlow();
  const didFit = useRef(false);

  useEffect(() => {
    if (canvas) layoutVersion.current = Math.max(layoutVersion.current, canvas.layoutVersion);
  }, [canvas]);

  const baselineCollapsed = useMemo(() => {
    const out: string[] = [];
    for (const n of canvas?.nodes ?? []) {
      const d = n.data as { cardCollapsed?: boolean; specCardId?: string | null };
      if (n.type === 'microservice' && d.cardCollapsed && d.specCardId) out.push(d.specCardId);
    }
    return out;
  }, [canvas]);

  // ---------------------------------------------------------------- positions

  /** Measured heights of microservice nodes, so a docked card sits just below its microservice (research R8). */
  const [parentHeights, setParentHeights] = useState<Record<string, number>>({});
  useEffect(() => {
    const next: Record<string, number> = {};
    for (const n of rfNodes) if (n.type === 'microservice' && n.measured?.height) next[n.id] = n.measured.height;
    setParentHeights((prev) => {
      const keys = Object.keys(next);
      if (keys.length === Object.keys(prev).length && keys.every((k) => prev[k] === next[k])) return prev;
      return next;
    });
  }, [rfNodes]);

  const basePositions = useMemo(() => {
    const out: Positions = {};
    for (const n of view.nodes) {
      if (n.parentId) {
        // docked: relative to the microservice, just below it
        const h = parentHeights[n.parentId];
        out[n.id] = h ? { x: 0, y: h + DOCK_GAP } : (n.position ?? { x: 0, y: 0 });
        continue;
      }
      const draftPos = isDraft ? draftPositions[n.id] : undefined;
      const pos = draftPos ?? localPositions[n.id] ?? n.position ?? undefined;
      if (pos) out[n.id] = pos;
    }
    // anything still unplaced (new or proposed nodes) goes in a column to the right
    const layoutNodes: LayoutNode[] = view.nodes.filter((n) => !n.parentId).map((n) => ({ id: n.id, position: out[n.id] ?? null }));
    return { ...out, ...columnRightOf(layoutNodes) };
  }, [view.nodes, isDraft, draftPositions, localPositions, parentHeights]);

  const positions = useMemo(() => (preview ? { ...basePositions, ...preview } : basePositions), [basePositions, preview]);

  /** The baseline positions to save: top-level baseline nodes only (docked cards never have their own). */
  const baselinePositionsRef = useRef<Positions>({});
  useEffect(() => {
    if (!canvas) return;
    const out: Positions = {};
    for (const n of canvas.nodes) {
      if (n.parentId) continue;
      const pos = localPositions[n.id] ?? n.position ?? undefined;
      if (pos) out[n.id] = pos;
    }
    baselinePositionsRef.current = out;
  }, [canvas, localPositions]);

  const collapsedRef = useRef<string[]>([]);
  collapsedRef.current = collapsedCards ?? baselineCollapsed;

  const flushSave = useCallback(() => {
    saveTimer.current = null;
    saveLayout.mutate(
      { version: layoutVersion.current, positions: baselinePositionsRef.current, collapsedCards: collapsedRef.current },
      {
        onSuccess: (r) => {
          layoutVersion.current = r.version;
          setCollapsedCards(null);
        },
        onError: (e) => {
          if (e instanceof ApiError && e.status === 409) {
            toast.error('The layout changed in another tab; reloaded the latest positions.');
            setLocalPositions({});
            setCollapsedCards(null);
            void canvasQuery.refetch();
          } else {
            toast.error(`Could not save the layout: ${messageOf(e)}`);
          }
        },
      },
    );
  }, [saveLayout, canvasQuery]);

  /** Debounced (1 s) PUT /api/layout. */
  const scheduleSave = useCallback(() => {
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(flushSave, 1000);
  }, [flushSave]);

  useEffect(
    () => () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    },
    [],
  );

  // entering draft mode: save pending baseline positions now, so nothing baseline-related is written while drafting
  useEffect(() => {
    if (isDraft && saveTimer.current) {
      clearTimeout(saveTimer.current);
      flushSave();
    }
  }, [isDraft, flushSave]);

  // collapse / expand a docked card (FR-014): persisted with the layout, never in draft mode
  /** Applies positions and collapse states to the baseline layout and saves now (undo/redo, spec 004 FR-008). */
  const applyLayout = useCallback(
    (patch: { positions?: Positions; collapse?: Record<string, boolean> }) => {
      const positions = patch.positions ?? {};
      if (Object.keys(positions).length > 0) {
        setLocalPositions((prev) => ({ ...prev, ...positions }));
        baselinePositionsRef.current = { ...baselinePositionsRef.current, ...positions };
      }
      let collapsed = collapsedRef.current;
      for (const [cardId, on] of Object.entries(patch.collapse ?? {})) {
        collapsed = on ? (collapsed.includes(cardId) ? collapsed : [...collapsed, cardId]) : collapsed.filter((c) => c !== cardId);
      }
      collapsedRef.current = collapsed;
      setCollapsedCards(collapsed);
      if (saveTimer.current) clearTimeout(saveTimer.current);
      flushSave();
    },
    [flushSave],
  );

  /** Records a baseline layout change: positions before → after, of the nodes that moved. */
  const recordMove = useCallback(
    (label: string, before: Positions, after: Positions) => {
      if (Object.keys(after).length === 0) return;
      recordEdit({
        label,
        undo: () => useCanvasStore.getState().actions.applyLayout({ positions: remapPositions(before) }),
        redo: () => useCanvasStore.getState().actions.applyLayout({ positions: remapPositions(after) }),
      });
    },
    [],
  );

  // collapse / expand a docked card (FR-014): persisted with the layout, never in draft mode
  useEffect(() => {
    setActions({
      toggleCardCollapsed: (cardId: string) => {
        const current = collapsedRef.current;
        const collapse = !current.includes(cardId);
        applyLayout({ collapse: { [cardId]: collapse } });
        recordEdit({
          label: collapse ? 'Collapse spec' : 'Expand spec',
          undo: () => useCanvasStore.getState().actions.applyLayout({ collapse: { [cardId]: !collapse } }),
          redo: () => useCanvasStore.getState().actions.applyLayout({ collapse: { [cardId]: collapse } }),
        });
      },
      applyLayout,
    });
  }, [setActions, applyLayout]);

  /** Sizes of top-level blocks: a microservice plus its docked card (research R8). */
  const layoutNodesOf = useCallback(
    (source: readonly ViewNode[], pos: (n: ViewNode) => XYPosition | null): LayoutNode[] => {
      const measured = new Map(rfNodes.map((n) => [n.id, n.measured]));
      return source
        .filter((n) => !n.parentId)
        .map((n) => {
          const own = measured.get(n.id);
          let width = own?.width;
          let height = own?.height;
          if (isMicroservice(n.data) && n.data.cardNodeId) {
            const card = measured.get(n.data.cardNodeId);
            if (card?.height) {
              width = Math.max(width ?? 0, card.width ?? 0) || undefined;
              height = (own?.height ?? 84) + DOCK_GAP + card.height;
            }
          }
          return { id: n.id, position: pos(n), width, height, row: isCard(n.data) ? ('cards' as const) : undefined };
        });
    },
    [rfNodes],
  );

  // First load / new nodes: place unpositioned baseline nodes once they are measured, then save.
  useEffect(() => {
    if (!canvas || isDraft || !nodesInitialized) return;
    const unplaced = canvas.nodes.filter((n) => !n.parentId && n.position === null && !localPositions[n.id]);
    if (unplaced.length === 0) return;
    const signature = unplaced.map((n) => n.id).join(',');
    if (placing.current === signature) return;
    placing.current = signature;
    const layoutNodes = layoutNodesOf(view.nodes, (n) => localPositions[n.id] ?? n.position ?? null);
    void placeUnpositioned(layoutNodes, view.edges).then(({ positions: placed }) => {
      // positions set meanwhile (an undo restoring a microservice) win over the automatic placement
      setLocalPositions((prev) => ({ ...placed, ...prev }));
      baselinePositionsRef.current = { ...placed, ...baselinePositionsRef.current };
      scheduleSave();
    });
  }, [canvas, view, isDraft, nodesInitialized, localPositions, layoutNodesOf, scheduleSave]);

  // Fit the view once, after the first nodes are measured and every node has a position.
  useEffect(() => {
    if (didFit.current || !canvas || !nodesInitialized || rfNodes.length === 0) return;
    const pending = canvas.nodes.some((n) => !n.parentId && n.position === null && !localPositions[n.id]);
    if (pending) return;
    didFit.current = true;
    requestAnimationFrame(() => void rf.fitView({ padding: 0.2, maxZoom: 1 }));
  }, [canvas, nodesInitialized, rfNodes.length, localPositions, rf]);

  // ---------------------------------------------------------------- nodes

  const connectionCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const e of view.edges) {
      if (e.data.proposed) continue;
      counts.set(e.source, (counts.get(e.source) ?? 0) + 1);
      counts.set(e.target, (counts.get(e.target) ?? 0) + 1);
    }
    return counts;
  }, [view.edges]);

  // ---------------------------------------------------------------- notes (spec 004 US4)

  const baselineNotes = canvas?.notes;
  const draftNotes = useScenarioStore((s) => s.draft.notes);
  const pendingNote = useCanvasStore((s) => s.pendingNote);
  const setPendingNote = useCanvasStore((s) => s.setPendingNote);
  const updateNote = useUpdateNote();
  const updateDraftNote = useScenarioStore((s) => s.updateNote);
  const noteNodes: NoteConnectionNode[] = useMemo(() => {
    const out: NoteConnectionNode[] = [];
    for (const n of baselineNotes ?? []) {
      const data: NoteUiData = { noteId: n.id, text: n.text, kind: n.kind, proposed: false, readOnly: isDraft };
      out.push({ id: `note:${n.id}`, type: 'note', position: n.position, draggable: !isDraft, data, zIndex: 5 });
    }
    if (isDraft) {
      for (const n of draftNotes ?? []) {
        const data: NoteUiData = { noteId: n.tempKey, text: n.text, kind: n.kind, proposed: true, readOnly: false };
        out.push({ id: `note:${n.tempKey}`, type: 'note', position: n.position, data, zIndex: 5 });
      }
    }
    if (pendingNote) {
      const data: NoteUiData = { noteId: null, text: '', kind: pendingNote.kind, proposed: isDraft, readOnly: false, isNew: true, position: pendingNote.position };
      out.push({ id: 'note:new', type: 'note', position: pendingNote.position, draggable: false, data, zIndex: 5 });
    }
    return out;
  }, [baselineNotes, draftNotes, pendingNote, isDraft]);

  const addNote = useCallback(() => {
    const host = document.querySelector('.canvas-host')?.getBoundingClientRect();
    const center = host ? { x: host.left + host.width / 2 - 100, y: host.top + host.height / 3 } : { x: 200, y: 200 };
    setPendingNote({ position: rf.screenToFlowPosition(center), kind: 'NOTE' });
  }, [rf, setPendingNote]);

  const desiredNodes = useMemo(() => {
    const ids = new Set<string>();
    const nodes: CanvasConnectionNode[] = view.nodes.map((n) => {
      ids.add(n.id);
      const searchFields = highlight.mode === 'search' ? highlight.search.byNode.get(n.id)?.fields : undefined;
      const data = dataCache.current.get(n, nodeHighlight(n, highlight), searchFields, isDraft, connectionCounts.get(n.id) ?? 0);
      const position = positions[n.id] ?? { x: 0, y: 0 };
      if (isCard(n.data)) {
        return {
          id: n.id,
          type: 'specCard' as const,
          position,
          parentId: n.parentId ?? undefined,
          draggable: n.parentId === null,
          hidden: Boolean(n.hidden),
          data: data as CardUiData,
        };
      }
      return { id: n.id, type: 'microservice' as const, position, data: data as MicroserviceUiData };
    });
    dataCache.current.retain(ids);
    return [...nodes, ...noteNodes];
  }, [view.nodes, positions, highlight, isDraft, connectionCounts, noteNodes]);

  // Merge into React Connection state, keeping measured sizes and in-progress drags.
  useEffect(() => {
    setRfNodes((prev) => {
      const byId = new Map(prev.map((n) => [n.id, n]));
      return desiredNodes.map((d) => {
        const p = byId.get(d.id);
        if (!p) return d;
        const position = p.dragging ? p.position : d.position;
        if (
          p.data === d.data &&
          samePos(p.position, position) &&
          p.parentId === d.parentId &&
          p.draggable === d.draggable &&
          p.hidden === d.hidden
        ) {
          return p;
        }
        return { ...p, data: d.data, position, parentId: d.parentId, draggable: d.draggable, hidden: d.hidden } as CanvasConnectionNode;
      });
    });
  }, [desiredNodes]);

  const onNodesChange: OnNodesChange<CanvasConnectionNode> = useCallback((changes: NodeChange<CanvasConnectionNode>[]) => {
    setRfNodes((nds) => applyNodeChanges(changes, nds));
  }, []);

  /**
   * Commits a drag: an unlinked card dropped on a microservice links to it (FR-012, proposed in draft mode);
   * otherwise positions go to the scenario in draft mode, or to the baseline layout.
   */
  const onNodeDragStop = useCallback(
    (_: unknown, node: CanvasConnectionNode, nodes: CanvasConnectionNode[]) => {
      if (node.type === 'specCard' && !node.parentId) {
        const target = rf.getIntersectingNodes(node).find((n) => n.type === 'microservice');
        const card = node.data as CardUiData;
        if (target && card.cardId) {
          const ms = target.data as MicroserviceUiData;
          if (isDraft) addProposedLink(ms.ref, card.cardId);
          else if (ms.microserviceId) link(ms.microserviceId, ms.name, card.cardId);
          return;
        }
      }
      const all = (nodes.length > 0 ? nodes : [node]).filter((n) => !n.parentId);
      // notes keep their position on the note itself, not in the layout (spec 004 research R5)
      for (const n of all) {
        if (n.type !== 'note') continue;
        const note = n.data as NoteUiData;
        if (!note.noteId || note.readOnly || note.isNew) continue;
        if (note.proposed) updateDraftNote(note.noteId, { position: n.position });
        else updateNote.mutate({ id: note.noteId, patch: { position: n.position } }, { onError: (e) => toast.error(messageOf(e)) });
      }
      const moved = all.filter((n) => n.type !== 'note');
      if (moved.length === 0) return;
      if (isDraft) {
        for (const n of moved) setDraftPosition(n.id, n.position);
        return;
      }
      const dropped: Positions = {};
      const before: Positions = {};
      for (const n of moved) {
        if (n.id.startsWith('proposed:') || n.id.startsWith('note:')) continue;
        dropped[n.id] = n.position;
        const was = baselinePositionsRef.current[n.id];
        if (was) before[n.id] = was;
      }
      recordMove(moved.length > 1 ? `Move ${moved.length} nodes` : 'Move', before, dropped);
      setLocalPositions((prev) => ({ ...prev, ...dropped }));
      baselinePositionsRef.current = { ...baselinePositionsRef.current, ...dropped };
      scheduleSave();
    },
    [rf, isDraft, addProposedLink, link, setDraftPosition, scheduleSave, recordMove, updateDraftNote, updateNote],
  );

  // ---------------------------------------------------------------- edges

  const rfEdges: ConnectionRfEdge[] = useMemo(() => {
    const offsets = parallelOffsets(view.edges);
    const flowColors = new Map(view.flows.map((g) => [g.id, g.color]));
    return bundle(view.edges, tier).map(({ edge: e, count, members }) => {
      const flowColor = e.data.flowId ? (flowColors.get(e.data.flowId) ?? null) : null;
      const color = connectionColor(e.data.kind, e.data, flowColor);
      const hl = members.map((m) => edgeHighlight(m, highlight)).reduce((a, b) => (a === 'active' || b === 'active' ? 'active' : a === 'none' || b === 'none' ? 'none' : 'dimmed'));
      return {
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: 'out',
        targetHandle: 'in',
        type: 'connection' as const,
        selected: e.id === selectedEdgeId,
        markerEnd: { type: MarkerType.ArrowClosed, color, width: 18, height: 18 },
        data: {
          ...e.data,
          highlight: hl,
          offset: count > 1 ? 0 : (offsets.get(e.id) ?? 0),
          bundle: count,
          showLabel: tier !== 'low',
          flowColor,
        },
        zIndex: hl === 'active' ? 10 : 0,
      };
    });
  }, [view.edges, view.flows, tier, highlight, selectedEdgeId]);

  // ---------------------------------------------------------------- interactions

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') clearHighlight();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [clearHighlight]);

  const isValidConnection: IsValidConnection<ConnectionRfEdge> = useCallback(
    (c) => c.source !== c.target && isMicroserviceNodeId(c.source) && isMicroserviceNodeId(c.target),
    [],
  );

  const onConnect = useCallback((c: Connection) => {
    if (c.source === c.target) {
      toast.error('A connection must go to a different microservice.');
      return;
    }
    if (!isMicroserviceNodeId(c.source) || !isMicroserviceNodeId(c.target)) {
      toast.error('Connections connect microservices. Link the spec to a microservice first.');
      return;
    }
    setNewConnection({ source: c.source, target: c.target });
  }, []);

  /** Forgiving drop: releasing a drag on a microservice's body connects to it. */
  const onConnectEnd: OnConnectEnd = useCallback(
    (event, state) => {
      if (state.isValid || !state.fromNode || !state.fromHandle) return;
      const point = 'changedTouches' in event ? event.changedTouches[0] : event;
      if (!point) return;
      const target = document
        .elementFromPoint(point.clientX, point.clientY)
        ?.closest<HTMLElement>('.react-flow__node')
        ?.getAttribute('data-id');
      if (!target) return;
      const fromSource = state.fromHandle.type === 'source';
      onConnect({
        source: fromSource ? state.fromNode.id : target,
        target: fromSource ? target : state.fromNode.id,
        sourceHandle: 'out',
        targetHandle: 'in',
      });
    },
    [onConnect],
  );

  const nameOf = (id: string) => {
    const n = view.nodes.find((x) => x.id === id);
    return n && isMicroservice(n.data) ? n.data.name : id;
  };
  const refOf = (id: string) => {
    const n = view.nodes.find((x) => x.id === id);
    return n && isMicroservice(n.data) ? n.data.ref : id;
  };

  const submitConnection = async (values: NewConnectionValues) => {
    if (!newConnection) return;
    if (isDraft) {
      // a proposed connection joining a flow without a step comes after the flow's last step (FR-002)
      const step =
        values.flowId && values.step === null
          ? Math.max(0, ...view.edges.filter((e) => e.data.flowId === values.flowId).map((e) => e.data.step ?? 0)) + 1
          : values.step;
      const id = addProposedConnection({ source: refOf(newConnection.source), target: refOf(newConnection.target), ...values, step });
      if (!id) throw new Error('That connection is already proposed between these microservices.');
      return;
    }
    await createConnection.mutateAsync({
      sourceId: newConnection.source.replace(/^ms:/, ''),
      targetId: newConnection.target.replace(/^ms:/, ''),
      name: values.name,
      kind: values.kind,
      description: values.description,
      flowId: values.flowId,
      endpoint: values.endpoint,
      step: values.flowId ? values.step : null,
    });
    toast.success(`Connection "${values.name}" added`);
  };

  const onAutoArrange = useCallback(async () => {
    const byId = new Map(rfNodes.map((n) => [n.id, n.position]));
    const result = await autoArrange(layoutNodesOf(view.nodes, (n) => byId.get(n.id) ?? null), view.edges);
    setPreview(result);
  }, [rfNodes, view, layoutNodesOf]);

  const keepPreview = useCallback(() => {
    if (!preview) return;
    if (isDraft) {
      for (const [id, pos] of Object.entries(preview)) setDraftPosition(id, pos);
    } else {
      const before: Positions = {};
      for (const id of Object.keys(preview)) {
        const was = baselinePositionsRef.current[id];
        if (was) before[id] = was;
      }
      recordMove('Auto-arrange', before, preview);
      setLocalPositions((prev) => ({ ...prev, ...preview }));
      baselinePositionsRef.current = { ...baselinePositionsRef.current, ...preview };
      flushSave();
    }
    setPreview(null);
  }, [preview, isDraft, setDraftPosition, flushSave, recordMove]);

  if (canvasQuery.isError) {
    return <div className="canvas-message">Could not load the canvas: {messageOf(canvasQuery.error)}</div>;
  }

  return (
    <>
      <ReactFlow<CanvasConnectionNode, ConnectionRfEdge>
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onNodeDragStop={onNodeDragStop}
        onNodeClick={(_, node) => node.type !== 'note' && selectNode(node.id)}
        onEdgeClick={(_, edge) => {
          if (edge.id.startsWith('bundle:')) {
            void rf.fitView({ nodes: [{ id: edge.source }, { id: edge.target }], duration: 400, minZoom: 0.6, padding: 0.3 });
            return;
          }
          selectEdge(edge.id);
        }}
        onPaneClick={() => clearHighlight()}
        onConnect={onConnect}
        onConnectEnd={onConnectEnd}
        connectionRadius={36}
        isValidConnection={isValidConnection}
        minZoom={0.1}
        maxZoom={2}
        proOptions={{ hideAttribution: true }}
        deleteKeyCode={null}
      >
        <Background gap={24} />
        <Controls className="no-export" />
        <MiniMap className="no-export" pannable zoomable />
        <Panel position="top-left" className="no-export canvas-tools">
          {preview ? (
            <div className="preview-bar">
              <span>Auto-arranged preview.</span>
              <button type="button" className="primary" onClick={keepPreview}>
                Keep layout
              </button>
              <button type="button" onClick={() => setPreview(null)}>
                Discard
              </button>
            </div>
          ) : (
            <>
              <button type="button" onClick={() => void onAutoArrange()} disabled={rfNodes.length === 0}>
                Auto-arrange
              </button>
              <button type="button" onClick={addNote} disabled={pendingNote !== null} title={isDraft ? 'Add a note to this draft' : 'Add a note to the canvas'}>
                + Note
              </button>
              <TeamFilter view={view} />
              {rfNodes.some((n) => n.type === 'microservice') && (
                <span className="connect-hint">
                  To {isDraft ? 'propose' : 'add'} a connection, drag from the <span className="hint-dot" /> on the right of a microservice
                  to another microservice. Drop a spec card on a microservice to link it.
                </span>
              )}
            </>
          )}
        </Panel>
        <Panel position="top-right">
          <HighlightSummary highlight={highlight} />
        </Panel>
        <Panel position="top-center" className="no-export">
          <WalkthroughBar view={view} />
        </Panel>
        <Panel position="bottom-center" className="legend-panel">
          <Legend draft={isDraft} flows={view.flows} />
        </Panel>
        {canvas && canvas.nodes.length === 0 && !isDraft && (
          <Panel position="top-center" className="empty-hint">
            Nothing here yet. Use <strong>Import spec</strong>, <strong>New microservice</strong> or <strong>Import connections</strong>.
          </Panel>
        )}
      </ReactFlow>
      {newConnection && (
        <NewConnectionPrompt
          flows={view.flows}
          endpoints={targetEndpoints(view, newConnection.target)}
          sourceName={nameOf(newConnection.source)}
          targetName={nameOf(newConnection.target)}
          proposed={isDraft}
          onSubmit={submitConnection}
          onClose={() => setNewConnection(null)}
        />
      )}
      {linkDialog}
    </>
  );
}

