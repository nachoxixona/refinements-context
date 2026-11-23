// elkjs ships its layout engine as a classic (GWT-generated) worker script. Vite serves it as an asset and
// elk-api starts it as a classic Web Worker, so layout never blocks the canvas (research R11).
import workerUrl from 'elkjs/lib/elk-worker.min.js?url';

export const elkWorkerUrl: string = workerUrl;
