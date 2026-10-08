#!/usr/bin/env node
// Pins for tools/visual/smoke-port.sh and its call sites in tools/visual/smoke.sh
// (wave 53, harness-hygiene T4). smoke.sh used to start vite with `npm run dev`
// (vite.config.ts port 3000, no strictPort), poll http://localhost:3000/ for any
// 2xx, and let Tier 5 / Tier 11 default WEB_PORT to '3000' — so a foreign server
// answering 200 on :3000 passed the readiness poll and was what both probes
// measured. Behavioural pins with real throwaway listeners (the picker), plus
// source pins on smoke.sh (which cannot run here: it boots vite and Chromium).
//
// The picker is called with a THROWAWAY default port (its optional argument), so
// these pins never bind, probe or disturb the real :3000.
//
// NEGATIVE CONTROLS, EXECUTED (wave 53; tools/titan/results/wave53-harness-hygiene/
// mutations.log): the picker returning the default unconditionally (P1), a
// caller-chosen port accepted although a foreign program holds it (P2),
// `--strictPort` dropped from smoke.sh's vite launch (P3) and the readiness poll
// hard-wired back to :3000 (P4) each turn a pin below red.
//
// Run via `node --test tools/visual/smoke-port.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');                      // PROJECT_ROOT for the guard
const GUARD = join(REPO, 'tools', 'visual', 'web-port-guard.sh');
const PICK = join(REPO, 'tools', 'visual', 'smoke-port.sh');
// A tiny HTTP server answering 200 — exactly the foreign holder that fooled the old poll.
const SERVER = "const s=require('http').createServer((q,r)=>r.end('ok'));s.listen(0,'127.0.0.1',()=>console.log(s.address().port));";

/** Start a throwaway listener outside the checkout; resolves to { child, port }. */
function listen() {
  return new Promise((ok, bad) => {
    const child = spawn(process.execPath, ['-e', SERVER], { cwd: tmpdir(), stdio: ['ignore', 'pipe', 'inherit'] });
    child.stdout.once('data', (d) => ok({ child, port: Number(String(d).trim()) }));
    child.once('error', bad);
  });
}
/** smoke_pick_web_port <defaultPort> in a fresh bash, sourced the way smoke.sh sources it. */
function pick(defaultPort, webPort) {
  const env = { ...process.env };
  // Unset unless the pin chooses a port (the "caller-chosen" path).
  delete env.WEB_PORT;
  if (webPort !== undefined) env.WEB_PORT = String(webPort);
  return spawnSync('bash', ['-c', `PROJECT_ROOT="${REPO}"; source "${GUARD}"; source "${PICK}"; smoke_pick_web_port ${defaultPort}`], { encoding: 'utf8', env });
}
const alive = (child) => { try { process.kill(child.pid, 0); return true; } catch { return false; } };
const settle = () => new Promise((r) => setTimeout(r, 300));
/** A port that was just free: bind an OS-chosen one, then close it. */
async function freedPort() { const { child, port } = await listen(); child.kill('SIGKILL'); await settle(); return port; }
// The picker's only probe is lsof (through the guard); without it these pins skip by name.
const NEEDS_LSOF = spawnSync('lsof', ['-v']).error ? { skip: 'lsof is not installed on this machine' } : {};

test('a free default port is used as is', NEEDS_LSOF, async () => {
  const port = await freedPort();
  const r = pick(port);
  assert.equal(r.status, 0);
  assert.equal(r.stdout.trim(), String(port));
});

test('a default port held by a foreign server moves the smoke into 3400-3499, and the holder survives, named', NEEDS_LSOF, async () => {
  const { child, port } = await listen();
  try {
    const r = pick(port);
    assert.equal(r.status, 0, r.stderr);
    const got = Number(r.stdout.trim());
    assert.ok(got >= 3400 && got <= 3499, `expected a port in smoke's own range, got ${r.stdout.trim()}`);
    assert.match(r.stderr, new RegExp(`port ${port} is held by pid ${child.pid} .*not this checkout's vite, not touching it`));
    assert.match(r.stderr, new RegExp(`\\[smoke-port\\] port ${port} is in use by another program — using ${got} for this smoke`));
    await settle();
    assert.equal(alive(child), true, 'the foreign server must still be running');
  } finally { child.kill('SIGKILL'); }
});

test('a caller-chosen WEB_PORT held by a foreign server is an error, never swapped', NEEDS_LSOF, async () => {
  const { child, port } = await listen();
  const other = await freedPort();                                 // the default is free: only WEB_PORT is held
  try {
    const r = pick(other, port);
    assert.notEqual(r.status, 0, 'an explicit port the smoke cannot use must stop it');
    assert.equal(r.stdout.trim(), '', 'no port is offered');
    assert.match(r.stderr, new RegExp(`WEB_PORT=${port} is in use by another program`));
    await settle();
    assert.equal(alive(child), true);
  } finally { child.kill('SIGKILL'); }
});

test('a free caller-chosen WEB_PORT is honoured over the default', NEEDS_LSOF, async () => {
  const chosen = await freedPort(), dflt = await freedPort();
  const r = pick(dflt, chosen);
  assert.equal(r.status, 0);
  assert.equal(r.stdout.trim(), String(chosen));
});

test('smoke.sh: vite and both probes use the picked, exported port; nothing is hard-wired to :3000', () => {
  const src = readFileSync(join(REPO, 'tools', 'visual', 'smoke.sh'), 'utf8');
  const live = src.split('\n').filter((l) => !/^\s*#/.test(l));    // comments may still tell the old story
  assert.match(src, /^source "\$PROJECT_ROOT\/tools\/visual\/web-port-guard\.sh"$/m, 'smoke sources the port guard');
  assert.match(src, /^source "\$PROJECT_ROOT\/tools\/visual\/smoke-port\.sh"$/m, 'smoke sources the picker');
  assert.equal(live.filter((l) => /localhost:3000|:3000\b/.test(l)).length, 0, 'no live :3000');
  assert.equal(live.filter((l) => /npm run dev/.test(l)).length, 0, '`npm run dev` follows vite.config.ts port 3000 and drifts when it is held');
  // The vite launch binds exactly the picked port or exits.
  assert.equal(live.filter((l) => /exec npx vite --port "\$WEB_PORT" --strictPort/.test(l)).length, 1, 'vite --port "$WEB_PORT" --strictPort');
  assert.equal(live.filter((l) => /curl [^\n]*"http:\/\/localhost:\$\{WEB_PORT\}\/"/.test(l)).length, 1, 'the readiness poll asks the picked port');
  // Ordering in the run section: pick → export → start vite → Tier 5.
  const at = (re) => { const i = live.findIndex((l) => re.test(l)); assert.ok(i >= 0, `missing ${re}`); return i; };
  const pickAt = at(/WEB_PORT="\$\(smoke_pick_web_port\)"/), exportAt = at(/^\s*export WEB_PORT$/);
  const startAt = at(/^\s*start_vite \|\|/), tier5At = at(/^\s*run_tier5 /);
  assert.ok(pickAt < exportAt && exportAt < startAt && startAt < tier5At, 'pick, export, start vite, then Tier 5');
});

test('smoke\'s fallback range is disjoint from test-all\'s and section-runner\'s', () => {
  const read = (...p) => readFileSync(join(REPO, ...p), 'utf8');
  const range = (src, re) => { const m = src.match(re); assert.ok(m, `range missing: ${re}`); return [Number(m[1]), Number(m[2])]; };
  const smoke = range(read('tools', 'visual', 'smoke-port.sh'), /wpg_free_port (\d+) (\d+)/);
  const all = range(read('test-all.sh'), /wpg_free_port (\d+) (\d+)/);
  const sec = range(read('tools', 'titan', 'section-runner.sh'), /seq (\d+) (\d+)\)/);
  assert.deepEqual(smoke, [3400, 3499]);
  for (const [name, r] of [['test-all', all], ['section-runner', sec]]) {
    assert.ok(smoke[1] < r[0] || r[1] < smoke[0], `smoke ${smoke} overlaps ${name} ${r}`);
  }
  // The default stays the vite.config.ts port the two probes fall back to.
  assert.match(read('tools', 'visual', 'smoke-port.sh'), /local default="\$\{1:-3000\}"/);
});

// wave53 fix pass (S1 S7): build_fixtures moved to tools/visual/smoke-fixtures.sh (PLAN §0
// size rule), so its wiring is pinned here. MUTATION (executed, red → byte-exact restore →
// green; sha256 in tools/titan/results/wave53-S1/_note.md "## Fix pass"): SF1 the
// smoke-fixtures.sh `source` line dropped from smoke.sh → red.
test('smoke.sh: the Tier-5/11 fixture JSONs are built (smoke-fixtures.sh, sourced) before vite and both tiers', () => {
  const src = readFileSync(join(REPO, 'tools', 'visual', 'smoke.sh'), 'utf8');
  const fx = readFileSync(join(REPO, 'tools', 'visual', 'smoke-fixtures.sh'), 'utf8');
  const live = src.split('\n').filter((l) => !/^\s*#/.test(l));    // code lines only
  assert.match(src, /^source "\$PROJECT_ROOT\/tools\/visual\/smoke-fixtures\.sh"$/m, 'smoke sources build_fixtures');
  // The function is defined there, and only there, and builds through the harness's own script.
  assert.match(fx, /^build_fixtures\(\) \{$/m);
  assert.equal(live.filter((l) => /^build_fixtures\(\)/.test(l)).length, 0, 'no second definition in smoke.sh');
  assert.match(fx, /npm run --silent build-fixtures/);
  // A failed build skips vite (and so both tiers): build → gate → start vite.
  const at = (re) => { const i = live.findIndex((l) => re.test(l)); assert.ok(i >= 0, `missing ${re}`); return i; };
  const buildAt = at(/^if build_fixtures; then FIXTURES_OK=1; else EXIT_CODE=1; fi$/);
  const gateAt = at(/^if \(\( FIXTURES_OK \)\); then$/), startAt = at(/^\s*start_vite \|\|/);
  assert.ok(buildAt < gateAt && gateAt < startAt, 'build, gate, then vite');
  // Both scripts parse (bash -n: no execution).
  for (const f of ['smoke.sh', 'smoke-fixtures.sh']) assert.equal(spawnSync('bash', ['-n', join(REPO, 'tools', 'visual', f)]).status, 0, f);
});
