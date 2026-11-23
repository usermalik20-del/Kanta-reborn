import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { PGlite } from '@electric-sql/pglite';
import { configFromEnv, createHandlers } from '../server/diagnostics.mjs';
import { postgresStore } from '../server/diagnostics-store.mjs';

const origin = 'https://example.invalid';
const always = {
  KANTA_DIAGNOSTICS_ENABLE: 'true', VERCEL: '1', VERCEL_ENV: 'production',
  KANTA_DIAGNOSTICS_DB_URL: 'postgresql://user:synthetic@db.invalid/diagnostics',
  KANTA_DIAGNOSTICS_HMAC_KEY: 'a'.repeat(64), KANTA_DIAGNOSTICS_OWNER_SECRET: 'b'.repeat(64),
  KANTA_DIAGNOSTICS_ORIGIN: origin,
};
const config = configFromEnv(always);
const bearer = 'c'.repeat(64);
const installationId = '01234567-89ab-4def-8123-456789abcdef';
const event = { type: 'crash', id: 'fedcba98-7654-4321-8def-0123456789ab' };
const pulse = { installationId, active: true, events: [] };
const headers = { 'content-type': 'application/json', authorization: `Bearer ${bearer}`, 'x-vercel-forwarded-for': '192.0.2.1' };
function req(body, { method = 'POST', h = headers, path = '/api/diagnostics', base = origin } = {}) {
  return new Request(base + path, { method, headers: h, body: typeof body === 'object' && !(body instanceof ReadableStream) && !Buffer.isBuffer(body) ? JSON.stringify(body) : body, duplex: 'half' });
}
const ownerAuth = `Basic ${Buffer.from('owner:' + config.ownerSecret).toString('base64')}`;
function dashboard(authorization, method = 'GET', path = '/owner/diagnostics') {
  return req(undefined, { method, path, h: authorization ? { authorization } : {} });
}

test('production approval, secret, TLS URL and canonical host are required', async () => {
  for (const key of Object.keys(always)) assert.equal(configFromEnv({ ...always, [key]: undefined }), null, key);
  assert.equal(configFromEnv({ ...always, VERCEL_ENV: 'preview' }), null);
  assert.equal(configFromEnv({ ...always, KANTA_DIAGNOSTICS_DB_URL: always.KANTA_DIAGNOSTICS_DB_URL + '?sslmode=disable' }), null);
  let accessed = 0;
  const make = c => createHandlers(() => c, () => { accessed++; });
  assert.equal((await make(null).collect(req(pulse))).status, 404);
  assert.equal((await make(null).owner(dashboard(ownerAuth))).status, 404);
  assert.equal((await make(config).collect(req(pulse, { base: 'https://preview.invalid' }))).status, 404);
  assert.equal((await make(config).collect(req(pulse, { path: '/api/diagnostics?id=x' }))).status, 404);
  assert.equal((await make(config).collect(req(pulse, { base: 'http://example.invalid' }))).status, 404);
  assert.equal(accessed, 0);
  const { collect } = createHandlers(() => config, () => ({ mutate: () => 0 }));
  assert.equal((await collect(req(pulse, { base: 'http://example.invalid', h: { ...headers, 'x-forwarded-proto': 'https' } }))).status, 204);
  assert.equal((await collect(req(pulse, { base: 'http://wrong.invalid', h: { ...headers, 'x-forwarded-proto': 'https' } }))).status, 404);
});

test('strict schema, UTF-8, duplicate keys and actual streamed byte limit', async () => {
  const { collect } = createHandlers(() => config, () => ({ mutate: () => 0 }));
  const text = JSON.stringify(pulse);
  const cases = [
    [pulse, 204], [{ ...pulse, active: false, events: [event] }, 204],
    [{ ...pulse, active: false }, 400], [{ ...pulse, active: 'true' }, 400],
    [{ ...pulse, installationId: installationId.toUpperCase() }, 400],
    [{ ...pulse, events: [{ type: 'crash', id: 'fedcba98-7654-3321-8def-0123456789ab' }] }, 400],
    [{ ...pulse, events: [event, event] }, 400], [{ ...pulse, events: [{ ...event, message: 'forbidden' }] }, 400],
    [{ ...pulse, events: [{ ...event, type: 'play' }] }, 400], [{ ...pulse, lyrics: 'forbidden' }, 400],
    [text.replace('"active":true', '"active":false,"\\u0061ctive":true'), 400],
    [JSON.stringify({ ...pulse, events: [event] }).replace('"type":"crash"','"type":"crash","type":"crash"'), 400],
    [text.padEnd(1024), 204], [text.padEnd(1025), 413], [Buffer.from([0xff]), 400],
    [new ReadableStream({ start(c) { c.enqueue(Buffer.alloc(512)); c.enqueue(Buffer.alloc(513)); c.close(); } }), 413],
  ];
  for (const [data, status] of cases) assert.equal((await collect(req(data))).status, status, typeof data === 'string' ? data.slice(0, 140) : undefined);
  for (const [h, status] of [[{ 'content-length': '1025' }, 413], [{ 'content-length': '1' }, 400],
    [{ 'content-encoding': 'gzip' }, 415], [{ 'content-type': 'text/plain' }, 415],
    [{ origin }, 404], [{ authorization: `Bearer ${bearer.toUpperCase()}` }, 401],
    [{ 'x-vercel-forwarded-for': '1.2.3.4, 5.6.7.8' }, 404]]) {
    assert.equal((await collect(req(pulse, { h: { ...headers, ...h } }))).status, status);
  }
  const { 'x-vercel-forwarded-for': ignored, ...noSource } = headers;
  assert.equal((await collect(req(pulse, { h: { ...noSource, 'x-forwarded-for': '1.2.3.4' } }))).status, 404);
  assert.equal((await collect(req(pulse, { method: 'DELETE' }))).status, 400);
});

test('database gets keyed identifiers only, source is normalized, failures are opaque', async () => {
  const seen = [];
  const { collect } = createHandlers(() => config, () => ({ mutate: (_, data) => { seen.push(data); return 67; } }));
  for (const ip of ['2001:db8:0:12::1', '2001:0db8:0000:0012::ffff', '192.0.2.1', '::ffff:192.0.2.1']) {
    const r = await collect(req({ ...pulse, events: [event] }, { h: { ...headers, 'x-vercel-forwarded-for': ip } }));
    assert.equal(r.status, 429);
    assert.equal(r.headers.get('retry-after'), '67');
    assert.equal(await r.text(), '');
  }
  assert.equal(seen[0].source, seen[1].source);
  assert.equal(seen[2].source, seen[3].source);
  assert.notEqual(seen[1].source, seen[2].source);
  for (const secret of [installationId, event.id, bearer, '2001:db8', '192.0.2.1']) assert.ok(!JSON.stringify(seen).includes(secret));
  const broken = createHandlers(() => config, () => { throw new Error(bearer); });
  const response = await broken.collect(req(pulse));
  assert.equal(response.status, 503);
  assert.equal(await response.text(), '');
});

test('owner authorization precedes aggregate-only access and suppresses caching', async () => {
  let reads = 0;
  const { owner } = createHandlers(() => config, () => ({ metrics: () => { reads++; return { daily_active: '1', monthly_active: '3', daily_crashes: '2', monthly_crashes: '4', raw: bearer }; } }));
  for (const auth of [undefined, `Bearer ${bearer}`, 'Basic Zm9vOmJhcg==']) assert.equal((await owner(dashboard(auth))).status, 401);
  assert.equal(reads, 0);
  const response = await owner(dashboard(ownerAuth));
  assert.equal(response.status, 200);
  assert.match(response.headers.get('cache-control'), /no-store/);
  assert.match(response.headers.get('x-robots-tag'), /noindex/);
  const html = await response.text();
  assert.match(html, /Active installations/);
  assert.match(html, /Crash reports/);
  assert.match(html, /Downloads/);
  assert.doesNotMatch(html, /<script|google|https:\/\//);
  assert.ok(!html.includes(bearer));
  assert.equal(await (await owner(dashboard(ownerAuth, 'HEAD'))).text(), '');
  assert.equal((await owner(dashboard(ownerAuth, 'POST'))).status, 405);
});

const schema = await readFile(new URL('../server/diagnostics.sql', import.meta.url), 'utf8');
const id = n => n.toString(16).padStart(64, '0');
const sample = { installation: id(1), credential: id(2), source: id(3), active: true, event: id(4) };
const all = n => ({ daily_active: n, monthly_active: n, daily_crashes: n, monthly_crashes: n });

test('SQL: restricted roles, deletion, dedupe, UTC metrics and unattended expiry', async t => {
  const db = new PGlite();
  t.after(() => db.close());
  const store = postgresStore(db);
  await db.exec(schema);
  assert.equal(await store.metrics(), null);
  await assert.rejects(store.mutate(false, sample), /maintenance unavailable/);
  await db.exec(`CREATE ROLE synthetic_runtime; GRANT USAGE ON SCHEMA kanta_diagnostics TO synthetic_runtime;
    GRANT EXECUTE ON FUNCTION kanta_diagnostics.mutate(boolean,text,text,text,boolean,text), kanta_diagnostics.metrics() TO synthetic_runtime;
    SELECT kanta_diagnostics.purge(); SET ROLE synthetic_runtime;`);
  await assert.rejects(db.query('SELECT * FROM kanta_diagnostics.installations'), /permission denied/);
  await assert.rejects(db.query('SELECT kanta_diagnostics.purge()'), /permission denied/);
  assert.deepEqual(await store.metrics(), all(0));
  assert.equal(await store.mutate(false, sample), 0);
  assert.equal(await store.mutate(false, sample), 0);
  assert.equal(await store.mutate(false, { ...sample, credential: id(7) }), -1);
  assert.equal(await store.mutate(true, { ...sample, active: false, event: null, credential: id(7) }), -1);
  assert.deepEqual(await store.metrics(), all(1));
  await db.exec(`RESET ROLE; UPDATE kanta_diagnostics.activity SET receipt = date_trunc('day', now(), 'UTC') - interval '1 second', utc_day = (now() AT TIME ZONE 'UTC')::date - 1;`);
  assert.equal(await store.mutate(false, sample), 0);
  assert.deepEqual(await store.metrics(), all(1));
  assert.equal(await store.mutate(true, { ...sample, active: false, event: null }), 0);
  assert.equal(await store.mutate(false, sample), -2);
  assert.equal(await store.mutate(true, { ...sample, active: false, event: null }), 0);
  assert.deepEqual(await store.metrics(), all(0));
  await db.exec(`UPDATE kanta_diagnostics.installations SET last_receipt = now() - interval '30 days';
    UPDATE kanta_diagnostics.throttle SET bucket = now() - interval '16 minutes'; SELECT kanta_diagnostics.purge();`);
  assert.deepEqual((await db.query('SELECT (SELECT count(*) FROM kanta_diagnostics.installations) AS identities, (SELECT count(*) FROM kanta_diagnostics.throttle) AS limits')).rows, [{ identities: 0, limits: 0 }]);
  assert.equal(await store.mutate(false, sample), 0);
  await db.exec(`UPDATE kanta_diagnostics.activity SET receipt = now() - interval '30 days'; UPDATE kanta_diagnostics.crashes SET receipt = now() - interval '30 days'; SELECT kanta_diagnostics.purge();`);
  assert.deepEqual(await store.metrics(), all(0));
  assert.equal((await db.query('SELECT count(*) AS n FROM kanta_diagnostics.crashes')).rows[0].n, 0);
  await db.exec("UPDATE kanta_diagnostics.maintenance SET last_purge = clock_timestamp() - interval '13 minutes'");
  assert.equal(await store.metrics(), null);
  await assert.rejects(store.mutate(false, sample), /maintenance unavailable/);
});

test('SQL: independent clients share write caps, new registrations and rate-bucket limits', async t => {
  const db = new PGlite();
  t.after(() => db.close());
  const store = postgresStore(db);
  const other = postgresStore(db);
  await db.exec(schema + '\nSELECT kanta_diagnostics.purge();');
  const fill = async (scope, n) => db.query(`INSERT INTO kanta_diagnostics.throttle VALUES ($1, date_bin(interval '15 minutes', clock_timestamp(), timestamptz '2000-01-01 00:00:00+00'), $2)
    ON CONFLICT (scope,bucket) DO UPDATE SET hits = excluded.hits`, [scope, n]);
  const limited = r => assert.ok(r >= 1 && r <= 900);
  assert.equal(await store.mutate(false, sample), 0);
  await fill('i:' + sample.installation, 8);
  limited(await other.mutate(true, { ...sample, active: false, event: null }));
  await fill('n:' + sample.source, 8);
  limited(await other.mutate(false, { ...sample, installation: id(12) }));
  await fill('s:' + sample.source, 40);
  limited(await other.mutate(false, { ...sample, installation: id(13) }));
  await fill('global', 300);
  limited(await other.mutate(false, { ...sample, source: id(14), installation: id(15) }));
  assert.equal((await db.query('SELECT count(*) AS n FROM kanta_diagnostics.installations')).rows[0].n, 1);
  assert.equal((await db.query('SELECT hits FROM kanta_diagnostics.throttle WHERE scope = $1', ['global'])).rows[0].hits, 300);
});
