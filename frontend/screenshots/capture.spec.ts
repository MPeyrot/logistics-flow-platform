import { test, type Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { mockApi } from '../e2e/mock-api';

// Pas une vérification : ce « test » écrit les JPEG de public/screenshots/ (npm run screenshots).
const OUT = 'public/screenshots';
const ago = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString();
const ahead = (h: number) => new Date(Date.now() + h * 3_600_000).toISOString();
const ref = (n: string) => `LFP-261114-${n}`;

const SHIPMENTS = (
  [
    ['01', 'Paris', 'Lyon', 'IN_TRANSIT', 30, []],
    ['02', 'Lille', 'Marseille', 'DELIVERED', -20, []],
    ['03', 'Nantes', 'Bordeaux', 'OUT_FOR_DELIVERY', 4, []],
    ['04', 'Rennes', 'Toulouse', 'DELIVERY_FAILED', -6, ['LATE']],
    ['05', 'Strasbourg', 'Nice', 'IN_TRANSIT', -30, ['LATE', 'STALLED']],
    ['06', 'Paris', 'Brest', 'CREATED', 72, []],
    ['07', 'Lyon', 'Grenoble', 'IN_TRANSIT', 20, ['STALLED']],
    ['08', 'Dijon', 'Metz', 'DELIVERED', -70, []],
  ] as const
).map(([n, origin, destination, status, due, anomalies], i) => ({
  id: `s${i + 1}`,
  reference: ref(n),
  origin,
  destination,
  status,
  createdAt: ago(120 - i * 6),
  expectedDeliveryAt: ahead(due),
  openAnomalies: anomalies.map((type) => ({ type, detectedAt: ago(6) })),
}));

const SCANS = (
  [
    ['PICKED_UP', 'PAR', 100],
    ['ARRIVED_AT_SITE', 'LYO', 76],
    ['ARRIVED_AT_SITE', 'MRS', 52],
    ['OUT_FOR_DELIVERY', 'MRS', 30],
    ['DELIVERY_FAILED', 'MRS', 24, 'Destinataire absent'],
  ] as const
).map(([type, site, h, reason], i) => ({
  eventId: `e${i}`,
  type,
  site,
  occurredAt: ago(h),
  receivedAt: ago(h),
  ...(reason ? { reason } : {}),
}));

const letter = (partition: number, offset: number, key: string, cls: string, message: string, replayed: boolean) => ({
  partition,
  offset,
  timestamp: ago(3 + offset - 40),
  key,
  value: JSON.stringify({ eventId: `0000-${offset}`, shipmentReference: key, type: 'DELIVERED', site: 'PAR' }),
  exceptionClass: cls,
  exceptionMessage: message,
  replayed,
  replayedAt: replayed ? ago(1) : null,
});

const ANOMALIES = [
  { id: 'a1', type: 'LATE', shipmentReference: ref('04'), detectedAt: ago(1.5), resolvedAt: null },
  { id: 'a2', type: 'LATE', shipmentReference: ref('05'), detectedAt: ago(7), resolvedAt: null },
  { id: 'a3', type: 'STALLED', shipmentReference: ref('05'), detectedAt: ago(13), resolvedAt: null },
  { id: 'a4', type: 'STALLED', shipmentReference: ref('07'), detectedAt: ago(12), resolvedAt: null },
];

const USERS = (
  [
    ['admin', 'ADMIN', null, true],
    ['operateur.demo', 'OPERATOR', null, true],
    ['scanner.paris', 'SCANNER', 'PAR', true],
    ['scanner.lyon', 'SCANNER', 'LYO', true],
    ['ancien.operateur', 'OPERATOR', null, false],
  ] as const
).map(([username, role, site, active], i) => ({ id: `u${i + 1}`, username, role, site, active, createdAt: ago(900 - i * 100) }));

type Role = 'ADMIN' | 'OPERATOR' | 'SCANNER';

async function prepare(page: Page, role: Role, scheme: 'light' | 'dark') {
  await page.emulateMedia({ colorScheme: scheme });
  const sub = role === 'ADMIN' ? 'admin' : role === 'OPERATOR' ? 'operateur.demo' : 'scanner.paris';
  const claims = { sub, roles: [role], ...(role === 'SCANNER' ? { site: 'PAR' } : {}) };
  const token = `e30.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.sig`;
  await page.addInitScript(
    ([t, e]) => {
      localStorage.setItem('auth.token', t);
      localStorage.setItem('auth.expiresAt', e);
    },
    [token, ahead(1)],
  );
  await mockApi(page, '**/api/shipments', { body: SHIPMENTS });
  await mockApi(page, /\/api\/shipments\/[^/]+$/, {
    body: { ...SHIPMENTS[3], scans: SCANS, anomalies: [ANOMALIES[0]] },
  });
  await mockApi(page, /\/api\/anomalies/, { body: ANOMALIES });
  await mockApi(page, '**/api/users', { body: USERS });
  await mockApi(page, '**/api/admin/dlt/scan-events', {
    body: [
      letter(0, 40, ref('05'), 'org.example.InvalidScanException', 'Transition interdite : IN_TRANSIT -> DELIVERED', false),
      letter(1, 41, ref('99'), 'org.example.UnknownShipmentException', `Expédition inconnue : ${ref('99')}`, false),
      letter(2, 42, ref('02'), 'org.example.InvalidScanException', 'Transition interdite : DELIVERED -> PICKED_UP', true),
    ],
  });
  await mockApi(page, '**/api/delivery-notes/extract', {
    body: {
      costUsd: 0.0004,
      usage: { inputTokens: 1200, outputTokens: 85, cacheWriteTokens: 0, cacheReadTokens: 0 },
      shipment: SHIPMENTS[0],
      deliveryNote: { reference: ref('01'), deliveryDate: '2026-11-20', senderName: 'Atelier Dupont', recipientName: 'Maison Lambert', recipientAddress: '12 rue des Lilas, 69003 Lyon', packageCount: 3, totalWeightKg: 12.5 },
    },
  });
  await mockApi(page, '**/api/scans/imports', {
    body: { lines: 120, published: 117, rejected: 3, errors: [{ line: 14, detail: 'type inconnu : PICKUP' }, { line: 58, detail: 'occurredAt illisible' }, { line: 90, detail: 'site différent de celui du jeton' }] },
  });
}

interface Screen {
  name: string;
  role: Role;
  url: string;
  after?: (page: Page) => Promise<void>;
}

const SCREENS: Screen[] = [
  { name: 'tableau-de-bord', role: 'OPERATOR', url: '/dashboard' },
  { name: 'expeditions', role: 'OPERATOR', url: '/shipments' },
  { name: 'detail', role: 'OPERATOR', url: `/shipments/${ref('04')}` },
  { name: 'dlt', role: 'ADMIN', url: '/admin/dlt' },
  { name: 'comptes', role: 'ADMIN', url: '/admin/users' },
  { name: 'scan', role: 'SCANNER', url: '/scan' },
  {
    name: 'import-csv',
    role: 'SCANNER',
    url: '/scan/import',
    after: async (page) => {
      await page.locator('input[type=file]').setInputFiles({ name: 'scans-paris.csv', mimeType: 'text/csv', buffer: Buffer.from('shipmentReference,type,site,occurredAt\n') });
      await page.getByText('117').first().waitFor();
    },
  },
  {
    name: 'extraction',
    role: 'OPERATOR',
    url: '/delivery-notes',
    after: async (page) => {
      await page.getByLabel('Fichier du bon').setInputFiles({ name: 'bon-de-livraison.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
      await page.getByText('Atelier Dupont').first().waitFor();
    },
  },
];

mkdirSync(OUT, { recursive: true });

for (const scheme of ['light', 'dark'] as const) {
  for (const screen of SCREENS) {
    test(`${screen.name} (${scheme})`, async ({ page }) => {
      await prepare(page, screen.role, scheme);
      await page.goto(screen.url);
      await page.waitForLoadState('networkidle');
      await screen.after?.(page);
      await page.waitForTimeout(400);
      await page.screenshot({ path: `${OUT}/${screen.name}-${scheme}.jpg`, type: 'jpeg', quality: 72 });
    });
  }
}
