import type { EdgeHighlight } from '../edges/edgeTypes';
import type { CardUiData, MicroserviceUiData, NodeHighlight } from '../nodes/nodeTypes';
import type { Direction, SearchResult } from '../search/fieldSearch';
import { isCard, type ViewNode } from '../viewModel';
import type { BlastRadius } from './blastRadius';

export type HighlightInput =
  | { mode: 'none' }
  | { mode: 'blast'; selectedId: string; blast: BlastRadius }
  | { mode: 'search'; search: SearchResult }
  | { mode: 'flow'; flowId: string; edgeIds: Set<string>; nodeIds: Set<string> }
  /** spec 004 FR-005: the current step is emphasized, earlier steps quiet, the rest dimmed */
  | {
      mode: 'walkthrough';
      currentEdgeIds: Set<string>;
      currentNodeIds: Set<string>;
      visitedEdgeIds: Set<string>;
      visitedNodeIds: Set<string>;
    }
  /** spec 004 FR-015: one team's microservices */
  | { mode: 'team'; nodeIds: Set<string> };

function microserviceHighlight(id: string, input: HighlightInput): NodeHighlight {
  switch (input.mode) {
    case 'none':
      return 'none';
    case 'blast': {
      if (id === input.selectedId) return 'selected';
      const up = input.blast.upstream.has(id);
      const down = input.blast.downstream.has(id);
      if (up && down) return 'both';
      if (up) return 'upstream';
      if (down) return 'downstream';
      return 'dimmed';
    }
    case 'search':
      return input.search.byNode.has(id) || input.search.owners.has(id) ? 'search' : 'dimmed';
    case 'flow':
      return input.nodeIds.has(id) ? 'search' : 'dimmed';
    case 'walkthrough':
      return input.currentNodeIds.has(id) ? 'search' : input.visitedNodeIds.has(id) ? 'none' : 'dimmed';
    case 'team':
      return input.nodeIds.has(id) ? 'search' : 'dimmed';
  }
}

/**
 * A docked card shares its microservice's emphasis in blast mode (FR-033); in search mode cards are
 * highlighted by their own hits and microservices by their cards' hits (FR-031).
 */
export function nodeHighlight(node: Pick<ViewNode, 'id' | 'data'>, input: HighlightInput): NodeHighlight {
  if (isCard(node.data)) {
    if (input.mode === 'search') return input.search.byNode.has(node.id) ? 'search' : 'dimmed';
    if (input.mode === 'flow' || input.mode === 'team') {
      return node.data.ownerId && input.nodeIds.has(node.data.ownerId) ? 'none' : 'dimmed';
    }
    if (input.mode === 'walkthrough') {
      const owner = node.data.ownerId;
      return owner && (input.currentNodeIds.has(owner) || input.visitedNodeIds.has(owner)) ? 'none' : 'dimmed';
    }
    if (input.mode === 'blast' && node.id === input.selectedId) return 'selected';
    if (node.data.ownerId) {
      const owner = microserviceHighlight(node.data.ownerId, input);
      return owner === 'selected' ? 'both' : owner;
    }
    return input.mode === 'none' ? 'none' : 'dimmed';
  }
  return microserviceHighlight(node.id, input);
}

export function edgeHighlight(edge: { id: string; source: string; target: string }, input: HighlightInput): EdgeHighlight {
  switch (input.mode) {
    case 'none':
      return 'none';
    case 'blast':
      return input.blast.edgeIds.has(edge.id) ? 'active' : 'dimmed';
    case 'search': {
      const lit = (id: string) => input.search.byNode.has(id) || input.search.owners.has(id);
      return lit(edge.source) && lit(edge.target) ? 'none' : 'dimmed';
    }
    case 'flow':
      return input.edgeIds.has(edge.id) ? 'active' : 'dimmed';
    case 'walkthrough':
      return input.currentEdgeIds.has(edge.id) ? 'active' : input.visitedEdgeIds.has(edge.id) ? 'none' : 'dimmed';
    case 'team':
      return input.nodeIds.has(edge.source) && input.nodeIds.has(edge.target) ? 'none' : 'dimmed';
  }
}

type UiData = MicroserviceUiData | CardUiData;

interface CacheEntry {
  base: ViewNode['data'];
  highlight: NodeHighlight;
  searchFields: ReadonlyMap<string, Direction> | undefined;
  draftMode: boolean;
  connectionCount: number;
  value: UiData;
}

/**
 * Returns the same data object for a node whose inputs did not change, so React.memo'd nodes skip
 * re-rendering when the selection elsewhere changes.
 */
export class NodeDataCache {
  private entries = new Map<string, CacheEntry>();

  get(
    node: ViewNode,
    highlight: NodeHighlight,
    searchFields: ReadonlyMap<string, Direction> | undefined,
    draftMode: boolean,
    connectionCount = 0,
  ): UiData {
    const prev = this.entries.get(node.id);
    if (
      prev &&
      prev.base === node.data &&
      prev.highlight === highlight &&
      sameHits(prev.searchFields, searchFields) &&
      prev.draftMode === draftMode &&
      prev.connectionCount === connectionCount
    ) {
      return prev.value;
    }
    const value: UiData = isCard(node.data)
      ? { ...node.data, highlight, searchFields, draftMode }
      : { ...node.data, highlight, draftMode, connectionCount };
    this.entries.set(node.id, { base: node.data, highlight, searchFields, draftMode, connectionCount, value });
    return value;
  }

  retain(ids: ReadonlySet<string>): void {
    for (const id of this.entries.keys()) if (!ids.has(id)) this.entries.delete(id);
  }
}

function sameHits(a: ReadonlyMap<string, Direction> | undefined, b: ReadonlyMap<string, Direction> | undefined) {
  if (a === b) return true;
  if (!a || !b || a.size !== b.size) return false;
  for (const [k, v] of a) if (b.get(k) !== v) return false;
  return true;
}
