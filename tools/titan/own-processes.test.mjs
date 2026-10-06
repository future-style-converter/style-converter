#!/usr/bin/env node
// Pins for tools/titan/own-processes.sh (wave 52): the gate driver may stop
// ONLY the emulators provision-devices.sh recorded and the test browsers that
// were started from this checkout. Behavioural, with real throwaway processes
// — and HERMETIC: every call runs against a throwaway pool dir and a throwaway
// "checkout", so this file can run while a real gate is capturing without
// touching that gate's emulator or its puppeteer browsers.
//
// NEGATIVE CONTROLS, EXECUTED (wave 52):
//   • own_emulator_pids' command-line check replaced by `echo "$c"` → "a
//     recorded pid that is no longer an emulator survives" goes red;
//   • _op_cwd_in_checkout mutated to `return 0` → "a test browser started
//     OUTSIDE the checkout survives" goes red;
//   • gate-driver.sh with one `pkill -f 'qemu-system'` restored → the source
//     pin goes red.
//
// Run via `node --test tools/titan/own-processes.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');
const LIB = join(REPO, 'tools', 'titan', 'own-processes.sh');
// macOS tmpdir is a symlink (/var → /private/var); lsof reports the real path.
const scratch = () => realpathSync(mkdtempSync(join(tmpdir(), 'ownproc-')));

/** A do-nothing process whose command line carries `tag` (ps shows argv), started in `cwd`. */
function idle(cwd, tag) {
  return new Promise((ok, bad) => {
    const child = spawn(process.execPath, ['-e', "console.log('up');setInterval(()=>{},1000)", tag], { cwd, stdio: ['ignore', 'pipe', 'inherit'] });
    child.stdout.once('data', () => ok(child));   // wait until it is really running
    child.once('error', bad);
  });
}
/** Run one library function in a fresh bash against a throwaway pool + checkout. */
function lib(call, { pool, checkout }) {
  return spawnSync('bash', ['-c', `PROJECT_ROOT="${checkout}"; TITAN_POOL_ROOT="${pool}"; source "${LIB}"; ${call}`], { encoding: 'utf8' });
}
const alive = (child) => { try { process.kill(child.pid, 0); return true; } catch { return false; } };
const settle = () => new Promise((r) => setTimeout(r, 400));   // let a SIGTERM be delivered and reaped
const reap = (...children) => children.forEach((c) => { if (alive(c)) c.kill('SIGKILL'); });
// Ownership of a test browser is read from its cwd through lsof; without lsof (a
// slim CI image) that one pin is skipped by name — the emulator pins need only ps/pgrep.
const NEEDS_LSOF = spawnSync('lsof', ['-v']).error ? { skip: 'lsof is not installed on this machine' } : {};

test('a recorded emulator is stopped; an unrecorded one survives and is named', async () => {
  const pool = scratch(), checkout = scratch();
  // Both look like emulators to ps; only the first is in the pool's record.
  const ours = await idle(tmpdir(), 'qemu-system-aarch64-headless');
  const foreign = await idle(tmpdir(), 'qemu-system-aarch64-headless');
  try {
    writeFileSync(join(pool, 'emulator-pids'), `${ours.pid}\n`);
    assert.equal(lib('own_emulator_pids', { pool, checkout }).stdout.trim(), String(ours.pid));
    const r = lib('kill_own_emulators', { pool, checkout });
    assert.equal(r.status, 0);
    assert.match(r.stderr, new RegExp(`emulator pid ${foreign.pid} was not launched by provision-devices.sh — not touching it`));
    await settle();
    assert.equal(alive(ours), false, 'the emulator this project launched must be gone');
    assert.equal(alive(foreign), true, 'an emulator nobody recorded is somebody else\'s');
  } finally { reap(ours, foreign); }
});

test('a recorded pid that is no longer an emulator survives (pid reuse)', async () => {
  const pool = scratch(), checkout = scratch();
  // The record outlives the emulator; the OS may hand its pid to anything.
  const reused = await idle(tmpdir(), 'some-unrelated-program');
  try {
    writeFileSync(join(pool, 'emulator-pids'), `${reused.pid}\nnot-a-pid\n`);
    assert.equal(lib('own_emulator_pids', { pool, checkout }).stdout.trim(), '', 'not an emulator → not ours');
    lib('kill_own_emulators', { pool, checkout });
    await settle();
    assert.equal(alive(reused), true);
  } finally { reap(reused); }
});

test('no pool record means nothing is ours', async () => {
  const pool = scratch(), checkout = scratch();
  const stray = await idle(tmpdir(), 'qemu-system-aarch64-headless');
  try {
    assert.equal(lib('own_emulator_pids', { pool, checkout }).stdout, '');
    lib('kill_own_emulators', { pool, checkout });
    await settle();
    assert.equal(alive(stray), true);
  } finally { reap(stray); }
});

test('a test browser started from the checkout is stopped; one started OUTSIDE it survives', NEEDS_LSOF, async () => {
  const pool = scratch(), checkout = scratch(), elsewhere = scratch();
  const ours = await idle(checkout, 'Google Chrome for Testing');      // cwd inside the "checkout"
  const foreign = await idle(elsewhere, 'Google Chrome for Testing');  // another project's browser
  try {
    const r = lib('kill_own_test_browsers', { pool, checkout });
    assert.equal(r.status, 0);
    assert.match(r.stderr, new RegExp(`test browser pid ${foreign.pid} was not started from this checkout`));
    await settle();
    assert.equal(alive(ours), false, 'this checkout\'s leftover browser must be gone');
    assert.equal(alive(foreign), true, 'another project\'s browser is not ours to stop');
  } finally { reap(ours, foreign); }
});

test('gate-driver.sh stops emulators and browsers ONLY through the library', () => {
  const src = readFileSync(join(REPO, 'tools', 'titan', 'gate-driver.sh'), 'utf8');
  const live = src.split('\n').filter((l) => !/^\s*#/.test(l));     // comments may still describe the old behaviour
  assert.equal(live.filter((l) => /pkill[^\n]*qemu-system/.test(l)).length, 0, 'no host-wide emulator pkill');
  assert.equal(live.filter((l) => /pkill[^\n]*Chrome for Testing/.test(l)).length, 0, 'no host-wide browser pkill');
  assert.match(src, /source "\$TITAN_DIR\/own-processes\.sh"/, 'the driver sources the library');
  // Start-up, the watchdog's reprovision and the teardown: three emulator stops.
  assert.equal(live.filter((l) => /\bkill_own_emulators\b/.test(l)).length, 3, 'all three former pkill sites go through the library');
  assert.equal(live.filter((l) => /\bkill_own_test_browsers\b/.test(l)).length, 1);
});
