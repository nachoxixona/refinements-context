import { defineConfig, devices } from '@playwright/test';

/**
 * The smoke test starts its own isolated stack, so it never touches your dev data: a throwaway PostgreSQL
 * container on :55433 (removed when the run ends), a backend on :8081, and a Vite dev server on :5174
 * proxying /api to it. Needs Docker.
 */
const DB = 'viz-e2e-db';
const DB_URL = 'jdbc:postgresql://localhost:55433/e2e';
const waitForDb = `until docker exec ${DB} pg_isready -q -h 127.0.0.1 -U e2e; do sleep 1; done`;

export default defineConfig({
  testDir: './tests/e2e',
  globalTeardown: './tests/e2e/teardown.ts',
  timeout: 90_000,
  workers: 1,
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
    viewport: { width: 1600, height: 950 },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1600, height: 950 } } }],
  webServer: [
    {
      command: `docker rm -f ${DB} >/dev/null 2>&1; docker run --rm --name ${DB} -p 55433:5432 --tmpfs /var/lib/postgresql/data -e POSTGRES_DB=e2e -e POSTGRES_USER=e2e -e POSTGRES_PASSWORD=e2e postgres:16-alpine`,
      port: 55433,
      timeout: 60_000,
      reuseExistingServer: false,
    },
    {
      command: `${waitForDb} && ./mvnw -q spring-boot:run -Dspotless.check.skip=true -Dspring-boot.run.arguments="--server.port=8081 --spring.datasource.url=${DB_URL} --spring.datasource.username=e2e --spring.datasource.password=e2e"`,
      cwd: '../backend',
      url: 'http://localhost:8081/actuator/health',
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      command: 'npx vite --port 5174 --strictPort',
      url: 'http://localhost:5174',
      env: { VITE_API_TARGET: 'http://localhost:8081' },
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
});
