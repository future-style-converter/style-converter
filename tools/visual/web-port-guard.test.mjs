#!/usr/bin/env node
// Pins for tools/visual/web-port-guard.sh (wave 52): the capture scripts may
// kill ONLY a vite dev server started from this checkout; any other program
// listening on the port must survive. Behavioural, with real throwaway
// listeners — a source scan could not tell a guard that works from one that
// merely exists.
//
// NEGATIVE CONTROL, EXECUTED (wave 52): with `wpg_is_our_vite` mutated to
// `return 0` on its first line, "a foreign listener survives" and "a
// vite-named listener OUTSIDE the checkout survives" both go red (the guard
// kills the throwaway servers); restoring the file turns them green.
//
// Run via `node --test tools/visual/web-port-guard.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { readFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');                       // this checkout's root = PROJECT_ROOT for the guard
const GUARD = join(REPO, 'tools', 'visual', 'web-port-guard.sh');
// A tiny HTTP listener on an OS-chosen port that prints the port it got; extra
// argv (e.g. the word "vite") only decorates the command line ps shows.
const SERVER = "const s=require('http').createServer((q,r)=>r.end('ok'));s.listen(0,'127.0.0.1',()=>console.log(s.address().port));";

/** Start a throwaway listener in `cwd` with `tag` as an extra argv; resolves to { child, port }. */
function listen(cwd, tag) {
  return new Promise((ok, bad) => {
    const child = spawn(process.execPath, ['-e', SERVER, ...(tag ? [tag] : [])], { cwd, stdio: ['ignore', 'pipe', 'inherit'] });
    child.stdout.once('data', (d) => ok({ child, port: Number(String(d).trim()) })); // the first line is the port
    child.once('error', bad);
  });
}
/** Run one guard function in a fresh bash with PROJECT_ROOT set, as the callers do. */
function guard(call) {
  return spawnSync('bash', ['-c', `PROJECT_ROOT="${REPO}"; source "${GUARD}"; ${call}`], { encoding: 'utf8' });
}
/** True when the process is still running (signal 0 probes without killing). */
const alive = (child) => { try { process.kill(child.pid, 0); return true; } catch { return false; } };
const settle = () => new Promise((r) => setTimeout(r, 300));       // let a SIGKILL be reaped before probing
// The guard's only probe is lsof. The capture harness is a macOS host tool, so a
// machine without lsof (a slim CI image) skips the behavioural pins — loudly, by
// name — and still runs the source pin at the bottom.
const NEEDS_LSOF = spawnSync('lsof', ['-v']).error ? { skip: 'lsof is not installed on this machine' } : {};

test('a foreign listener on the port survives, and the guard reports it (exit 1)', NEEDS_LSOF, async () => {
  const { child, port } = await listen(tmpdir(), null);            // plain node, cwd outside the checkout
  try {
    const r = guard(`wpg_kill_our_vite_on_port ${port}`);
    assert.equal(r.status, 1, 'a foreign holder must make the guard return 1');
    assert.match(r.stderr, /not this checkout's vite, not touching it/); // it says who holds the port
    await settle();
    assert.equal(alive(child), true, 'the foreign server must still be running');
  } finally { child.kill('SIGKILL'); }
});

test('a vite-named listener OUTSIDE the checkout survives (name alone is not enough)', NEEDS_LSOF, async () => {
  const { child, port } = await listen(mkdtempSync(join(tmpdir(), 'wpg-')), 'vite'); // argv says vite, cwd is foreign
  try {
    const r = guard(`wpg_kill_our_vite_on_port ${port}`);
    assert.equal(r.status, 1);
    await settle();
    assert.equal(alive(child), true, 'a sibling checkout\'s vite is not ours to kill');
  } finally { child.kill('SIGKILL'); }
});

test('a non-vite listener INSIDE the checkout survives (cwd alone is not enough)', NEEDS_LSOF, async () => {
  const { child, port } = await listen(join(REPO, 'apps', 'web-harness'), null); // our cwd, but not vite
  try {
    const r = guard(`wpg_kill_our_vite_on_port ${port}`);
    assert.equal(r.status, 1);
    await settle();
    assert.equal(alive(child), true);
  } finally { child.kill('SIGKILL'); }
});

test('our own stale vite (vite in argv, cwd inside the checkout) IS killed, and the guard returns 0', NEEDS_LSOF, async () => {
  const { child, port } = await listen(join(REPO, 'apps', 'web-harness'), 'vite');
  try {
    const r = guard(`wpg_kill_our_vite_on_port ${port}`);
    assert.equal(r.status, 0, 'nothing foreign holds the port');
    await settle();
    assert.equal(alive(child), false, 'the stale vite of this checkout must be gone');
  } finally { if (alive(child)) child.kill('SIGKILL'); }
});

test('wpg_free_port skips a busy port and fails when the whole range is taken', NEEDS_LSOF, async () => {
  const { child, port } = await listen(tmpdir(), null);
  try {
    const next = guard(`wpg_free_port ${port} ${port + 20}`);
    assert.equal(next.status, 0);
    assert.notEqual(Number(next.stdout.trim()), port, 'the busy port is never offered');
    assert.equal(guard(`wpg_free_port ${port} ${port}`).status, 1, 'a fully taken range is an error, not a guess');
  } finally { child.kill('SIGKILL'); }
});

test('every capture script frees its port ONLY through the guard', () => {
  // test-all, the section runner, and the text-metrics probe (which kept the old kill until later in the same wave).
  for (const rel of ['test-all.sh', join('tools', 'titan', 'section-runner.sh'), join('tools', 'visual', 'probe-text-metrics.sh')]) {
    const src = readFileSync(join(REPO, rel), 'utf8');
    // The old unconditional kill, in any spelling of the port variable, is gone…
    assert.doesNotMatch(src, /lsof -ti:"\$(WEB_)?PORT"[^\n]*xargs kill/, `${rel} still kills whatever holds the port`);
    // …and both sites go through the guard, which the script sources.
    assert.match(src, /web-port-guard\.sh/, `${rel} must source the guard`);
    assert.match(src, /wpg_kill_our_vite_on_port/, `${rel} must free the port through the guard`);
  }
});
