import { execSync } from 'node:child_process';

/** Removes the throwaway e2e database: stopping `docker run` does not stop the container itself. */
export default function teardown() {
  execSync('docker rm -f viz-e2e-db', { stdio: 'ignore' });
}
