import type { HighlightInput } from './highlight/highlightState';

/** Summary chip: upstream and downstream counts for the blast radius, or the number of search hits. */
export function HighlightSummary({ highlight }: { highlight: HighlightInput }) {
  if (highlight.mode === 'blast') {
    return (
      <div className="summary-chip" data-testid="highlight-summary">
        <span className="chip-up">↑ {highlight.blast.upstream.size} upstream</span>
        <span className="chip-down">↓ {highlight.blast.downstream.size} downstream</span>
        <span className="muted">Esc to clear</span>
      </div>
    );
  }
  if (highlight.mode === 'search') {
    return (
      <div className="summary-chip" data-testid="highlight-summary">
        {highlight.search.hits.length} match{highlight.search.hits.length === 1 ? '' : 'es'} in {highlight.search.byNode.size} node
        {highlight.search.byNode.size === 1 ? '' : 's'}
      </div>
    );
  }
  if (highlight.mode === 'flow') {
    return (
      <div className="summary-chip" data-testid="highlight-summary">
        {highlight.edgeIds.size} connection{highlight.edgeIds.size === 1 ? '' : 's'} in this flow
        <span className="muted">Esc to clear</span>
      </div>
    );
  }
  if (highlight.mode === 'team') {
    return (
      <div className="summary-chip" data-testid="highlight-summary">
        {highlight.nodeIds.size} microservice{highlight.nodeIds.size === 1 ? '' : 's'} in this team
        <span className="muted">Esc to clear</span>
      </div>
    );
  }
  return null;
}
