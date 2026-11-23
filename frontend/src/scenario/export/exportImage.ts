import { getNodesBounds, getViewportForBounds, type ReactFlowInstance } from '@xyflow/react';
import { toPng } from 'html-to-image';
import { downloadBlob, safeFileName } from './download';

const nextFrame = () => new Promise<void>((r) => requestAnimationFrame(() => r()));

/**
 * PNG export of the current canvas, baseline or scenario (FR-036, research R15): the viewport is framed
 * on all nodes with getNodesBounds/getViewportForBounds, captured at 2× pixel ratio with the legend and
 * draft banner, then restored. Controls and minimap are excluded.
 */
export async function exportCanvasImage(
  rf: Pick<ReactFlowInstance, 'getNodes' | 'getViewport' | 'setViewport'>,
  frame: HTMLElement,
  name: string,
): Promise<void> {
  const nodes = rf.getNodes();
  const previous = rf.getViewport();
  const { width, height } = frame.getBoundingClientRect();
  if (nodes.length > 0) {
    const bounds = getNodesBounds(nodes);
    const viewport = getViewportForBounds(bounds, width, height, 0.1, 2, 0.1);
    await rf.setViewport(viewport);
    await nextFrame();
    await nextFrame();
  }
  try {
    const dataUrl = await toPng(frame, {
      pixelRatio: 2,
      backgroundColor: '#ffffff',
      filter: (node) => !(node instanceof HTMLElement && node.classList.contains('no-export')),
    });
    const blob = await (await fetch(dataUrl)).blob();
    downloadBlob(blob, `${safeFileName(name)}.png`);
  } finally {
    await rf.setViewport(previous);
  }
}
