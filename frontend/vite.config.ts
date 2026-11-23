import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // VITE_API_TARGET lets the Playwright run use its own isolated backend
      '/api': process.env.VITE_API_TARGET ?? 'http://localhost:8080',
    },
  },
  // elkjs runs in a module web worker (src/canvas/layout/elk.worker.ts, research R11)
  worker: {
    format: 'es',
  },
});
