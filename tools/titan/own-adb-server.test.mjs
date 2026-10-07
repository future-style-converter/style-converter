#!/usr/bin/env node
// Pins for kill_wedged_adb_server (tools/titan/own-processes.sh, wave 53,
// harness-hygiene T3) and its two call sites. The device-listing timeout
// branches of section-runner.sh and provision-devices.sh ran
// `kill-server; pkill -9 -x adb`: the pkill fired even when kill-server
// worked and killed every process NAMED adb (every session's clients). Only
// the server's listener on the adb port may be killed now. Behavioural, on a
// throwaway ANDROID_ADB_SERVER_PORT, plus source pins on the gate path.
//
// NEGATIVE CONTROLS, EXECUTED with byte-exact restores
// (tools/titan/results/wave53-harness-hygiene/mutate.py → mutations.log): any
// listener taken for the server (A1); the LISTEN filter dropped (A2);
// `pkill -9 -x adb` restored in section-runner.sh (S1) or provision-devices.sh
// (S2) — text only, nothing executes the restored line.
//
// Run via `node --test tools/titan/own-adb-server.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');
const LIB = join(REPO, 'tools', 'titan', 'own-processes.sh');
const alive = (child) => { try { process.kill(child.pid, 0); return true; } catch { return false; } };
const settle = () => new Promise((r) => setTimeout(r, 400));   // let a SIGKILL be delivered and reaped
const reap = (...children) => children.forEach((c) => { if (alive(c)) c.kill('SIGKILL'); });
/** Non-comment lines of a gate script (comments may still describe the old behaviour). */
const live = (src) => src.split('\n').filter((l) => !/^\s*#/.test(l));
const readTitan = (f) => readFileSync(join(REPO, 'tools', 'titan', f), 'utf8');
// The listener probe is lsof; without it (a slim CI image) the behavioural pins skip by name.
const NEEDS_LSOF = spawnSync('lsof', ['-v']).error ? { skip: 'lsof is not installed on this machine' } : {};

// ── adb server (wave 53, harness-hygiene T3) ─────────────────────────────────
// A throwaway listener stands in for the server on a throwaway
// ANDROID_ADB_SERVER_PORT; its extra argv carries the real server's shape
// (`adb -L tcp:5037 fork-server server --reply-fd 4`, read off the live one).
const LISTENER = "const s=require('net').createServer(()=>{});s.listen(0,'127.0.0.1',()=>console.log(s.address().port));";
/** A listener on an OS-chosen port; resolves to { child, port }. */
function listener(tag) {
  return new Promise((ok, bad) => {
    const child = spawn(process.execPath, ['-e', LISTENER, ...(tag ? [tag] : [])], { cwd: tmpdir(), stdio: ['ignore', 'pipe', 'inherit'] });
    child.stdout.once('data', (d) => ok({ child, port: Number(String(d).trim()) }));
    child.once('error', bad);
  });
}
/** A CLIENT connected to `port` whose argv looks like the server's: not listening, so never the server. */
function client(port, tag) {
  return new Promise((ok, bad) => {
    const child = spawn(process.execPath, ['-e', `require('net').connect(${port},'127.0.0.1',()=>console.log('up'));setInterval(()=>{},1000)`, tag], { cwd: tmpdir(), stdio: ['ignore', 'pipe', 'inherit'] });
    child.stdout.once('data', () => ok(child));
    child.once('error', bad);
  });
}
const adbCall = (port) => spawnSync('bash', ['-c', `source "${LIB}"; kill_wedged_adb_server`], { encoding: 'utf8', env: { ...process.env, ANDROID_ADB_SERVER_PORT: String(port) } });
const SERVER_ARGV = (port) => `adb -L tcp:${port} fork-server server --reply-fd 4`;

test('adb: the wedged server (the fork-server listener on the adb port) is killed', NEEDS_LSOF, async () => {
  const fake = await listener('adb fork-server server');
  try {
    const r = adbCall(fake.port);
    assert.equal(r.status, 0);
    assert.match(r.stderr, new RegExp(`killed the wedged adb server pid ${fake.child.pid} on tcp:${fake.port}`));
    await settle();
    assert.equal(alive(fake.child), false);
  } finally { reap(fake.child); }
});

test('adb: a foreign listener on the adb port and a connected fork-server CLIENT both survive', NEEDS_LSOF, async () => {
  const foreign = await listener(null);                                  // somebody else's server on that port
  const decoy = await client(foreign.port, SERVER_ARGV(foreign.port));   // the server's argv, but only a client socket
  try {
    const r = adbCall(foreign.port);
    assert.equal(r.status, 0);
    assert.match(r.stderr, new RegExp(`tcp:${foreign.port} is held by pid ${foreign.child.pid} .* not an adb server, not touching it`));
    await settle();
    assert.equal(alive(foreign.child), true, 'a non-adb listener is not ours to kill');
    assert.equal(alive(decoy), true, 'a client socket to the port is not the server');
  } finally { reap(foreign.child, decoy); }
});

test('no live `pkill … adb` in the gate path; both timeout branches stop the server through the library', () => {
  for (const f of ['gate-driver.sh', 'section-runner.sh', 'provision-devices.sh']) {
    assert.equal(live(readTitan(f)).filter((l) => /pkill[^\n]*\badb\b/.test(l)).length, 0, `${f}: no pkill of every process named adb`);
  }
  for (const f of ['section-runner.sh', 'provision-devices.sh']) {
    const src = readTitan(f);
    assert.match(src, /^source "\$(PROJECT_ROOT\/tools\/titan|SCRIPT_DIR)\/own-processes\.sh"$/m, `${f} sources the library`);
    assert.equal(live(src).filter((l) => /kill-server[^\n]*; kill_wedged_adb_server; sleep 1/.test(l)).length, 1, `${f}: the timeout branch stops the server by its listener`);
  }
});
