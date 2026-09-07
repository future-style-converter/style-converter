#!/usr/bin/env node
// Pins for the guard functions in ./test-all.sh (retrospective lane R8a):
//
//   · _assert_platform_columns  — column presence (A9#0): exit 7 when a
//                                 non-skipped platform captured fewer PNGs
//                                 than the IR's expected count
//   · _gate_set_fixtures        — the --gate-set list parser (A12#3)
//   · _gate_fixture_has_baselines — the BASELINE downgrade probe (A12#3)
//   · _fixture_is_seize_only    — the `_capture.seizeOnly` header (A11#16)
//   · _adb_bounded              — the adb watchdog (A11#17)
//
// test-all.sh cannot be sourced (it converts, builds and captures on load),
// so each function is EXTRACTED by name from the script text and evaluated
// in a fresh `bash` with stub `err`/`log` — the same bash 3.2 the script
// targets on macOS. If a function is renamed or its closing brace moves off
// column 0 the extraction fails loudly rather than testing stale text.
//
// Run via `node --test tools/visual/test-all-guards.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, existsSync, mkdirSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, '..', '..');
const SCRIPT = readFileSync(join(REPO, 'test-all.sh'), 'utf8');

/** Slice `name() { … }` (closing brace at column 0) out of the script text. */
function bashFn(name) {
  const start = SCRIPT.indexOf(`\n${name}() {`);
  assert.ok(start >= 0, `${name}() not found in test-all.sh`);
  const end = SCRIPT.indexOf('\n}\n', start);
  assert.ok(end > start, `${name}() has no column-0 closing brace`);
  return SCRIPT.slice(start + 1, end + 2);
}

/** Run `body` in bash with the named functions defined and stub loggers. */
function bash(fnNames, body, env = {}) {
  const prelude = [
    'set -uo pipefail',
    'err() { echo "[err] $1" >&2; }',
    'warn() { echo "[warn] $1" >&2; }',
    'log() { echo "[log] $1"; }',
    ...fnNames.map(bashFn),
  ].join('\n');
  const r = spawnSync('bash', ['-c', `${prelude}\n${body}`], { encoding: 'utf8', env: { ...process.env, ...env } });
  return { status: r.status, stdout: r.stdout, stderr: r.stderr };
}

test('test-all.sh parses (bash -n)', () => {
  const r = spawnSync('bash', ['-n', join(REPO, 'test-all.sh')], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
});

test('no bare "$ADB" call survives outside the bounded wrapper (A11#17)', () => {
  // Every adb use must go through `adb()` so the watchdog and the -s pin
  // apply. The only legitimate "$ADB" references are the resolution loop, the
  // wrapper itself, and prose in a warn message.
  const bare = SCRIPT.match(/"\$ADB" (devices|shell|pull|emu|logcat|wait-for-device)/g) ?? [];
  assert.deepEqual(bare, [], 'bare "$ADB" call(s) bypass the watchdog');
  // …and the wrapper must hand _adb_bounded the resolved BINARY, never the
  // bare word `adb`: bash resolves functions before $PATH, so a bare `adb`
  // there re-enters the wrapper — unbounded recursion (the exact garble the
  // resumed lane found in the killed attempt's draft).
  assert.match(SCRIPT, /_adb_bounded "\$secs" "\$ADB" \$\{ADB_SERIAL:\+-s "\$ADB_SERIAL"\} "\$@"/,
    'adb() must call _adb_bounded with "$ADB" and the optional -s pin');
});

// ── _assert_platform_columns (A9#0) ─────────────────────────────────────────

const COLS = (env) => bash(['_assert_platform_columns'], '_assert_platform_columns; echo "rc=$?"', env);

test('column presence: three full columns → 0', () => {
  const r = COLS({ COMPONENT_COUNT: '109', CAPTURED_IOS: '109', CAPTURED_ANDROID: '109', CAPTURED_WEB: '109' });
  assert.match(r.stdout, /rc=0/);
  assert.equal(r.stderr, '');
});

test('column presence: an EMPTY Android column without SKIP_ANDROID → 7, naming the platform', () => {
  // The audit's exact hole: CAPTURED_ANDROID="" printed "– Android: skipped".
  const r = COLS({ COMPONENT_COUNT: '109', CAPTURED_IOS: '109', CAPTURED_ANDROID: '', CAPTURED_WEB: '109' });
  assert.match(r.stdout, /rc=7/);
  assert.match(r.stderr, /Android: captured 0 of 109 components and SKIP_ANDROID was not set/);
});

test('column presence: the same empty column with SKIP_ANDROID=1 → 0 (a deliberate skip is a result)', () => {
  const r = COLS({ COMPONENT_COUNT: '109', CAPTURED_IOS: '109', CAPTURED_ANDROID: '', CAPTURED_WEB: '109', SKIP_ANDROID: '1' });
  assert.match(r.stdout, /rc=0/);
});

test('column presence: a SHORT column (10 of 142) → 7', () => {
  // colhole2 — Android 10/142 passed the comparator with 16 warnings.
  const r = COLS({ COMPONENT_COUNT: '142', CAPTURED_IOS: '142', CAPTURED_ANDROID: '10', CAPTURED_WEB: '142' });
  assert.match(r.stdout, /rc=7/);
  assert.match(r.stderr, /Android: captured 10 of 142/);
});

test('column presence: an OVER-long column is also a mismatch (stale PNGs from a previous fixture)', () => {
  const r = COLS({ COMPONENT_COUNT: '8', CAPTURED_IOS: '8', CAPTURED_ANDROID: '42', CAPTURED_WEB: '8' });
  assert.match(r.stdout, /rc=7/);
});

test('column presence: leading-zero counts are read base-10, and every short platform is named', () => {
  const r = COLS({ COMPONENT_COUNT: '8', CAPTURED_IOS: '008', CAPTURED_ANDROID: '', CAPTURED_WEB: '3' });
  assert.match(r.stdout, /rc=7/);
  assert.doesNotMatch(r.stderr, /iOS:/);                  // 008 == 8
  assert.match(r.stderr, /Android: captured 0 of 8/);
  assert.match(r.stderr, /web: captured 3 of 8 components and SKIP_WEB was not set/);
});

test('column presence guards the baseline SYNC: UPDATE_BASELINE=1 asserts whole columns before --update-baseline is passed', () => {
  // syncBaseline() upserts and never deletes, so a refresh from a run with a
  // short column would splice this run's PNGs beside the missed components'
  // old ones. The assertion must run BEFORE the comparator is armed to write.
  const guard = SCRIPT.indexOf('if ! _assert_platform_columns; then');
  const arm = SCRIPT.indexOf('COMPARE_ARGS+=(--update-baseline)');
  const def = SCRIPT.indexOf('\n_assert_platform_columns() {');
  assert.ok(guard > 0 && arm > 0 && def > 0, 'guard, arm and definition must all exist');
  assert.ok(def < guard, 'the function must be defined before the guard calls it (bash resolves at call time)');
  assert.ok(guard < arm, '--update-baseline must only be added after the column assertion passed');
  assert.match(SCRIPT.slice(guard, arm), /exit 7/, 'the refusal is exit 7, the column-presence code');
});

// ── _gate_set_fixtures + _gate_fixture_has_baselines (A12#3) ────────────────

test('gate-set list parser drops comments and blanks, keeps order, ignores trailing comments', () => {
  const dir = mkdtempSync(join(tmpdir(), 'gateset-'));
  const list = join(dir, 'list.txt');
  writeFileSync(list, [
    '# header comment',
    '',
    'fixtures/visual-test.json',
    '   fixtures/composition-test.json   # trailing note',
    '',
    '  # indented comment',
    'fixtures/combinations/opacity-blend.json',
  ].join('\n'));                                           // no trailing newline on purpose — last line must still count
  const r = bash(['_gate_set_fixtures'], `_gate_set_fixtures "${list}"`);
  assert.equal(r.status, 0, r.stderr);
  assert.deepEqual(r.stdout.trim().split('\n'), [
    'fixtures/visual-test.json',
    'fixtures/composition-test.json',
    'fixtures/combinations/opacity-blend.json',
  ]);
});

test('the committed gate-fixtures.txt lists only fixtures that exist, visual-test first', () => {
  const r = bash(['_gate_set_fixtures'], `_gate_set_fixtures "${join(REPO, 'tools/visual/gate-fixtures.txt')}"`);
  const fixtures = r.stdout.trim().split('\n');
  assert.equal(fixtures[0], 'fixtures/visual-test.json', 'the 327-net stays first');
  assert.ok(fixtures.length >= 7, `expected the 7 retrospective fixtures, got ${fixtures.length}`);
  for (const f of fixtures) assert.ok(existsSync(join(REPO, f)), `${f} listed but missing on disk`);
});

test('baseline probe: visual-test.json has committed baselines; a made-up fixture does not', () => {
  const baselineDir = join(REPO, 'tools/visual/baseline');
  const yes = bash(['_gate_fixture_has_baselines'],
    `_gate_fixture_has_baselines "${join(REPO, 'fixtures/visual-test.json')}" "${baselineDir}"; echo "rc=$?"`);
  assert.match(yes.stdout, /rc=0/);
  const dir = mkdtempSync(join(tmpdir(), 'gateset-'));
  const fx = join(dir, 'nope.json');
  writeFileSync(fx, JSON.stringify({ components: { No_Such_Component_XYZ: { properties: { width: '1px' } } } }));
  const no = bash(['_gate_fixture_has_baselines'], `_gate_fixture_has_baselines "${fx}" "${baselineDir}"; echo "rc=$?"`);
  assert.match(no.stdout, /rc=1/);
});

// ── _fixture_is_seize_only (A11#16) ─────────────────────────────────────────

test('seize-only header: {"_capture":{"seizeOnly":true}} → 0; absent, string "true", or missing file → 1', () => {
  const dir = mkdtempSync(join(tmpdir(), 'seize-'));
  const cases = {
    yes: { _capture: { seizeOnly: true }, components: {} },
    plain: { components: {} },
    stringy: { _capture: { seizeOnly: 'true' }, components: {} },   // only the boolean counts — no truthy-string drift
    other: { _capture: { note: 'x' }, components: {} },
  };
  for (const [k, doc] of Object.entries(cases)) writeFileSync(join(dir, `${k}.json`), JSON.stringify(doc));
  const rc = (f) => bash(['_fixture_is_seize_only'], `_fixture_is_seize_only "${f}"; echo "rc=$?"`).stdout;
  assert.match(rc(join(dir, 'yes.json')), /rc=0/);
  assert.match(rc(join(dir, 'plain.json')), /rc=1/);
  assert.match(rc(join(dir, 'stringy.json')), /rc=1/);
  assert.match(rc(join(dir, 'other.json')), /rc=1/);
  assert.match(rc(join(dir, 'missing.json')), /rc=1/);
});

test('the committed motion fixture carries the header — the guard has a live carrier (A11#16)', () => {
  // fixtures/fidelity/motion/keyframes-basic.json is the fixture the audit
  // measured at 22/24 "unexpected" pairs under a live clock. If someone
  // regenerates it without the header, this is the test that says so.
  const fx = join(REPO, 'fixtures/fidelity/motion/keyframes-basic.json');
  assert.match(bash(['_fixture_is_seize_only'], `_fixture_is_seize_only "${fx}"; echo "rc=$?"`).stdout, /rc=0/);
});

test('the guard block refuses a seize-only fixture only when CAPTURE_ANIMATION_TIME is unset', () => {
  // The guard's condition, verbatim from the script, over the function.
  const dir = mkdtempSync(join(tmpdir(), 'seize-'));
  const fx = join(dir, 'motion.json');
  writeFileSync(fx, JSON.stringify({ _capture: { seizeOnly: true }, components: {} }));
  const body = `if [[ -z "\${CAPTURE_ANIMATION_TIME:-}" ]] && _fixture_is_seize_only "${fx}"; then echo REFUSED; else echo PASS; fi`;
  assert.match(bash(['_fixture_is_seize_only'], body).stdout, /REFUSED/);
  assert.match(bash(['_fixture_is_seize_only'], body, { CAPTURE_ANIMATION_TIME: '0.5' }).stdout, /PASS/);
});

// ── _adb_bounded (A11#17) ───────────────────────────────────────────────────

test('adb watchdog: a hung command is killed, returns 124, says so, and marks the hang flag', () => {
  const dir = mkdtempSync(join(tmpdir(), 'adbwd-'));
  const flag = join(dir, 'hang');
  const t0 = Date.now();
  const r = bash(['_adb_bounded'], `_adb_bounded 1 sleep 30; echo "rc=$?"`, { ADB_HANG_FLAG: flag });
  const secs = (Date.now() - t0) / 1000;
  assert.match(r.stdout, /rc=124/);
  assert.match(r.stderr, /adb hung for >1s and was killed: sleep 30/);
  assert.ok(existsSync(flag), 'hang flag must be touched so `$(…)` callers can detect the kill');
  assert.ok(secs < 10, `watchdog should fire in ~1s, took ${secs}s`);   // 898 s was the measured unbounded hang
});

test('adb watchdog: a fast command passes its own exit status through, no flag', () => {
  const dir = mkdtempSync(join(tmpdir(), 'adbwd-'));
  const flag = join(dir, 'hang');
  const ok = bash(['_adb_bounded'], `_adb_bounded 5 true; echo "rc=$?"`, { ADB_HANG_FLAG: flag });
  assert.match(ok.stdout, /rc=0/);
  const three = bash(['_adb_bounded'], `_adb_bounded 5 sh -c 'exit 3'; echo "rc=$?"`, { ADB_HANG_FLAG: flag });
  assert.match(three.stdout, /rc=3/);
  assert.ok(!existsSync(flag), 'no hang → no flag');
  assert.equal(ok.stderr, '');
});

test('adb watchdog: cancelling the watchdog after a fast command reaps its sleeper (no orphan `sleep`)', () => {
  // The poll loop makes one bounded call per second for minutes. With the
  // plain provision-devices.sh pattern (`kill -KILL $wd` on the watchdog
  // subshell) every call left its `sleep <secs>` running as an orphan to
  // full term — ~60 stray sleepers at any moment. The wrapper's watchdog
  // traps TERM and kills its own sleep; a unique, recognisable duration
  // makes the sleeper findable via pgrep. Proven able to fail: swapping the
  // trap line for the plain pattern leaves `sleep 7.31` alive here (and,
  // because that pattern's sleeper inherits this spawn's pipes, spawnSync
  // waits the full 7.31 s for it — the short duration keeps the mutation
  // cheap to observe).
  const dir = mkdtempSync(join(tmpdir(), 'adbwd-'));
  const r = bash(['_adb_bounded'], `_adb_bounded 7.31 true; echo "rc=$?"; sleep 0.2; pgrep -f "^sleep 7[.]31$" | wc -l | tr -d " "`,
    { ADB_HANG_FLAG: join(dir, 'hang') });
  assert.match(r.stdout, /rc=0/);
  assert.equal(r.stdout.trim().split('\n').pop(), '0', `orphan sleeper(s) left behind:\n${r.stdout}`);
  spawnSync('pkill', ['-f', '^sleep 7[.]31$']);               // hygiene if the assertion above failed
});

test('adb watchdog: stdout of the bounded command reaches a $(…) caller', () => {
  // The poll loop captures `adb shell ls | wc -l` — the wrapper must not eat
  // the command's output while it babysits the pid.
  const r = bash(['_adb_bounded'], `out=$(_adb_bounded 5 echo hello); echo "got=$out"`, { ADB_HANG_FLAG: '/nonexistent/flag' });
  assert.match(r.stdout, /got=hello/);
});

test('adb() wrapper: invokes the resolved binary, pins -s only when ADB_SERIAL is set, passes args through', () => {
  // A fake ADB that echoes its argv stands in for the binary, so the exact
  // command line the device would see is observable. Without the -s pin,
  // "more than one device/emulator" fails every call into an empty column
  // (retrospective A9#0); with the bare word `adb` in the wrapper the call
  // would recurse into the wrapper instead of reaching the binary at all.
  const dir = mkdtempSync(join(tmpdir(), 'adbwrap-'));
  const fake = join(dir, 'fake-adb');
  writeFileSync(fake, '#!/bin/bash\necho "argv: $*"\n');
  chmodSync(fake, 0o755);
  const env = { ADB: fake, ADB_TIMEOUT: '5', ADB_PULL_TIMEOUT: '5', ADB_HANG_FLAG: join(dir, 'hang') };
  const pinned = bash(['_adb_bounded', 'adb'], 'adb shell ls /sdcard', { ...env, ADB_SERIAL: 'emulator-5554' });
  assert.equal(pinned.stdout.trim(), 'argv: -s emulator-5554 shell ls /sdcard', pinned.stderr);
  const unpinned = bash(['_adb_bounded', 'adb'], 'adb devices', { ...env, ADB_SERIAL: '' });
  assert.equal(unpinned.stdout.trim(), 'argv: devices', unpinned.stderr);
  // A pull gets the pull-specific bound; anything else the general one. The
  // watchdog's `sleep` is observable only via timing, so prove the routing by
  // giving `pull` a bound too short for the fake to finish.
  writeFileSync(fake, '#!/bin/bash\nsleep 3; echo "argv: $*"\n');
  const slowPull = bash(['_adb_bounded', 'adb'], 'adb pull /x /y; echo "rc=$?"', { ...env, ADB_PULL_TIMEOUT: '1', ADB_TIMEOUT: '10' });
  assert.match(slowPull.stdout, /rc=124/);
  assert.match(slowPull.stderr, /adb hung for >1s and was killed/);
});

// ── the --gate-set driver loop (A12#3) ──────────────────────────────────────
//
// The loop runs `"$0" <fixture>` per line, so it can be driven end-to-end by
// evaluating the extracted block with `$0` pointing at a STUB child that
// records its arguments and exits with a per-fixture code — no converter, no
// devices. bash -c's positional form (`bash -c script name arg…`) sets $0.

/** Slice the column-0 `if … fi` block that starts with `startLine`. */
function bashBlock(startLine) {
  const start = SCRIPT.indexOf(`\n${startLine}`);
  assert.ok(start >= 0, `block starting "${startLine}" not found in test-all.sh`);
  const end = SCRIPT.indexOf('\nfi\n', start);
  assert.ok(end > start, 'block has no column-0 `fi`');
  return SCRIPT.slice(start + 1, end + 4);
}

function gateSetHarness() {
  const root = mkdtempSync(join(tmpdir(), 'gateset-drv-'));
  // Scratch repo shape the block expects under dirname($0): a baseline dir
  // and the fixtures it will probe.
  mkdirSync(join(root, 'tools', 'visual', 'baseline'), { recursive: true });
  mkdirSync(join(root, 'fixtures'));
  writeFileSync(join(root, 'tools', 'visual', 'baseline', 'iOS__000_Has.png'), '');   // only the NAME matters to the probe
  writeFileSync(join(root, 'fixtures', 'has.json'), JSON.stringify({ components: { Has: { properties: {} } } }));
  writeFileSync(join(root, 'fixtures', 'none.json'), JSON.stringify({ components: { Nope: { properties: {} } } }));
  writeFileSync(join(root, 'fixtures', 'fail.json'), JSON.stringify({ components: { Fail: { properties: {} } } }));
  // The stub child: echoes what it was given, exit 4 for the "fail" fixture.
  const stub = join(root, 'stub.sh');
  writeFileSync(stub, '#!/bin/bash\necho "child fixture=$1 BASELINE=${BASELINE:-unset}"\ncase "$1" in *fail*) exit 4;; esac\nexit 0\n');
  chmodSync(stub, 0o755);                                    // the driver execs it by path
  const list = join(root, 'list.txt');
  writeFileSync(list, '# scratch gate set\nfixtures/has.json\nfixtures/none.json\nfixtures/fail.json\n');
  const script = [
    'set -euo pipefail',
    bashFn('_gate_set_fixtures'),
    bashFn('_gate_fixture_has_baselines'),
    bashBlock('if [[ "${1:-}" == "--gate-set" ]]; then'),
    'echo "fell through: not --gate-set"',
  ].join('\n');
  const run = (env, args = ['--gate-set']) => {
    const r = spawnSync('bash', ['-c', script, stub, ...args], { encoding: 'utf8', env: { ...process.env, GATE_FIXTURES: list, ...env } });
    return { status: r.status, stdout: r.stdout, stderr: r.stderr };
  };
  return { root, list, run };
}

test('--gate-set: runs every listed fixture, downgrades BASELINE only where no baseline exists, re-raises the first non-zero exit', () => {
  const h = gateSetHarness();
  const r = h.run({ BASELINE: '1' });
  assert.equal(r.status, 4, `${r.stdout}\n${r.stderr}`);          // fail.json's 4, re-raised
  assert.match(r.stdout, /child fixture=fixtures\/has\.json BASELINE=1/);        // has a baseline → BASELINE kept
  assert.match(r.stdout, /child fixture=fixtures\/none\.json BASELINE=0/);       // none → downgraded
  assert.match(r.stderr, /fixtures\/none\.json has NO committed baseline .* running it WITHOUT --baseline/);
  assert.match(r.stdout, /gate set summary — 3 fixture\(s\)/);
  assert.match(r.stdout, /exit 0  fixtures\/has\.json\n/);
  assert.match(r.stdout, /exit 0  fixtures\/none\.json   \(gate-only: no committed baseline\)/);
  assert.match(r.stdout, /exit 4  fixtures\/fail\.json/);
  assert.doesNotMatch(r.stdout, /fell through/);
});

test('--gate-set: without BASELINE nothing is downgraded and a clean set exits 0', () => {
  const h = gateSetHarness();
  writeFileSync(h.list, 'fixtures/has.json\nfixtures/none.json\n');
  const r = h.run({});
  assert.equal(r.status, 0, `${r.stdout}\n${r.stderr}`);
  assert.doesNotMatch(r.stderr, /NO committed baseline/);
  assert.match(r.stdout, /child fixture=fixtures\/none\.json BASELINE=0/);   // BASELINE unset → child sees the explicit 0
});

test('--gate-set: an empty or missing list is exit 2 — a gate set that runs nothing must not pass', () => {
  const h = gateSetHarness();
  writeFileSync(h.list, '# only comments\n\n');
  assert.equal(h.run({}).status, 2);
  assert.match(h.run({}).stderr, /lists no fixtures/);
  assert.equal(h.run({ GATE_FIXTURES: join(h.root, 'absent.txt') }).status, 2);
});

test('--gate-set: a plain fixture argument does not enter the driver', () => {
  const h = gateSetHarness();
  const r = h.run({}, ['fixtures/has.json']);
  assert.match(r.stdout, /fell through/);
});
