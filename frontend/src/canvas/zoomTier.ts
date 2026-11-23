export type ZoomTier = 'low' | 'mid' | 'high';

/** Research R12: < 0.45 name + count; 0.45–0.9 endpoint rows; ≥ 0.9 expandable fields. */
export function zoomTier(zoom: number): ZoomTier {
  if (zoom < 0.45) return 'low';
  if (zoom < 0.9) return 'mid';
  return 'high';
}

export const MAX_ROWS_COLLAPSED = 25;
