import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';

// The declared-connections happy path (tasks.md T083): import a card → create a microservice → link → upload a connections
// document → draw a connection → export → re-import ("No changes") → draft a removal → leave draft mode with the
// baseline unchanged.

const RES = resolve(import.meta.dirname, '../../../backend/src/test/resources');
const spec = (f: string) => readFileSync(resolve(RES, 'specs/reference-workspace', f), 'utf8');
const CONNECTIONS = resolve(RES, 'connections/shop.json');

interface CanvasJson {
  nodes: { id: string; type: string; parentId?: string | null; data: Record<string, unknown> }[];
  edges: { id: string; source: string; target: string; data: { name: string; connectionId: string } }[];
}

async function canvasJson(page: Page): Promise<CanvasJson> {
  return (await (await page.request.get('/api/canvas')).json()) as CanvasJson;
}

async function msId(page: Page, name: string): Promise<string> {
  let id: string | undefined;
  await expect
    .poll(async () => {
      const c = await canvasJson(page);
      id = c.nodes.find((x) => x.type === 'microservice' && x.data.name === name)?.data.microserviceId as string | undefined;
      return id;
    })
    .toBeTruthy();
  return id!;
}

test.describe.configure({ mode: 'serial' });

test('cards, microservices, links and declared connections end to end', async ({ page }) => {
  await page.goto('/');

  // US1: import a spec as an unlinked card; no connections appear
  await page.getByRole('button', { name: 'Import spec' }).click();
  await page.getByLabel('Spec content').fill(spec('order-service.yaml'));
  await page.getByRole('dialog').getByRole('button', { name: 'Import' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const cards = (await (await page.request.get('/api/cards')).json()) as { id: string; title: string }[];
  const cardId = cards.find((c) => c.title === 'Order Service')!.id;
  await expect(page.getByTestId(`node-card:${cardId}`)).toBeVisible();
  expect((await canvasJson(page)).edges).toHaveLength(0);

  // US2: create a microservice and link the card to it from the card menu
  await page.getByRole('button', { name: 'New microservice' }).click();
  await page.getByLabel('Microservice name').fill('Orders');
  await page.getByRole('dialog').getByRole('button', { name: 'Create' }).click();
  const orders = await msId(page, 'Orders');
  await expect(page.getByTestId(`node-${orders}`)).toBeVisible();
  await page.getByTestId(`node-card:${cardId}`).getByLabel('Actions for Order Service').click();
  await page.getByRole('menu').getByRole('button', { name: 'Link to…' }).click();
  await page.getByLabel('Microservice to link to').selectOption({ label: 'Orders' });
  await expect.poll(async () => (await canvasJson(page)).nodes.find((n) => n.id === `card:${cardId}`)?.parentId).toBe(`ms:${orders}`);

  // regression: the menu of a microservice with a docked card is not covered by the card
  await page.getByTestId(`node-${orders}`).getByLabel('Actions for Orders').click();
  await page.getByRole('menu').getByRole('button', { name: 'Close' }).click();
  await expect(page.getByRole('menu')).toHaveCount(0);

  // US3: upload a connections document → preview → apply
  await page.getByLabel('Import connections document').setInputFiles(CONNECTIONS);
  await expect(page.getByRole('dialog')).toContainText('Connections added (7)');
  await page.getByRole('dialog').getByRole('button', { name: 'Apply', exact: true }).click();
  await expect.poll(async () => (await canvasJson(page)).edges.length).toBe(7);
  const before = (await canvasJson(page)).edges.length;

  // draw a connection from Payments to Orders
  const payments = await msId(page, 'Payments');
  await page.getByRole('button', { name: 'Auto-arrange' }).click();
  await page.getByRole('button', { name: 'Keep layout' }).click();
  await page.getByRole('button', { name: 'Fit View' }).click();
  const source = page.getByTestId(`node-${payments}`).locator('.react-flow__handle.source');
  const target = page.getByTestId(`node-${orders}`).locator('.node-title');
  // wait until Keep layout and Fit View have settled, so the drag starts and ends on the right elements
  let last = '';
  await expect
    .poll(async () => {
      const now = JSON.stringify([await source.boundingBox(), await target.boundingBox()]);
      const stable = now === last;
      last = now;
      return stable;
    }, { intervals: [250] })
    .toBe(true);
  const from = (await source.boundingBox())!;
  const to = (await target.boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 20 });
  await page.mouse.up();
  await page.getByLabel('Connection name').fill('Refund issued');
  await page.getByLabel('Connection kind').selectOption('EVENT');
  // FR-054: pick an endpoint of the target's spec (Orders has the Order Service card)
  await page.getByLabel('Connection endpoint').selectOption('POST /orders');
  await page.getByRole('dialog').getByRole('button', { name: 'Create' }).click();
  await expect.poll(async () => (await canvasJson(page)).edges.length).toBe(before + 1);
  const drawn = (await canvasJson(page)).edges.find((e) => e.data.name === 'Refund issued')!;
  // the endpoint is shown on the diagram as its own badge (method + path) near the arrowhead
  await expect(page.getByTestId(`connection-endpoint-${drawn.id}`)).toContainText('POST');
  await expect(page.getByTestId(`connection-endpoint-${drawn.id}`)).toContainText('/orders');

  // flows (FR-049 – FR-051): create one from the legend, assign the new connection, highlight it
  await page.getByRole('button', { name: '+ Flows' }).click();
  await page.getByLabel('New flow name').fill('Refunds');
  await page.getByLabel('New flow color').selectOption('ORANGE');
  await page.getByRole('button', { name: 'Add flow' }).click();
  await expect(page.getByLabel('Name of flow Refunds')).toBeVisible();
  const refundConnection = (await canvasJson(page)).edges.find((e) => e.data.name === 'Refund issued')!;
  await page.getByTestId(`connection-label-${refundConnection.id}`).click();
  await page.getByRole('complementary').getByLabel('Connection flow').selectOption({ label: 'Refunds' });
  await page.getByRole('complementary').getByRole('button', { name: 'Save' }).click();
  await expect
    .poll(async () => {
      const c = (await canvasJson(page)) as CanvasJson & { flows: { id: string; name: string }[] };
      const g = c.edges.find((e) => e.id === refundConnection.id)?.data as { flowId?: string | null } | undefined;
      return c.flows.find((x) => x.id === g?.flowId)?.name ?? null;
    })
    .toBe('Refunds');
  await page.getByRole('button', { name: 'Refunds', exact: true }).click();
  await expect(page.getByTestId('highlight-summary')).toContainText('1 connection in this flow');
  await page.keyboard.press('Escape');

  // export and re-import: no changes (SC-003)
  await page.getByRole('button', { name: 'Export ▾' }).click();
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('menuitem', { name: 'Connections document' }).click(),
  ]);
  const exported = await download.path();
  expect(readFileSync(exported, 'utf8')).toContain('"Refund issued"');
  await page.getByLabel('Import connections document').setInputFiles(exported);
  await expect(page.getByRole('dialog')).toContainText('No changes');
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).last().click();

  // US5: draft a removal, then leave draft mode with the baseline unchanged
  const snapshot = async () => {
    const c = (await canvasJson(page)) as CanvasJson & { layoutVersion?: number };
    delete c.layoutVersion;
    return JSON.stringify(c);
  };
  const baseline = await snapshot();
  await page.getByRole('button', { name: 'Draft Scenario Mode' }).click();
  // regression: a proposed microservice can be edited after creating it
  await page.getByRole('button', { name: 'Proposals' }).click();
  await page.getByLabel('Proposed microservice name').fill('Loyalty');
  await page.getByRole('button', { name: 'Add proposed microservice' }).click();
  await page.getByLabel('Edit proposed Loyalty').click();
  await page.getByRole('dialog').getByLabel('Proposed microservice description').fill('Points and tiers');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await expect(page.getByTestId('node-proposed:loyalty')).toContainText('Points and tiers');
  await page.getByTestId('node-proposed:loyalty').getByLabel('Actions for Loyalty').click();
  await expect(page.getByRole('menu')).toContainText('Edit name or description');
  await page.getByRole('menu').getByRole('button', { name: 'Close' }).click();

  // regression: a proposed connection in a flow keeps the flow's color (green halo marks it as proposed)
  await page.getByRole('button', { name: 'Fit View' }).click();
  await page.waitForTimeout(500);
  const pFrom = (await page.getByTestId(`node-${orders}`).locator('.react-flow__handle.source').boundingBox())!;
  const pTo = (await page.getByTestId('node-proposed:loyalty').locator('.node-title').boundingBox())!;
  await page.mouse.move(pFrom.x + pFrom.width / 2, pFrom.y + pFrom.height / 2);
  await page.mouse.down();
  await page.mouse.move(pTo.x + pTo.width / 2, pTo.y + pTo.height / 2, { steps: 20 });
  await page.mouse.up();
  await page.getByLabel('Connection name').fill('Points earned');
  await page.getByLabel('Connection flow').selectOption({ label: 'Refunds' });
  await page.getByRole('dialog').getByRole('button', { name: 'Propose' }).click();
  const proposedPath = page.locator('[data-testid^="rf__edge-proposed:"] path.react-flow__edge-path');
  await expect(proposedPath).toHaveCount(1);
  expect(await proposedPath.evaluate((el) => (el as SVGPathElement).style.stroke)).toBe('rgb(234, 88, 12)');
  await expect(page.locator('[data-testid^="proposal-halo-"]')).toHaveCount(1);

  const refund = (await canvasJson(page)).edges.find((e) => e.data.name === 'Refund issued')!;
  await page.getByTestId(`connection-label-${refund.id}`).click();
  await page.getByRole('complementary').getByRole('button', { name: 'Propose removal' }).click();
  await expect(page.getByRole('complementary')).toContainText('proposed removal');
  await page.getByRole('button', { name: 'Exit draft' }).click();
  await page.getByRole('button', { name: 'Discard and leave' }).click();
  expect(await snapshot()).toBe(baseline);
});
