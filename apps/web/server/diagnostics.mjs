import { createHmac, timingSafeEqual } from 'node:crypto';
import { isIP } from 'node:net';
import { dashboard } from './diagnostics-dashboard.mjs';

const MAX_BYTES = 1024;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const HEX = /^[0-9a-f]{64}$/;
const HEADERS = {
  'Cache-Control': 'private, no-store, max-age=0',
  'CDN-Cache-Control': 'no-store',
  'Vercel-CDN-Cache-Control': 'no-store',
  'X-Robots-Tag': 'noindex, nofollow, noai, noimageai',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
};

export function configFromEnv(env) {
  if (env.KANTA_DIAGNOSTICS_ENABLE !== 'true' || env.VERCEL !== '1' || env.VERCEL_ENV !== 'production') return null;
  const { KANTA_DIAGNOSTICS_DB_URL: dbUrl, KANTA_DIAGNOSTICS_HMAC_KEY: key,
    KANTA_DIAGNOSTICS_OWNER_SECRET: ownerSecret, KANTA_DIAGNOSTICS_ORIGIN: origin } = env;
  if (!HEX.test(key ?? '') || !HEX.test(ownerSecret ?? '') || key === ownerSecret || !dbUrl || !origin) return null;
  try {
    const db = new URL(dbUrl);
    const site = new URL(origin);
    if (db.protocol !== 'postgresql:' || !db.hostname || !db.username || !db.password ||
        db.pathname.length < 2 || db.search || db.hash || site.protocol !== 'https:' || site.origin !== origin) return null;
    return { dbUrl, key, ownerSecret, origin };
  } catch {
    return null;
  }
}

function reply(status, headers = {}, body = null) {
  return new Response(body, { status, headers: { ...HEADERS, ...headers } });
}

function exactKeys(value, keys) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) &&
    Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}

function uuid(value) {
  return typeof value === 'string' && UUID.test(value);
}

function validate(text, remove) {
  const record = JSON.parse(text);
  const keyCount = [...text.matchAll(/"(?:[^"\\]|\\[\s\S])*"|:/g)].filter(m => m[0] === ':').length;
  // The grammar has no other object levels. JSON.parse drops duplicate keys;
  // count the structural colons outside strings to detect any it dropped.
  if (remove) return keyCount === 1 && exactKeys(record, ['installationId']) && uuid(record.installationId) ? record : null;
  if (!exactKeys(record, ['installationId', 'active', 'events']) || !uuid(record.installationId) ||
      typeof record.active !== 'boolean' || !Array.isArray(record.events) || record.events.length > 1 ||
      (!record.active && record.events.length === 0) || keyCount !== 3 + 2 * record.events.length) return null;
  if (record.events.some(e => !exactKeys(e, ['type', 'id']) || e.type !== 'crash' || !uuid(e.id))) return null;
  return record;
}

async function readRecord(request, remove) {
  const headers = request.headers;
  if (headers.has('content-encoding') || !/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(headers.get('content-type') ?? '')) return [415];
  const length = headers.get('content-length');
  if (length !== null && !/^\d+$/.test(length)) return [400];
  if (length !== null && Number(length) > MAX_BYTES) return [413];
  if (!request.body) return [400];
  const reader = request.body.getReader();
  try {
    const buffer = new Uint8Array(MAX_BYTES);
    let size = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (size + value.length > MAX_BYTES) {
        void reader.cancel().catch(() => {});
        return [413];
      }
      buffer.set(value, size);
      size += value.length;
    }
    if (length !== null && Number(length) !== size) return [400];
    const record = validate(new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(buffer.subarray(0, size)), remove);
    return record ? [200, record] : [400];
  } catch {
    return [400];
  } finally {
    reader.releaseLock();
  }
}

function digest(key, purpose, data) {
  return createHmac('sha256', Buffer.from(key, 'hex')).update(purpose + '\0' + data).digest('hex');
}

function isOwner(request, secret) {
  const presented = /^Basic ([A-Za-z0-9+/]+={0,2})$/i.exec(request.headers.get('authorization') ?? '')?.[1];
  const expected = Buffer.from('owner:' + secret).toString('base64');
  return presented?.length === expected.length && timingSafeEqual(Buffer.from(presented), Buffer.from(expected));
}

function network(ip) {
  if (isIP(ip) === 4) return ip;
  if (isIP(ip) !== 6 || ip.includes('%')) return null;
  const normal = new URL(`https://[${ip}]`).hostname.slice(1, -1);
  const mapped = /^::ffff:([0-9a-f]+):([0-9a-f]+)$/.exec(normal);
  if (mapped) return mapped.slice(1).flatMap(x => [parseInt(x, 16) >> 8, parseInt(x, 16) & 255]).join('.');
  const [lead, tail] = normal.split('::');
  const first = lead ? lead.split(':') : [];
  const last = tail ? tail.split(':') : [];
  return [...first, ...Array(8 - first.length - last.length).fill('0'), ...last].slice(0, 4).join(':') + '/64';
}

export function createHandlers(getConfig, getStore) {
  function configFor(request) {
    const config = getConfig();
    const url = new URL(request.url);
    if (!config || request.url.includes('?') || url.username || url.password) return null;
    // Vercel's Node Fetch adapter may reconstruct an HTTPS request as HTTP
    // when the Host has no :443. Trust only its HTTPS forwarding signal.
    const proxiedHttps = url.protocol === 'http:' && request.headers.get('x-forwarded-proto') === 'https';
    if (proxiedHttps) url.protocol = 'https:';
    return url.origin === config.origin ? config : null;
  }

  async function collect(request) {
    const config = configFor(request);
    if (!config) return reply(404);
    if (request.method !== 'POST' && request.method !== 'DELETE') return reply(405, { Allow: 'POST, DELETE' });
    // This header must be overwritten by the directly connected Vercel edge.
    // Never fall back to client-provided X-Forwarded-For or the DB adapter.
    const source = network(request.headers.get('x-vercel-forwarded-for') ?? '');
    if (!source) return reply(404);
    if (request.headers.has('origin')) return reply(404); // Native clients only; no CORS.
    const bearer = /^Bearer ([0-9a-f]{64})$/i.exec(request.headers.get('authorization') ?? '')?.[1];
    if (!bearer || !HEX.test(bearer)) return reply(401);
    const remove = request.method === 'DELETE';
    const [status, record] = await readRecord(request, remove);
    if (!record) return reply(status);
    const { installationId } = record;
    const data = {
      installation: digest(config.key, 'installation', installationId),
      credential: digest(config.key, 'credential', installationId + ':' + bearer),
      source: digest(config.key, 'source', source),
      active: record.active ?? false,
      event: record.events?.length ? digest(config.key, 'event', installationId + ':' + record.events[0].id) : null,
    };
    try {
      const result = await getStore(config).mutate(remove, data);
      if (result >= 1 && result <= 900) return reply(429, { 'Retry-After': String(result) });
      if (result === 0) return reply(204);
      if (result === -1) return reply(404);
      if (result === -2) return reply(410);
      return reply(503);
    } catch {
      // Never emit SQL error details or log request metadata, body or secrets.
      return reply(503);
    }
  }

  async function owner(request) {
    const config = configFor(request);
    if (!config) return reply(404);
    if (request.method !== 'GET' && request.method !== 'HEAD') return reply(405, { Allow: 'GET, HEAD' });
    if (!isOwner(request, config.ownerSecret)) return reply(401, { 'WWW-Authenticate': 'Basic realm="KANTA owner", charset="UTF-8"' });
    try {
      const metrics = await getStore(config).metrics();
      if (!metrics) return reply(503);
      return reply(200, { 'Content-Type': 'text/html; charset=utf-8' }, request.method === 'HEAD' ? null : dashboard(metrics));
    } catch {
      return reply(503);
    }
  }
  return { collect, owner };
}
