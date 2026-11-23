import { normalizeName } from '../normalize';
import type { SearchMode } from '../canvasStore';
import { isCard, isMicroservice, type ViewNode } from '../viewModel';

export type Direction = 'IN' | 'OUT';

export interface SearchHit {
  /** the card node (or, in name mode, the microservice node) that matched */
  nodeId: string;
  cardTitle: string | null;
  /** linked microservice name, or null for an unlinked card (FR-031) */
  ownerName: string | null;
  ownerId: string | null;
  endpointKey?: string;
  method?: string;
  path?: string;
  fieldPath?: string;
  direction?: Direction;
}

export interface NodeHits {
  /** `${endpointKey}|${fieldPath}` → direction, for marking field rows */
  fields: Map<string, Direction>;
  directions: Set<Direction>;
  endpointKeys: Set<string>;
}

export interface SearchResult {
  hits: SearchHit[];
  byNode: Map<string, NodeHits>;
  /** microservice node ids whose docked card has a hit */
  owners: Set<string>;
}

export const fieldHitKey = (endpointKey: string, fieldPath: string) => `${endpointKey}|${fieldPath}`;

function nodeHits(byNode: Map<string, NodeHits>, key: string): NodeHits {
  let h = byNode.get(key);
  if (!h) {
    h = { fields: new Map(), directions: new Set(), endpointKeys: new Set() };
    byNode.set(key, h);
  }
  return h;
}

/**
 * Field mode (FR-030): case-insensitive and partial, normalized like the backend, over the field names and
 * paths the backend already flattened, plus endpoint paths and operation ids. Name mode: microservice
 * names, card titles, endpoint paths and operation ids. Pure filtering of backend data (Principle V).
 */
export function fieldSearch(nodes: readonly ViewNode[], query: string, mode: SearchMode): SearchResult {
  const hits: SearchHit[] = [];
  const byNode = new Map<string, NodeHits>();
  const owners = new Set<string>();
  const raw = query.trim();
  if (raw === '') return { hits, byNode, owners };
  const q = normalizeName(raw);
  const lower = raw.toLowerCase();

  for (const node of nodes) {
    const d = node.data;
    if (isMicroservice(d)) {
      // name search also matches team names (spec 004 FR-015)
      if (mode === 'name' && (d.name.toLowerCase().includes(lower) || (d.team ?? '').toLowerCase().includes(lower))) {
        hits.push({ nodeId: node.id, cardTitle: d.cardTitle, ownerName: d.name, ownerId: node.id });
        nodeHits(byNode, node.id);
      }
      continue;
    }
    if (!isCard(d)) continue;
    const base = { nodeId: node.id, cardTitle: d.title, ownerName: d.ownerName, ownerId: d.ownerId };
    const mark = (endpointKey: string) => {
      nodeHits(byNode, node.id).endpointKeys.add(endpointKey);
      if (d.ownerId) owners.add(d.ownerId);
    };
    if (mode === 'name' && d.title.toLowerCase().includes(lower)) {
      hits.push(base);
      nodeHits(byNode, node.id);
      if (d.ownerId) owners.add(d.ownerId);
    }
    for (const ep of d.endpoints) {
      if (ep.path.toLowerCase().includes(lower) || (ep.operationId ?? '').toLowerCase().includes(lower)) {
        hits.push({ ...base, endpointKey: ep.endpointKey, method: ep.method, path: ep.path });
        mark(ep.endpointKey);
      }
      if (mode !== 'field') continue;
      const scan = (direction: Direction, fields: typeof ep.inputs) => {
        for (const f of fields) {
          if (f.normalizedName.includes(q) || normalizeName(f.path).includes(q)) {
            hits.push({ ...base, endpointKey: ep.endpointKey, method: ep.method, path: ep.path, fieldPath: f.path, direction });
            const h = nodeHits(byNode, node.id);
            h.fields.set(fieldHitKey(ep.endpointKey, f.path), direction);
            h.directions.add(direction);
            mark(ep.endpointKey);
          }
        }
      };
      scan('IN', ep.inputs);
      scan('OUT', ep.outputs);
    }
  }
  return { hits, byNode, owners };
}
