import type { HttpMethod } from '../../api/types';

/** Research R13: each method has a color AND a text label, so color is never the only cue. */
export function MethodBadge({ method }: { method: HttpMethod }) {
  return (
    <span className={`method-badge method-${method.toLowerCase()}`} aria-label={`HTTP ${method}`}>
      {method}
    </span>
  );
}
