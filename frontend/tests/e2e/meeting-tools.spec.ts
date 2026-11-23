import { expect, test, type Page } from '@playwright/test';

// spec 004 quickstart: flow walkthrough, undo/redo, ownership and notes, end to end.

interface CanvasJson {
  nodes: { id: string; type: string; data: Record<string, unknown> }[];
  edges: { id: string; data: { name: string; connectionId: string; step?: number | null } }[];
  danglingConnections: unknown[];
  notes: { id: string; text: string; kind: string }[];
}

const canvasJson = async (page: Page) => (await (await page.request.get('/api/canvas')).json()) as CanvasJson;
const stepOf = async (page: Page, name: string) => (await canvasJson(page)).edges.find((e) => e.data.name === name)?.data.step;
const msExists = async (page: Page, name: string) => (await canvasJson(page)).nodes.some((n) => n.type === 'microservice' && n.data.name === name);

async function post<T>(page: Page, url: string, data: unknown): Promise<T> {
  const r = await page.request.post(url, { data });
  expect(r.ok(), await r.text()).toBeTruthy();
  return (await r.json()) as T;
}

test('walkthrough, undo/redo, ownership and notes', async ({ page }) => {
  // setup through the API: three microservices and a two-step flow with a parallel step
  const ms = async (name: string) => (await post<{ microservice: { id: string } }>(page, '/api/microservices', { name })).microservice.id;
  const cart = await ms('Cart');
  const pay = await ms('Pay');
  const ship = await ms('Ship');
  const flow = (await post<{ flow: { id: string } }>(page, '/api/flows', { name: 'Purchase', color: 'PURPLE' })).flow.id;
  const conn = (sourceId: string, targetId: string, name: string, step: number) =>
    post(page, '/api/connections', { sourceId, targetId, name, kind: 'REQUEST', flowId: flow, step });
  await conn(cart, pay, 'Charge', 1);
  await conn(pay, ship, 'Dispatch', 2);
  await conn(cart, ship, 'Notify', 2);
  const charge = (await canvasJson(page)).edges.find((e) => e.data.name === 'Charge')!;

  await page.goto('/');
  await expect(page.getByTestId(`connection-step-${charge.id}`)).toHaveText('1');

  // US1: walk through the flow with the keyboard
  await page.getByRole('button', { name: 'Walk through Purchase' }).click();
  await expect(page.getByTestId('walkthrough-position')).toHaveText('· step 1 of 2');
  await expect(page.getByTestId('walkthrough-bar')).toContainText('Charge');
  await page.keyboard.press('ArrowRight');
  await expect(page.getByTestId('walkthrough-position')).toHaveText('· step 2 of 2');
  await expect(page.getByTestId('walkthrough-bar')).toContainText('Dispatch');
  await expect(page.getByTestId('walkthrough-bar')).toContainText('2 in parallel');
  if (process.env.SHOTS) await page.screenshot({ path: process.env.SHOTS + '/walkthrough.png' });
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('walkthrough-bar')).toHaveCount(0);

  // US1: reorder in the Flows panel; US2: undo and redo the reorder
  await page.getByRole('button', { name: 'Edit flows' }).click();
  await page.getByRole('button', { name: 'Steps of Purchase' }).click();
  await page.getByRole('button', { name: 'Move step 2 of Purchase up' }).click();
  await expect.poll(() => stepOf(page, 'Charge')).toBe(2);
  expect(await stepOf(page, 'Dispatch')).toBe(1);
  await page.getByLabel('Close panel').click();
  await page.locator('.react-flow__pane').click({ position: { x: 5, y: 300 } });
  await page.keyboard.press('Control+z');
  await expect.poll(() => stepOf(page, 'Charge')).toBe(1);
  await page.keyboard.press('Control+Shift+z');
  await expect.poll(() => stepOf(page, 'Charge')).toBe(2);
  await page.getByRole('button', { name: /^Undo Reorder Purchase/ }).click();
  await expect.poll(() => stepOf(page, 'Charge')).toBe(1);

  // US2: undo a microservice deletion; its connections re-attach
  await page.getByTestId(`node-${ship}`).getByLabel('Actions for Ship').click();
  await page.getByRole('menu').getByRole('button', { name: 'Delete microservice' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
  await expect.poll(() => msExists(page, 'Ship')).toBe(false);
  await expect.poll(async () => (await canvasJson(page)).danglingConnections.length).toBeGreaterThanOrEqual(2);
  await page.getByRole('button', { name: 'Undo Delete Ship' }).click();
  await expect.poll(() => msExists(page, 'Ship')).toBe(true);
  await expect.poll(async () => (await canvasJson(page)).edges.filter((e) => ['Dispatch', 'Notify'].includes(e.data.name)).length).toBe(2);
  expect(await stepOf(page, 'Dispatch')).toBe(2);
  await page.getByRole('button', { name: 'Redo Delete Ship' }).click();
  await expect.poll(() => msExists(page, 'Ship')).toBe(false);
  await page.getByRole('button', { name: 'Undo Delete Ship' }).click();
  await expect.poll(() => msExists(page, 'Ship')).toBe(true);

  // US3: ownership on a microservice and the team filter
  await page.getByTestId(`node-${pay}`).getByLabel('Actions for Pay').click();
  await page.getByRole('menu').getByRole('button', { name: 'Edit details and ownership' }).click();
  await page.getByLabel('Microservice team').fill('Payments');
  await page.getByLabel('Repository URL').fill('https://git.example.com/pay');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await expect(page.getByTestId(`ownership-${pay}`)).toContainText('Payments');
  await expect(page.getByTestId(`ownership-${pay}`).getByRole('link', { name: 'repo ↗' })).toHaveAttribute('href', 'https://git.example.com/pay');
  await page.getByLabel('Team filter').selectOption({ label: 'Payments' });
  await expect(page.getByTestId('highlight-summary')).toContainText('1 microservice in this team');
  await expect(page.getByTestId(`node-${cart}`)).toHaveClass(/hl-dimmed/);
  await page.getByLabel('Team filter').selectOption({ label: 'all teams' });

  // US4: a question note survives a reload
  await page.getByRole('button', { name: '+ Note' }).click();
  await page.getByTestId('note-new').getByLabel('Note kind').selectOption('QUESTION');
  await page.getByLabel('Note text').fill('Who owns refunds?');
  await page.getByLabel('Note text').press('Control+Enter');
  await expect.poll(async () => (await canvasJson(page)).notes.map((n) => `${n.kind}:${n.text}`)).toContain('QUESTION:Who owns refunds?');
  await page.reload();
  await expect(page.locator('.note-node.note-question')).toContainText('Who owns refunds?');
  if (process.env.SHOTS) await page.screenshot({ path: process.env.SHOTS + '/notes-ownership.png' });
});
