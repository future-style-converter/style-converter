#!/usr/bin/env node
// Pins for kill_own_gradle_daemons (tools/titan/own-processes.sh, wave 53,
// harness-hygiene T2) and its gate-driver.sh call site. The two
// `./gradlew --stop` calls of stop_our_processes stopped EVERY daemon of the
// user's Gradle version, whatever checkout it served; a daemon's cwd cannot
// tell (every daemon runs in ~/.gradle/daemon/<ver>), so ownership is read off
// the daemon's own log: ours iff it served >= 1 build and every one of them
// was inside one of the given roots. Behavioural, with real throwaway
// processes, and HERMETIC (a throwaway GRADLE_USER_HOME; TITAN_GRADLE_PIDS).
//
// NEGATIVE CONTROLS, EXECUTED with byte-exact restores
// (tools/titan/results/wave53-harness-hygiene/mutate.py → mutations.log): the
// ownership predicate forced to "ours" (G1); the root test without its `/`
// boundary (G2); the roots evaluated one at a time (G3); the nested-checkout
// walk removed (G4); an empty log counted as ours (G5); TITAN_GRADLE_PIDS
// ignored (G6); `./gradlew --stop` restored (S3); the driver.log routing
// dropped (S4). They run with `--test-skip-pattern DISCOVERY`: every other
// pin here carries TITAN_GRADLE_PIDS, so even a predicate mutated to "ours"
// only ever sees this file's own fake daemons, never a real one on the host.
//
// Run via `node --test tools/titan/own-gradle-daemons.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');
const LIB = join(REPO, 'tools', 'titan', 'own-processes.sh');
// macOS tmpdir is a symlink (/var → /private/var); paths are compared physically.
const scratch = () => realpathSync(mkdtempSync(join(tmpdir(), 'ownproc-')));
/** A do-nothing process whose command line carries `tag` (ps shows argv), started in `cwd`. */
function idle(cwd, tag) {
  return new Promise((ok, bad) => {
    const child = spawn(process.execPath, ['-e', "console.log('up');setInterval(()=>{},1000)", tag], { cwd, stdio: ['ignore', 'pipe', 'inherit'] });
    child.stdout.once('data', () => ok(child));   // wait until it is really running
    child.once('error', bad);
  });
}
const alive = (child) => { try { process.kill(child.pid, 0); return true; } catch { return false; } };
const settle = () => new Promise((r) => setTimeout(r, 400));   // let a SIGTERM be delivered and reaped
const reap = (...children) => children.forEach((c) => { if (alive(c)) c.kill('SIGKILL'); });
/** Non-comment lines of a gate script (comments may still describe the old behaviour). */
const live = (src) => src.split('\n').filter((l) => !/^\s*#/.test(l));
const readTitan = (f) => readFileSync(join(REPO, 'tools', 'titan', f), 'utf8');

// A fake daemon is an idle process whose argv ends the way a real one's does
// (`org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.6.1`, read off the live
// 9.6.1 daemon with ps). Its log, under a THROWAWAY GRADLE_USER_HOME, carries the
// VERBATIM 9.6.1 `Received command` line (copied from a real daemon-<pid>.out.log)
// with only the currentDir swapped. A real host daemon has no log under the
// throwaway home, so it is foreign by construction — and TITAN_GRADLE_PIDS keeps
// every pin but the one discovery pin from even looking at it.
const DAEMON_ARGV = 'org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.6.1';
const received = (dir) => `2026-10-07T04:22:05.739+0200 [INFO] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Received command: Build{id=9186454d-3d9b-436b-a594-81ef017d8318, currentDir=${dir}}.\n`;
/** Write daemon `pid`'s 9.6.1 log under `home`, one Received line per served dir (none → an empty log). */
function daemonLog(home, pid, dirs) {
  const d = join(home, 'daemon', '9.6.1');
  mkdirSync(d, { recursive: true });
  writeFileSync(join(d, `daemon-${pid}.out.log`), dirs.map(received).join(''));
}
/** Run `kill_own_gradle_daemons <args>` hermetically; `only` narrows the scan to those children. */
function gradle(args, { checkout, home, only }) {
  const env = { ...process.env, GRADLE_USER_HOME: home };
  if (only) env.TITAN_GRADLE_PIDS = only.map((c) => c.pid).join(' ');
  return spawnSync('bash', ['-c', `PROJECT_ROOT="${checkout}"; source "${LIB}"; kill_own_gradle_daemons ${args}`], { encoding: 'utf8', env });
}
const summary = (r) => (r.stderr.match(/kill_own_gradle_daemons roots=\[[^\]]*\]: stopped \[([^\]]*)\] left \[([^\]]*)\]/) || []).slice(1);

test('gradle: a daemon that served only this checkout is stopped; another checkout\'s survives and is named', async () => {
  const checkout = scratch(), other = scratch(), home = scratch();
  const ours = await idle(tmpdir(), DAEMON_ARGV), foreign = await idle(tmpdir(), DAEMON_ARGV);
  try {
    // The two roots the old --stop pair ran from: the repo and the Android harness.
    daemonLog(home, ours.pid, [checkout, join(checkout, 'apps', 'android-harness')]);
    daemonLog(home, foreign.pid, [join(other, 'apps', 'android-harness')]);
    const r = gradle('', { checkout, home, only: [ours, foreign] });
    assert.equal(r.status, 0);
    assert.match(r.stderr, new RegExp(`gradle daemon pid ${foreign.pid} \\(9\\.6\\.1\\) served \\[${other}/apps/android-harness\\] — not only this checkout's, not touching it`));
    assert.deepEqual(summary(r), [String(ours.pid), String(foreign.pid)], 'the summary line names what was stopped and what was left');
    await settle();
    assert.equal(alive(ours), false, 'this checkout\'s daemon must be gone');
    assert.equal(alive(foreign), true, 'another checkout\'s daemon is not ours to stop');
  } finally { reap(ours, foreign); }
});

test('gradle: a mixed daemon, a sibling-prefix daemon, a nested-checkout daemon and a log-less daemon all survive', async () => {
  const checkout = scratch(), other = scratch(), home = scratch();
  // A git worktree nested under the checkout (.claude/worktrees/<name>) is ANOTHER checkout.
  const nested = join(checkout, '.claude', 'worktrees', 'w');
  mkdirSync(nested, { recursive: true }); writeFileSync(join(nested, '.git'), 'gitdir: elsewhere\n');
  const mixed = await idle(tmpdir(), DAEMON_ARGV), sibling = await idle(tmpdir(), DAEMON_ARGV);
  const inner = await idle(tmpdir(), DAEMON_ARGV), bare = await idle(tmpdir(), DAEMON_ARGV);
  const empty = await idle(tmpdir(), DAEMON_ARGV);
  try {
    daemonLog(home, mixed.pid, [checkout, other]);          // shared with a foreign checkout
    daemonLog(home, sibling.pid, [`${checkout}-x`]);        // a prefix of the path, not a child
    daemonLog(home, inner.pid, [join(nested, 'apps', 'android-harness')]);
    daemonLog(home, empty.pid, []);                         // a log that records no build proves nothing
    const r = gradle('', { checkout, home, only: [mixed, sibling, inner, bare, empty] });   // `bare` has no log at all
    assert.equal(r.status, 0);
    for (const c of [mixed, sibling, inner, bare, empty]) assert.match(r.stderr, new RegExp(`gradle daemon pid ${c.pid} .*not touching it`));
    assert.match(r.stderr, new RegExp(`pid ${bare.pid} \\(9\\.6\\.1\\) served \\[no log at `));
    assert.equal(summary(r)[0], '', 'nothing here is ours');
    await settle();
    for (const c of [mixed, sibling, inner, bare, empty]) assert.equal(alive(c), true, `pid ${c.pid} must survive`);
  } finally { reap(mixed, sibling, inner, bare, empty); }
});

test('gradle: with two roots, a daemon spanning both is stopped; one spanning a root and a foreign tree survives', async () => {
  const checkout = scratch(), lane = scratch(), other = scratch(), home = scratch();
  const both = await idle(tmpdir(), DAEMON_ARGV), half = await idle(tmpdir(), DAEMON_ARGV);
  try {
    daemonLog(home, both.pid, [join(checkout, 'apps', 'android-harness'), join(lane, 'apps', 'android-harness')]);
    daemonLog(home, half.pid, [join(lane, 'apps', 'android-harness'), other]);
    const r = gradle(`"${checkout}" "${lane}"`, { checkout, home, only: [both, half] });
    assert.equal(r.status, 0);
    assert.deepEqual(summary(r), [String(both.pid), String(half.pid)]);
    await settle();
    assert.equal(alive(both), false, 'every served dir is inside one of the given roots');
    assert.equal(alive(half), true, 'one served dir is outside every given root');
  } finally { reap(both, half); }
});

test('gradle (PLAN §8 step 7a): a PROJECT_ROOT + lane-tree daemon is left by the default call and stopped by the two-root call', async () => {
  // The measured shape of 8 of the 210 9.6.1 logs on this host (plan-skeptic round 2).
  const checkout = scratch(), lane = scratch(), home = scratch();
  const shared = await idle(tmpdir(), DAEMON_ARGV);
  try {
    daemonLog(home, shared.pid, [join(checkout, 'apps', 'android-harness'), join(lane, 'apps', 'android-harness')]);
    // gate-driver's own call: no arguments → PROJECT_ROOT alone.
    const alone = gradle('', { checkout, home, only: [shared] });
    assert.match(alone.stderr, new RegExp(`gradle daemon pid ${shared.pid} \\(9\\.6\\.1\\) served \\[.*${lane}/apps/android-harness.*\\] — not only this checkout's`));
    await settle();
    assert.equal(alive(shared), true, 'PROJECT_ROOT alone does not own a daemon a lane tree also used');
    // Step 7a's ONE call names both trees.
    const r = gradle(`"$PROJECT_ROOT" "${lane}"`, { checkout, home, only: [shared] });
    assert.deepEqual(summary(r), [String(shared.pid), '']);
    await settle();
    assert.equal(alive(shared), false);
  } finally { reap(shared); }
});

test('gradle: TITAN_GRADLE_PIDS only narrows the scan', async () => {
  const checkout = scratch(), home = scratch();
  const named = await idle(tmpdir(), DAEMON_ARGV), unnamed = await idle(tmpdir(), DAEMON_ARGV);
  try {
    // Both are ours by their logs; only the one in the list may be considered.
    daemonLog(home, named.pid, [checkout]); daemonLog(home, unnamed.pid, [checkout]);
    const r = gradle('', { checkout, home, only: [named] });
    assert.deepEqual(summary(r), [String(named.pid), '']);
    assert.doesNotMatch(r.stderr, new RegExp(`pid ${unnamed.pid}\\b`), 'a pid outside the list is not even named');
    await settle();
    assert.equal(alive(unnamed), true);
  } finally { reap(named, unnamed); }
});

test('gradle DISCOVERY (unrestricted scan): pgrep finds a daemon by its argv; any host daemon has no log here and is left', async () => {
  // The production path: no TITAN_GRADLE_PIDS. Safe with the real predicate
  // (a real daemon's log is not under the throwaway home); the mutation runs
  // skip this one test by name, because a predicate mutated to "ours" would
  // reach the host's real daemons through this scan.
  const checkout = scratch(), home = scratch();
  const ours = await idle(tmpdir(), DAEMON_ARGV);
  try {
    daemonLog(home, ours.pid, [checkout]);
    const r = gradle('', { checkout, home });
    assert.equal(summary(r)[0], String(ours.pid));
    await settle();
    assert.equal(alive(ours), false);
  } finally { reap(ours); }
});

test('gate-driver: stop_our_processes writes the Gradle helper\'s stopped/left lines into driver.log', async () => {
  // Behavioural pin of the CALL SITE: the driver's own `log()` and
  // `stop_our_processes()` are lifted verbatim out of gate-driver.sh and run
  // against a throwaway DRV_DIR, with the emulator/browser stops stubbed out.
  const src = readFileSync(join(REPO, 'tools', 'titan', 'gate-driver.sh'), 'utf8');
  const logFn = src.match(/^log\(\) \{.*\}$/m)?.[0];
  const stopFn = src.match(/^stop_our_processes\(\) \{\n[\s\S]*?\n\}$/m)?.[0];
  assert.ok(logFn && stopFn, 'gate-driver.sh still defines log() and stop_our_processes()');
  const checkout = scratch(), other = scratch(), home = scratch(), drv = scratch();
  const ours = await idle(tmpdir(), DAEMON_ARGV), foreign = await idle(tmpdir(), DAEMON_ARGV);
  try {
    daemonLog(home, ours.pid, [join(checkout, 'apps', 'android-harness')]);
    daemonLog(home, foreign.pid, [other]);
    // The scratch paths travel as ENVIRONMENT variables and the script reads them: nothing file-system
    // derived is interpolated into the shell text (CodeQL js/shell-command-injection-from-environment).
    const script = ['PROJECT_ROOT="$T_CHECKOUT"; DRV_DIR="$T_DRV"; LOG="$T_DRV/driver.log"', 'source "$T_LIB"',
      'kill_own_emulators() { :; }; kill_own_test_browsers() { :; }', logFn, stopFn, 'stop_our_processes'].join('\n');
    const r = spawnSync('bash', ['-c', script], { encoding: 'utf8', env: { ...process.env, GRADLE_USER_HOME: home, TITAN_GRADLE_PIDS: `${ours.pid} ${foreign.pid}`,
      T_CHECKOUT: checkout, T_DRV: drv, T_LIB: LIB } });
    assert.equal(r.status, 0, r.stderr);
    const log = readFileSync(join(drv, 'driver.log'), 'utf8');
    assert.match(log, new RegExp(`\\[gate-driver [0-9:]+\\] \\[own-processes\\] gradle daemon pid ${foreign.pid} \\(9\\.6\\.1\\) served \\[${other}\\] — not only this checkout's, not touching it`));
    assert.match(log, new RegExp(`\\[gate-driver [0-9:]+\\] \\[own-processes\\] kill_own_gradle_daemons roots=\\[${checkout}\\]: stopped \\[${ours.pid}\\] left \\[${foreign.pid}\\]`));
    await settle();
    assert.equal(alive(ours), false);
    assert.equal(alive(foreign), true);
  } finally { reap(ours, foreign); }
});

test('gate-driver.sh has no live `./gradlew --stop`; its one Gradle stop is the ownership-checked helper', () => {
  const driver = live(readTitan('gate-driver.sh'));
  assert.equal(driver.filter((l) => /gradlew[^\n]*--stop/.test(l)).length, 0, 'no `./gradlew --stop` (every daemon of the version)');
  assert.equal(driver.filter((l) => /\bkill_own_gradle_daemons\b/.test(l)).length, 1, 'one ownership-checked Gradle stop');
});
