import { memo, useRef, useState } from 'react';
import { Handle, Position, useStore, type NodeProps } from '@xyflow/react';
import { useCanvasStore } from '../canvasStore';
import { zoomTier } from '../zoomTier';
import { normalizeLabel } from '../normalize';
import { MicroserviceActions } from '../../microservices/MicroserviceActions';
import { ProposedMicroserviceActions } from '../../scenario/ProposedMicroserviceActions';
import type { MicroserviceConnectionNode } from './nodeTypes';

const tierSelector = (s: { transform: [number, number, number] }) => zoomTier(s.transform[2]);

/**
 * A microservice: a named node with or without a spec (FR-010), the end point of connections. A linked spec card is
 * docked below it, or shown as an "API: {title}" badge when collapsed (FR-014).
 */
function MicroserviceNodeImpl({ data }: NodeProps<MicroserviceConnectionNode>) {
  const tier = useStore(tierSelector);
  const toggleCard = useCanvasStore((s) => s.actions.toggleCardCollapsed);
  const setTeamFilter = useCanvasStore((s) => s.setTeamFilter);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const classes = ['microservice-node', `hl-${data.highlight}`, `tier-${tier}`];
  if (data.proposed) classes.push('microservice-proposed');
  if (data.removed) classes.push('microservice-removed');
  if (data.cardNodeId) classes.push('has-docked-card');
  const cardId = data.specCardId;
  const canCollapse = !data.draftMode && cardId !== null && !data.proposed;

  return (
    <div className={classes.join(' ')} data-testid={`node-${data.ref}`} title={data.description ?? undefined}>
      <Handle type="target" position={Position.Left} id="in" className="handle ms-handle" title="Drop a connection here" />
      <div className="node-header">
        {data.proposed && <span className="proposed-tag">PROPOSED</span>}
        {data.removed && <span className="removed-tag">PROPOSED REMOVAL</span>}
        <span className="node-title">{data.name}</span>
        {(!data.proposed || data.draftMode) && (
          <button
            type="button"
            ref={menuButton}
            className="node-menu-button nodrag"
            aria-label={`Actions for ${data.name}`}
            onClick={(e) => {
              e.stopPropagation();
              setMenuOpen((o) => !o);
            }}
          >
            ⋯
          </button>
        )}
      </div>
      {tier !== 'low' && data.description && <div className="ms-description">{data.description}</div>}
      {(data.team || data.repositoryUrl || data.docsUrl) && (
        <div className="ms-ownership" data-testid={`ownership-${data.ref}`}>
          {data.team && (
            <button
              type="button"
              className="team-chip nodrag"
              title={`Highlight team ${data.team}`}
              onClick={(e) => {
                e.stopPropagation();
                setTeamFilter(normalizeLabel(data.team!));
              }}
            >
              {data.team}
            </button>
          )}
          {tier !== 'low' && data.repositoryUrl && (
            <a className="ms-link nodrag" href={data.repositoryUrl} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
              repo ↗
            </a>
          )}
          {tier !== 'low' && data.docsUrl && (
            <a className="ms-link nodrag" href={data.docsUrl} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
              docs ↗
            </a>
          )}
        </div>
      )}
      {cardId && data.cardCollapsed ? (
        <button
          type="button"
          className="api-badge nodrag"
          disabled={!canCollapse}
          aria-label={`Expand spec ${data.cardTitle ?? ''}`}
          onClick={(e) => {
            e.stopPropagation();
            toggleCard(cardId);
          }}
        >
          API: {data.cardTitle}
        </button>
      ) : data.cardNodeId ? (
        canCollapse && (
          <button
            type="button"
            className="api-collapse nodrag"
            aria-label={`Collapse spec ${data.cardTitle ?? ''}`}
            onClick={(e) => {
              e.stopPropagation();
              toggleCard(cardId);
            }}
          >
            ▴ {data.linkRemoved ? 'spec (link proposed for removal)' : 'spec'}
          </button>
        )
      ) : (
        tier !== 'low' && <div className="ms-nospec">no spec</div>
      )}
      {menuOpen && data.proposed && (
        <ProposedMicroserviceActions
          tempKey={data.ref}
          name={data.name}
          description={data.description}
          team={data.team ?? null}
          anchor={menuButton.current}
          onClose={() => setMenuOpen(false)}
        />
      )}
      {menuOpen && data.microserviceId && (
        <MicroserviceActions
          microserviceId={data.microserviceId}
          name={data.name}
          description={data.description}
          ownership={{ team: data.team ?? null, repositoryUrl: data.repositoryUrl ?? null, docsUrl: data.docsUrl ?? null }}
          hasCard={data.specCardId !== null}
          connectionCount={data.connectionCount}
          draftMode={data.draftMode}
          removed={Boolean(data.removed)}
          linkRemoved={Boolean(data.linkRemoved)}
          anchor={menuButton.current}
          onClose={() => setMenuOpen(false)}
        />
      )}
      <Handle type="source" position={Position.Right} id="out" className="handle ms-handle" title={`Drag to draw a connection from ${data.name}`} />
    </div>
  );
}

export const MicroserviceNode = memo(MicroserviceNodeImpl);
