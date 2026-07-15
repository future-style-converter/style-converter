#!/usr/bin/env node
//
// tools/titan/feed-android.mjs
//
// HOST-SIDE feeder for the TITAN Android inbox capture loop (this lane's
// producer). It reuses an ALREADY-BOOTED emulator — it never boots/erases one —
// installs+launches the harness in titan-inbox mode, then per IR fixture:
//   push the IR JSON into the device inbox  →  poll the on-device screenshot
//   dir until the expected per-component PNGs appear  →  pull them to --out
//   (verifying each parses; retry once on the adb-pull truncation flake)  →
//   clear the on-device PNGs  →  next fixture.
// The expected filenames come from the fed IR document itself (feed-lib.mjs's
// expectedPngNames, a mirror of the on-device flatten+naming), and are pulled
// with the SAME `<idx>_<safeName>.png` names inject-wpt-block.mjs globs for, so
// captures drop straight into the existing compare pipeline.
//
// TITAN Round 3 — `--composed`: launch the app with titanComposed=true so it
// renders the WHOLE fed doc COMPOSED on one ref-matching canvas and writes ONE
// `<safe(testKey)>.png` per test (name derived from the fixture basename, the
// same key the app derives from the inbox filename). The feeder then waits for
// and pulls that single PNG instead of the per-component `%03d_<safe>.png` set;
// tools/titan/inject-wpt-block.mjs's diffComposedVsRef diffs it DIRECTLY vs the
// browser-ref (no stitch). Without the flag, the legacy per-component path is
// byte-for-byte unchanged.
//
// Usage:
//   node tools/titan/feed-android.mjs --fixtures <dir|a.json,b.json> --out <dir> \
//       [--timeout-per-fixture 30] [--udid emulator-5554] [--skip-install] [--composed]
//
// Contract: the device must already be running. If none is attached the feeder
// FAILS LOUDLY telling the caller to boot one (per the lane's BACKGROUND_MODE
// rule — this tool must not create/destroy devices).

import { execFileSync, execSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs, expectedPngNames, composedPngName, pngIsValid } from './feed-lib.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');

// On-device layout (mirrors ScreenshotManager: getExternalFilesDir(null)+…).
const PKG = 'com.styleconverter.test';
const ACTIVITY = `${PKG}/.MainActivity`;
const INBOX_DIR = `/sdcard/Android/data/${PKG}/files/inbox`;
const SHOT_DIR = `/sdcard/Android/data/${PKG}/files/test_screenshots`;

const log = (m) => process.stderr.write(`[feed-android] ${m}\n`);

// ── Device plumbing ──────────────────────────────────────────────────────────

/** Locate the adb binary the same way test-all.sh does (SDK env vars first,
 *  then the canonical macOS SDK path, then PATH). */
function findAdb() {
  const candidates = [
    process.env.ANDROID_HOME && `${process.env.ANDROID_HOME}/platform-tools/adb`,
    process.env.ANDROID_SDK_ROOT && `${process.env.ANDROID_SDK_ROOT}/platform-tools/adb`,
    `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`,
  ].filter(Boolean);
  for (const c of candidates) if (existsSync(c)) return c;
  try { return execSync('command -v adb', { encoding: 'utf8' }).trim(); } catch { /* fall through */ }
  throw new Error('adb not found — set ANDROID_HOME or put adb on PATH');
}

/** Return connected device serials (lines ending in a TAB + "device"). */
function listDevices(adb) {
  const out = execFileSync(adb, ['devices'], { encoding: 'utf8' });
  return out.split('\n').slice(1)
    .map((l) => l.trim()).filter(Boolean)
    .filter((l) => /\tdevice$/.test(l))
    .map((l) => l.split('\t')[0]);
}

/** Bind a factory that runs `adb -s <serial> …`, returning stdout as text. */
function makeAdb(adb, serial) {
  return (args, opts = {}) =>
    execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', ...opts });
}

// ── Per-fixture steps ─────────────────────────────────────────────────────────

/** Snapshot the on-device screenshot dir as a Set of filenames (empty on a
 *  missing dir — `ls` exits non-zero, which execFileSync throws on). */
function deviceShotNames(adbx) {
  try {
    return new Set(adbx(['shell', 'ls', SHOT_DIR]).split('\n').map((s) => s.trim()).filter(Boolean));
  } catch { return new Set(); }
}

/** Block until every expected PNG name exists on-device, or the timeout
 *  elapses. Returns { done, present }. Pure polling — no pulling here. */
async function waitForPngs(adbx, expected, timeoutSec) {
  const deadline = Date.now() + timeoutSec * 1000;
  const want = new Set(expected);
  for (;;) {
    const have = deviceShotNames(adbx);
    if ([...want].every((n) => have.has(n))) return { done: true, present: have };
    if (Date.now() >= deadline) return { done: false, present: have };
    await new Promise((r) => setTimeout(r, 150)); // poll cadence
  }
}

/** Pull one PNG to the host and verify it decodes; retry the pull ONCE on the
 *  known adb truncation flake. `remoteName` is the on-device filename (device
 *  sanitiser rule) and `localName` is the host output name (compare-pipeline
 *  `safe()` rule) — they differ only when a component name carries a dot, in
 *  which case the pull RENAMES the device file to the compare-glob name.
 *  Returns true iff a valid PNG landed in out. */
function pullVerified(adbx, remoteName, localName, outDir) {
  const remote = `${SHOT_DIR}/${remoteName}`;
  const local = path.join(outDir, localName);
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      adbx(['pull', remote, local]);
      if (pngIsValid(readFileSync(local))) return true;
      log(`  pulled ${remoteName} failed PNG parse (attempt ${attempt + 1}) — retrying`);
    } catch (e) {
      log(`  pull ${remoteName} errored (attempt ${attempt + 1}): ${e.message}`);
    }
  }
  return false;
}

// ── Fixture list expansion ────────────────────────────────────────────────────

/** Expand --fixtures (a directory OR a comma-separated list) into a sorted
 *  list of absolute IR .json paths. Sorting makes the feed order stable. */
function expandFixtures(arg) {
  if (existsSync(arg) && statSync(arg).isDirectory()) {
    return readdirSync(arg).filter((f) => f.endsWith('.json')).sort()
      .map((f) => path.resolve(arg, f));
  }
  return arg.split(',').map((s) => path.resolve(s.trim())).filter(Boolean);
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (!opts.fixtures || !opts.out) {
    log('usage: feed-android.mjs --fixtures <dir|a.json,b.json> --out <dir> [--timeout-per-fixture N] [--udid S] [--skip-install] [--composed]');
    process.exit(2);
  }
  const fixtures = expandFixtures(opts.fixtures);
  if (fixtures.length === 0) { log(`no fixtures found under ${opts.fixtures}`); process.exit(2); }
  mkdirSync(opts.out, { recursive: true });

  const adb = findAdb();
  const devices = listDevices(adb);
  if (devices.length === 0) {
    log('FATAL: no Android device/emulator attached. Boot one first (this feeder never boots devices).');
    process.exit(3);
  }
  const serial = opts.udid || devices[0];
  if (!devices.includes(serial)) { log(`FATAL: requested --udid ${serial} not attached (${devices.join(', ')})`); process.exit(3); }
  const adbx = makeAdb(adb, serial);
  log(`device=${serial}  fixtures=${fixtures.length}  out=${opts.out}  timeout=${opts.timeoutPerFixture}s  composed=${opts.composed}`);

  // Install (unless reusing the already-installed app) so the feeder is
  // self-contained. Incremental Gradle → a no-change reinstall is fast.
  if (!opts.skipInstall) {
    log('installing APK (:app:installDebug)…');
    const env = { ...process.env };
    if (!env.JAVA_HOME) {
      const jdk = `${process.env.HOME}/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`;
      if (existsSync(jdk)) env.JAVA_HOME = jdk;
    }
    // Route Gradle's stdout to OUR stderr (fd 2) so it can't pollute the
    // machine-readable JSON summary we print to stdout at the end.
    execFileSync('./gradlew', [':app:installDebug', '--quiet'],
      { cwd: path.join(REPO_ROOT, 'apps', 'android-harness'), env, stdio: ['ignore', 2, 2] });
  }

  // Reset device state BEFORE launching so the poll loop starts clean
  // (idempotence: a second run can't see the first run's stale files).
  adbx(['shell', 'am', 'force-stop', PKG]);
  adbx(['shell', 'rm', '-rf', INBOX_DIR, SHOT_DIR]);
  adbx(['shell', 'mkdir', '-p', INBOX_DIR, SHOT_DIR]);
  try { adbx(['logcat', '-c']); } catch { /* logcat clear is best-effort */ }

  // Launch in inbox mode. Match the shared 390×844 @160dpi capture canvas.
  // Composed mode layers `--ez titanComposed true` on top: same inbox poll,
  // but the app composes the whole doc onto one canvas → one PNG per test.
  adbx(['shell', 'wm', 'size', '390x844']);
  adbx(['shell', 'wm', 'density', '160']);
  const launchArgs = ['shell', 'am', 'start', '-n', ACTIVITY, '--ez', 'titanInbox', 'true'];
  if (opts.composed) launchArgs.push('--ez', 'titanComposed', 'true');
  adbx(launchArgs);
  log(`launched in titan-${opts.composed ? 'composed' : 'inbox'} mode; verifying marker…`);
  // Grep the run-config marker (same verification philosophy as test-all's
  // animationTime/forceState gate) — proves the app really entered the mode.
  // In composed mode we additionally require titanComposed=true so a stale
  // per-component launch can't masquerade as composed.
  const markerRe = opts.composed ? /titanInbox=true titanComposed=true/ : /titanInbox=true/;
  let marked = false;
  for (let i = 0; i < 40 && !marked; i++) {
    try { marked = markerRe.test(adbx(['logcat', '-d'])); } catch { /* retry */ }
    if (!marked) await new Promise((r) => setTimeout(r, 250));
  }
  log(marked ? `verified: app logged ${opts.composed ? 'titanComposed=true' : 'titanInbox=true'}`
             : `WARNING: never saw ${opts.composed ? 'titanComposed=true' : 'titanInbox=true'} marker (continuing)`);

  // Feed each fixture and record a result row.
  const results = [];
  for (let i = 0; i < fixtures.length; i++) {
    const fx = fixtures[i];
    const base = path.basename(fx);
    let doc;
    try { doc = JSON.parse(readFileSync(fx, 'utf8')); }
    catch (e) { results.push({ fixture: base, ok: false, error: `bad IR json: ${e.message}` }); continue; }
    // Both modes yield a list of { deviceFile, hostFile } captures: deviceFile
    // is the on-device name the poll waits for; hostFile is the compare-glob
    // name the pull writes to --out (they differ only for a dotted name).
    //   • Composed: ONE PNG named for the WPT test key (derived from the
    //     fixture basename, identical to what the app derives from the inbox
    //     filename). The Android composed rule keeps the dot too, so device ==
    //     host here.
    //   • Per-component: one PNG per flattened component (expectedPngNames
    //     already returns the decoupled {deviceFile, hostFile} pairs).
    const expected = opts.composed
      ? (() => { const n = composedPngName(base); return [{ deviceFile: n, hostFile: n }]; })()
      : expectedPngNames(doc);
    const wantDevice = expected.map((e) => e.deviceFile);
    const t0 = Date.now();
    // Push into the inbox under a unique, FIFO-ordered name (index prefix
    // guarantees uniqueness even if two fixtures share a basename).
    adbx(['push', fx, `${INBOX_DIR}/${String(i).padStart(4, '0')}-${base}`]);
    const { done, present } = await waitForPngs(adbx, wantDevice, opts.timeoutPerFixture);
    if (!done) {
      results.push({ fixture: base, ok: false, error: 'timeout',
        want: wantDevice, got: [...present], elapsedSec: (Date.now() - t0) / 1000 });
      log(`  ${base}: TIMEOUT (${present.size}/${expected.length} PNGs) — continuing`);
      adbx(['shell', 'rm', '-f', `${SHOT_DIR}/*`]); // clear partials for next fixture
      continue;
    }
    await new Promise((r) => setTimeout(r, 150)); // settle before pulling
    const pulled = [];
    const bad = [];
    for (const e of expected) {
      (pullVerified(adbx, e.deviceFile, e.hostFile, opts.out) ? pulled : bad).push(e.hostFile);
    }
    const elapsedSec = (Date.now() - t0) / 1000;
    // Clear this fixture's on-device PNGs so the next fixture's poll is clean.
    adbx(['shell', 'rm', '-f', `${SHOT_DIR}/*`]);
    const ok = bad.length === 0;
    results.push({ fixture: base, ok, pulled, ...(bad.length ? { badPngs: bad } : {}), elapsedSec });
    log(`  ${base}: ${ok ? 'OK' : 'PARTIAL'} ${pulled.length}/${expected.length} PNGs in ${elapsedSec.toFixed(2)}s`);
  }

  adbx(['shell', 'am', 'force-stop', PKG]); // stop app; leave the device running

  // Machine-readable summary on stdout (human logs went to stderr).
  const okCount = results.filter((r) => r.ok).length;
  const timed = results.filter((r) => r.ok && typeof r.elapsedSec === 'number');
  const warm = timed.slice(1); // exclude the first (may include JIT warmup)
  const avg = (rs) => rs.length ? rs.reduce((a, r) => a + r.elapsedSec, 0) / rs.length : null;
  const summary = {
    device: serial, total: fixtures.length, ok: okCount,
    secPerFixtureAll: avg(timed), secPerFixtureWarm: avg(warm.length ? warm : timed),
    results,
  };
  process.stdout.write(JSON.stringify(summary, null, 2) + '\n');
  process.exit(okCount === fixtures.length ? 0 : 1);
}

main().catch((e) => { log(`FATAL: ${e.stack || e.message}`); process.exit(1); });
