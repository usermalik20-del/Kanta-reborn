import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';

// Exercise the shipped script, not a reimplementation. Real browser/TLS coverage is separate.
const script = await readFile(new URL('../../apps/android/app/src/main/assets/companion/remote.js', import.meta.url), 'utf8');
const code = 'b'.repeat(32), token = 'c'.repeat(64);
const tick = () => new Promise(resolve => setImmediate(resolve));
function fakePage({native = false, legacy = false} = {}) {
  const root = new EventTarget(), document = new EventTarget();
  const nodes = new Map(), calls = [], timers = new Map();
  let id = 0, clock = 0, submitId = 0, network = true, reject = 0, boot = native;
  const user = new Event('submit', {cancelable: true});
  function node(name) {
    if (!nodes.has(name)) {
      const item = new EventTarget();
      item.disabled = false; item.value = ''; item.hidden = name === 'controller'; item.dataset = {};
      item.replaceChildren = () => {}; item.append = () => {}; item.textContent = '';
      item.querySelector = selector => node(name + selector);
      nodes.set(name, item);
    }
    return nodes.get(name);
  }
  document.hidden = false;
  document.getElementById = node;
  document.querySelectorAll = () => [];
  document.createElement = tag => node(tag + (++id));
  const accept = (path, body, authorization) => {
    calls.push({path, body, authorization});
    if (!network) return {status: 0, body: ''};
    if (reject) return {status: reject, body: ''};
    if (path === '/pair') return {status: 200, body: token, reconnect: !legacy};
    if (path === '/leave') return {status: 200, body: ''};
    const request = Buffer.from(body.subarray(0, 16)).toString('hex').replace(/^(........)(....)(....)(....)/, '$1-$2-$3-$4-');
    return {status: 200, body: JSON.stringify({request, version: 1, source: 'local', playing: false,
      current: 'No song playing', songs: [], queue: [], capabilities: ['snapshot','search','reserve','cancel','play','pause','next','stop']})};
  };
  function timer(fn, delay, repeat) {timers.set(++id, {fn, due: clock + delay, repeat: repeat ? delay : 0}); return id;}
  function advance(ms) {
    clock += ms;
    // Only run the latest interval tick, like a tab resuming after suspension.
    for (const [key, item] of timers) if (item.due <= clock) {
      if (item.repeat) item.due = clock + item.repeat; else timers.delete(key);
      item.fn();
    }
  }
  const bindings = {document, AbortController, TextEncoder, DataView, Uint8Array, btoa,
    addEventListener: root.addEventListener.bind(root),
    setInterval: (fn, delay) => timer(fn, delay, true), setTimeout: timer,
    clearInterval: i => timers.delete(i), clearTimeout: i => timers.delete(i),
    crypto: {randomUUID: () => `00000000-0000-4000-8000-${String(++submitId).padStart(12,'0')}`},
    fetch: async (path, options) => {
      assert.equal(options.referrerPolicy, 'no-referrer');
      assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error');
      assert.ok(/^\/(pair|status|command|leave)$/.test(path));
      const result = accept(path, options.body, options.headers.Authorization);
      if (result.status === 0) throw new Error('Offline');
      return {ok: result.status === 200, status: result.status, text: async () => result.body,
        headers: {get: name => name === 'X-Kanta-Remote-Reconnect' && result.reconnect ? '1' : null}};
    }};
  if (native) bindings.KantaSecureTransport = {
    hasPairingCode: () => boot,
    post: (id, path, encoded, credential) => {
      const body = Buffer.from(encoded, 'base64');
      if (path === '/pair') {assert.ok(boot); boot = false; assert.equal(body.length, 0);}
      const response = accept(path, body, credential);
      queueMicrotask(() => root.dispatchEvent(new CustomEvent('kanta-response', {detail:{id, ...response}})));
    }
  };
  runInNewContext(script, bindings);
  return {document, node, calls, advance, reject: value => {reject = value;}, submit: () => node('pair-form').dispatchEvent(user),
    click: name => node(name).dispatchEvent(new Event('click')),
    send: name => root.dispatchEvent(new Event(name)), wifi: value => {network = value;}};
}

const page = fakePage();
page.node('code').value = code; page.submit(); await tick();
assert.equal(page.calls.length, 0, 'Unverified manual guest sent a credential');
page.node('verified').checked = true;
page.submit(); await tick();
assert.deepEqual(page.calls.map(call => call.path), ['/pair', '/status']);
assert.equal(new TextDecoder().decode(page.calls[0].body), code);
assert.equal(page.calls[1].authorization, 'Bearer ' + token);
assert.equal(page.node('code').value, '');
page.wifi(false); page.click('play-pause'); await tick();
assert.match(page.node('status').textContent, /Reconnecting/);
assert.equal(page.node('lookup').disabled, true);
page.document.hidden = true; page.document.dispatchEvent(new Event('visibilitychange'));
assert.equal(page.calls.filter(call => call.path === '/leave').length, 0);
page.wifi(true); page.document.hidden = false; page.document.dispatchEvent(new Event('visibilitychange')); await tick();
assert.equal(page.node('lookup').disabled, false);
assert.equal(page.calls.filter(call => call.path === '/command').length, 1, 'Mutation replayed during reconnect');
assert.match(page.node('notice').textContent, /Check the queue/);
page.reject(429); page.click('refresh'); await tick();
assert.match(page.node('notice').textContent, /request limit/);
assert.equal(page.node('controller').hidden, false);
page.reject(401); page.click('refresh'); await tick();
assert.equal(page.node('controller').hidden, true);
assert.equal(page.calls.filter(call => call.path === '/leave').length, 0);

const scanned = fakePage({native: true}); await tick();
assert.deepEqual(scanned.calls.map(call => call.path), ['/pair', '/status']);
assert.equal(scanned.calls[0].body.length, 0, 'Native bootstrap reached page JS');
assert.equal(scanned.calls[1].authorization, token);
scanned.send('online'); await tick();
assert.equal(scanned.calls.filter(call => call.path === '/pair').length, 1);
scanned.wifi(false);
scanned.advance(3*60*60*1000-1); await tick();
assert.equal(scanned.node('controller').hidden, false);
scanned.advance(1); await tick();
assert.equal(scanned.node('controller').hidden, true);

const old = fakePage({legacy: true});
old.node('verified').checked = true; old.node('code').value = code;
old.submit(); await tick();
assert.deepEqual(old.calls.map(call => call.path), ['/pair','/command']);
old.document.hidden = true; old.document.dispatchEvent(new Event('visibilitychange')); await tick();
assert.equal(old.calls.at(-1).path, '/leave');
assert.equal(old.node('controller').hidden, true);
console.log('Browser Remote JS: manual trust, native bootstrap, reconnect without replay, rate limit, revocation, offline expiry and legacy fallback passed.');
