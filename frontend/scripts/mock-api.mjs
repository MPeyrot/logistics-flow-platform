// Fausse API pour voir le design en local : aucune base, aucun service, rien n'est conservé (tout vit en mémoire).
// Usage : `npm run mock` dans un terminal, `npm start` dans un autre. Connexion : admin, operator ou scanner (mot de passe libre).
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

const PORTS = [8080, 8081]; // ceux de proxy.conf.json
const ago = (h) => new Date(Date.now() - h * 3_600_000).toISOString();
const ahead = (h) => new Date(Date.now() + h * 3_600_000).toISOString();

const shipments = [
  ['FR-24001', 'Paris', 'Lyon', 'IN_TRANSIT', 30, [], 'ARRIVED_AT_SITE'],
  ['FR-24002', 'Lille', 'Marseille', 'DELIVERED', -20, [], 'DELIVERED'],
  ['FR-24003', 'Nantes', 'Bordeaux', 'OUT_FOR_DELIVERY', 4, [], 'OUT_FOR_DELIVERY'],
  ['FR-24004', 'Rennes', 'Toulouse', 'DELIVERY_FAILED', -6, [['LATE', 5]], 'DELIVERY_FAILED'],
  ['FR-24005', 'Strasbourg', 'Nice', 'IN_TRANSIT', -30, [['LATE', 28], ['STALLED', 52]], 'ARRIVED_AT_SITE'],
  ['FR-24006', 'Paris', 'Brest', 'CREATED', 72, [], null],
  ['FR-24007', 'Lyon', 'Grenoble', 'IN_TRANSIT', 20, [['STALLED', 50]], 'PICKED_UP'],
  ['FR-24008', 'Dijon', 'Metz', 'DELIVERED', -70, [], 'DELIVERED'],
].map(([reference, origin, destination, status, due, anomalies, last], i) => ({
  id: `s${i + 1}`,
  reference,
  origin,
  destination,
  status,
  createdAt: ago(120 - i * 6),
  expectedDeliveryAt: ahead(due),
  openAnomalies: anomalies.map(([type, h]) => ({ type, detectedAt: ago(h / 4) })),
  _last: last,
}));

const scansOf = (s) => {
  const flow = ['PICKED_UP', 'ARRIVED_AT_SITE', 'ARRIVED_AT_SITE', 'OUT_FOR_DELIVERY', s._last];
  const end = s._last === 'PICKED_UP' ? 1 : s._last === 'ARRIVED_AT_SITE' ? 3 : s._last === 'OUT_FOR_DELIVERY' ? 4 : 5;
  if (!s._last) return [];
  return flow.slice(0, end === 5 ? 5 : end).map((type, i) => ({
    eventId: `${s.id}-${i}`,
    type: end === 5 || i < end - 1 || i === 0 ? type : s._last,
    site: ['PAR', 'LYO', 'MRS', 'BOR'][i % 4],
    occurredAt: ago(100 - i * 12),
    receivedAt: ago(100 - i * 12 - 0.01),
    ...(type === 'DELIVERY_FAILED' ? { reason: 'Destinataire absent' } : {}),
  }));
};

const users = [
  ['admin', 'ADMIN', null, true],
  ['operator', 'OPERATOR', null, true],
  ['scanner', 'SCANNER', 'PAR', true],
  ['lea.martin', 'SCANNER', 'LYO', true],
  ['paul.durand', 'OPERATOR', null, false],
].map(([username, role, site, active], i) => ({ id: `u${i + 1}`, username, role, site, active, createdAt: ago(900 - i * 100) }));

const letters = [
  ['FR-24005', 'org.example.InvalidScanException', 'Transition interdite : IN_TRANSIT -> DELIVERED', false],
  ['FR-24099', 'org.example.UnknownShipmentException', 'Expédition inconnue : FR-24099', false],
  ['FR-24002', 'org.example.InvalidScanException', 'Transition interdite : DELIVERED -> PICKED_UP', true],
].map(([key, exceptionClass, exceptionMessage, replayed], i) => ({
  partition: i % 3,
  offset: 40 + i,
  timestamp: ago(3 + i * 5),
  key,
  value: JSON.stringify({ eventId: randomUUID(), shipmentReference: key, type: 'DELIVERED', site: 'PAR', occurredAt: ago(3 + i * 5) }),
  exceptionClass,
  exceptionMessage,
  replayed,
  replayedAt: replayed ? ago(1) : null,
}));

let replay = null;

const anomalies = () =>
  shipments.flatMap((s) => s.openAnomalies.map((a, i) => ({ id: `${s.id}-a${i}`, type: a.type, shipmentReference: s.reference, detectedAt: a.detectedAt, resolvedAt: null }))).concat([
    { id: 'old-1', type: 'LATE', shipmentReference: 'FR-24002', detectedAt: ago(60), resolvedAt: ago(20) },
    { id: 'old-2', type: 'STALLED', shipmentReference: 'FR-24008', detectedAt: ago(90), resolvedAt: ago(70) },
  ]);

const strip = ({ _last, ...s }) => s;
const jwt = (claims) => `e30.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.mock`;

function handle(method, url, body) {
  const path = url.pathname;
  if (method === 'POST' && path === '/api/auth/token') {
    const name = String(body?.username ?? '').toLowerCase();
    const role = name.startsWith('scan') ? 'SCANNER' : name.startsWith('op') ? 'OPERATOR' : 'ADMIN';
    const hours = role === 'SCANNER' ? 12 : 1;
    return [200, { accessToken: jwt({ sub: body?.username || 'admin', roles: [role], ...(role === 'SCANNER' ? { site: 'PAR' } : {}) }), tokenType: 'Bearer', expiresAt: ahead(hours) }];
  }
  if (path === '/api/shipments' && method === 'GET') return [200, shipments.map(strip)];
  if (path === '/api/shipments' && method === 'POST') {
    const s = { id: `s${shipments.length + 1}`, ...body, status: 'CREATED', createdAt: new Date().toISOString(), openAnomalies: [], _last: null };
    shipments.unshift(s);
    return [201, strip(s)];
  }
  const one = path.match(/^\/api\/shipments\/([^/]+)$/);
  if (one && method === 'GET') {
    const s = shipments.find((x) => x.reference === decodeURIComponent(one[1]));
    if (!s) return [404, { title: 'Introuvable', detail: 'Expédition inconnue' }];
    return [200, { ...strip(s), scans: scansOf(s), anomalies: anomalies().filter((a) => a.shipmentReference === s.reference) }];
  }
  if (path === '/api/anomalies') return [200, anomalies().filter((a) => (url.searchParams.get('open') === 'true') === (a.resolvedAt === null))];
  if (path === '/api/users' && method === 'GET') return [200, users];
  if (path === '/api/users' && method === 'POST') {
    const u = { id: `u${users.length + 1}`, username: body.username, role: body.role, site: body.site ?? null, active: true, createdAt: new Date().toISOString() };
    users.push(u);
    return [201, u];
  }
  const user = path.match(/^\/api\/users\/([^/]+)$/);
  if (user && method === 'PATCH') {
    const u = users.find((x) => x.id === decodeURIComponent(user[1]));
    if (u) u.active = body.active;
    return [200, u];
  }
  if (path === '/api/admin/dlt/scan-events' && method === 'GET') return [200, letters];
  if (path === '/api/admin/dlt/scan-events/replay' && method === 'POST') {
    replay = { id: randomUUID(), polls: 0 };
    return [202, { id: replay.id, finished: false, replayed: 0, published: 0, failed: 0, error: null }];
  }
  if (path.startsWith('/api/admin/dlt/scan-events/replay/') && method === 'GET') {
    const finished = ++replay.polls >= 4;
    return [200, { id: replay.id, finished, replayed: finished ? 2 : replay.polls, published: finished ? 2 : 0, failed: 0, error: null }];
  }
  const one2 = path.match(/^\/api\/admin\/dlt\/scan-events\/(\d+)\/(\d+)\/replay$/);
  if (one2 && method === 'POST') {
    const l = letters.find((x) => x.partition === +one2[1] && x.offset === +one2[2]);
    if (l) Object.assign(l, { replayed: true, replayedAt: new Date().toISOString() });
    return [200, l];
  }
  if (path === '/api/delivery-notes/extract') {
    return [200, { costUsd: 0.0004, shipment: strip(shipments[0]), deliveryNote: { reference: 'FR-24001', deliveryDate: '2026-11-20', senderName: 'Atelier Dupont', recipientName: 'Maison Lambert', recipientAddress: '12 rue des Lilas, 69003 Lyon', packageCount: 3, totalWeightKg: 12.5 } }];
  }
  if (path === '/api/scans/imports') return [200, { lines: 120, published: 117, rejected: 3, errors: [{ line: 14, detail: 'type inconnu : PICKUP' }, { line: 58, detail: 'occurredAt illisible' }, { line: 90, detail: 'site différent de celui du jeton' }] }];
  if (path === '/api/scans') return [202, { eventId: randomUUID() }];
  return [404, { title: 'Route inconnue de la fausse API', detail: `${method} ${path}` }];
}

const server = createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const raw = Buffer.concat(chunks).toString();
    let body = null;
    try {
      body = raw && (req.headers['content-type'] ?? '').includes('json') ? JSON.parse(raw) : null;
    } catch {
      /* corps non JSON (fichier) : ignoré */
    }
    const [status, json] = handle(req.method, new URL(req.url, 'http://x'), body);
    setTimeout(() => {
      res.writeHead(status, { 'content-type': status >= 400 ? 'application/problem+json' : 'application/json' });
      res.end(JSON.stringify(json));
    }, 150);
  });
});

for (const port of PORTS) {
  const s = server === undefined ? null : createServer(server.listeners('request')[0]);
  s.on('error', (e) => {
    console.error(`Port ${port} indisponible (${e.code}) : un vrai service tourne-t-il déjà ?`);
    process.exit(1);
  });
  s.listen(port, '127.0.0.1', () => console.log(`Fausse API sur http://127.0.0.1:${port}`));
}
console.log('Connexion : admin, operator ou scanner (mot de passe libre). Rien n’est conservé.');
