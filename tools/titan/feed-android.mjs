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
import { parseArgs, expectedPngNames, composedPngName, pngIsValid,
         documentFontSrcs, resolveFontFile } from './feed-lib.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');

// On-device layout (mirrors ScreenshotManager: getExternalFilesDir(null)+…).
const PKG = 'com.styleconverter.test';
const ACTIVITY = `${PKG}/.MainActivity`;
const INBOX_DIR = `/sdcard/Android/data/${PKG}/files/inbox`;
const SHOT_DIR = `/sdcard/Android/data/${PKG}/files/test_screenshots`;
// wave-35 lane B2 — the @font-face sandbox. A SIBLING of the inbox, not a
// subdirectory of it: ScreenshotManager.nextFixtureFile() lists the inbox for
// `*.json`, so a font under it would be inert but confusing, and a future
// listing change would trip over it. The runtime reads File(FONTS_DIR, src)
// with the corpus-relative path preserved verbatim (see feed-lib.mjs's hop
// banner), which is why the pushes below mkdir the src's parent chain.
const FONTS_DIR = `/sdcard/Android/data/${PKG}/files/fonts`;

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

/** Force-stop the harness, wipe the inbox + shot dirs + logcat, relaunch in
 *  inbox/composed mode, and verify the run-config marker. Returns whether the
 *  marker was seen. Used at startup AND as recovery: a single fixture whose
 *  on-device capture WEDGES (e.g. a pathological transform layer the composed
 *  capture never returns from) leaves the inbox poll loop stuck forever, so
 *  every fixture queued behind it would cascade-timeout on a still-hung app.
 *  Restarting between a timeout and the next fixture gives each one a clean
 *  app — one bad fixture costs one timeout, not the whole tail of the batch. */
async function resetAndLaunch(adbx, opts) {
  adbx(['shell', 'am', 'force-stop', PKG]);
  // wave-35: FONTS_DIR joins the wipe. A face registered from a PREVIOUS
  // run's file would be the worst possible stale state — the capture would
  // shape correctly-looking glyphs from the wrong file with no error anywhere
  // — so the fonts sandbox gets the same idempotence guarantee the inbox has.
  adbx(['shell', 'rm', '-rf', INBOX_DIR, SHOT_DIR, FONTS_DIR]);
  adbx(['shell', 'mkdir', '-p', INBOX_DIR, SHOT_DIR, FONTS_DIR]);
  try { adbx(['logcat', '-c']); } catch { /* logcat clear is best-effort */ }
  // Match the shared 390×844 @160dpi capture canvas. Composed mode layers
  // `--ez titanComposed true`: same inbox poll, whole doc onto one canvas.
  adbx(['shell', 'wm', 'size', '390x844']);
  adbx(['shell', 'wm', 'density', '160']);
  const launchArgs = ['shell', 'am', 'start', '-n', ACTIVITY, '--ez', 'titanInbox', 'true'];
  if (opts.composed) launchArgs.push('--ez', 'titanComposed', 'true');
  adbx(launchArgs);
  // Grep the run-config marker (same verification philosophy as test-all's
  // animationTime/forceState gate) — proves the app really entered the mode.
  // In composed mode we additionally require titanComposed=true so a stale
  // per-component launch can't masquerade as composed. logcat was just cleared,
  // so a match is this launch's marker, not a stale one from a prior launch.
  const markerRe = opts.composed ? /titanInbox=true titanComposed=true/ : /titanInbox=true/;
  let marked = false;
  for (let i = 0; i < 40 && !marked; i++) {
    try { marked = markerRe.test(adbx(['logcat', '-d'])); } catch { /* retry */ }
    if (!marked) await new Promise((r) => setTimeout(r, 250));
  }
  return marked;
}

/** wave-35 lane B2 — push this document's @font-face FILES into the device's
 *  fonts sandbox, preserving the corpus-relative path verbatim.
 *
 *  Called BEFORE the IR reaches the inbox, and that ordering is the contract:
 *  the app registers faces at decode time, so a font arriving after its
 *  document would register too late to shape the capture — and with
 *  `font-display: block`-equivalent semantics absent on the native side, the
 *  capture would silently record the fallback face.
 *
 *  Returns { pushed, declined } for the feeder's per-fixture log. A decline is
 *  never fatal: the runtimes degrade to their bundled face and stamp the miss,
 *  which is the wave-34 behaviour this hop improves on rather than replaces.
 *  Failing the fixture instead would hide every OTHER property it measures. */
function pushFontFaces(adbx, doc, opts) {
  const srcs = documentFontSrcs(doc);
  if (srcs.length === 0 || !opts.wptDir) return { pushed: 0, declined: srcs.length };
  let pushed = 0, declined = 0;
  const madeDirs = new Set();
  for (const src of srcs) {
    const abs = resolveFontFile(opts.wptDir, src, { resolve: path.resolve, existsSync, statSync });
    if (!abs) { declined++; log(`  font DECLINED (unresolvable/not a font): ${src}`); continue; }
    // `adb push` will not create intermediate directories, and the relative
    // chain is what the runtime resolves against — so mkdir the parent once
    // per distinct directory (WPT font paths cluster in one resources/ dir).
    const remote = `${FONTS_DIR}/${src}`;
    const parent = remote.slice(0, remote.lastIndexOf('/'));
    if (!madeDirs.has(parent)) { adbx(['shell', 'mkdir', '-p', parent]); madeDirs.add(parent); }
    try { adbx(['push', abs, remote]); pushed++; }
    catch (e) { declined++; log(`  font PUSH FAILED: ${src} — ${e.message}`); }
  }
  return { pushed, declined };
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

  // Reset device state + launch BEFORE the loop so the poll starts clean
  // (idempotence: a second run can't see the first run's stale files). Same
  // helper the timeout branch uses to recover from a wedged capture.
  log(`launching in titan-${opts.composed ? 'composed' : 'inbox'} mode; verifying marker…`);
  const marked = await resetAndLaunch(adbx, opts);
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
    // wave-35: faces FIRST — see pushFontFaces' ordering contract.
    const fonts = pushFontFaces(adbx, doc, opts);
    if (fonts.pushed || fonts.declined) {
      log(`  ${base}: fonts pushed=${fonts.pushed} declined=${fonts.declined}`);
    }
    // Push into the inbox under a unique, FIFO-ordered name (index prefix
    // guarantees uniqueness even if two fixtures share a basename).
    adbx(['push', fx, `${INBOX_DIR}/${String(i).padStart(4, '0')}-${base}`]);
    const { done, present } = await waitForPngs(adbx, wantDevice, opts.timeoutPerFixture);
    if (!done) {
      results.push({ fixture: base, ok: false, error: 'timeout',
        want: wantDevice, got: [...present], elapsedSec: (Date.now() - t0) / 1000 });
      // A timeout means the on-device capture WEDGED — the app's inbox loop is
      // now stuck on this fixture and will never process the next one. Restart
      // the app so the wedge doesn't cascade-timeout the whole tail of the
      // batch (resetAndLaunch also wipes the inbox + partials). Skip the
      // restart if this was the last fixture — nothing left to protect.
      if (i < fixtures.length - 1) {
        log(`  ${base}: TIMEOUT (${present.size}/${expected.length} PNGs) — restarting app to clear the wedge`);
        const remarked = await resetAndLaunch(adbx, opts);
        if (!remarked) log('  WARNING: marker not seen after restart (continuing)');
      } else {
        log(`  ${base}: TIMEOUT (${present.size}/${expected.length} PNGs) — last fixture, not restarting`);
      }
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

  // Tail-retry pass: give every timed-out fixture ONE more chance now that
  // the app is warm. The dominant timeout cause on a pooled device is the
  // COLD first launch after boot (JIT + first render blow the budget on
  // fixture #1, then everything runs in ~1s) — without this pass, every
  // section that lands on a cold slot ships a hole at its first fixture. A
  // genuinely wedging fixture (e.g. css-transforms z-ordering-003) just
  // times out a second time and stays failed — retried:true marks the row
  // either way so the summary shows what was salvaged vs truly broken.
  const timedOutRows = results.filter((r) => r.error === 'timeout');
  if (timedOutRows.length > 0 && timedOutRows.length < fixtures.length) {
    log(`tail-retry: ${timedOutRows.length} timed-out fixture(s), one warm retry each…`);
    for (const row of timedOutRows) {
      const fx = fixtures.find((f) => path.basename(f) === row.fixture);
      if (!fx) continue;
      const expected = opts.composed
        ? (() => { const n = composedPngName(row.fixture); return [{ deviceFile: n, hostFile: n }]; })()
        : expectedPngNames(JSON.parse(readFileSync(fx, 'utf8')));
      const t0 = Date.now();
      // 9xxx prefix keeps the inbox name unique vs the first attempt's 0xxx.
      adbx(['push', fx, `${INBOX_DIR}/9${String(fixtures.indexOf(fx)).padStart(3, '0')}-${row.fixture}`]);
      const { done } = await waitForPngs(adbx, expected.map((e) => e.deviceFile), opts.timeoutPerFixture);
      row.retried = true;
      if (done) {
        await new Promise((r) => setTimeout(r, 150));
        const pulled = [];
        for (const e of expected) {
          if (pullVerified(adbx, e.deviceFile, e.hostFile, opts.out)) pulled.push(e.hostFile);
        }
        if (pulled.length === expected.length) {
          row.ok = true; delete row.error;
          row.pulled = pulled; row.elapsedSec = (Date.now() - t0) / 1000;
          log(`  ${row.fixture}: RETRY OK in ${row.elapsedSec.toFixed(2)}s`);
        }
      } else {
        log(`  ${row.fixture}: RETRY TIMEOUT — genuinely failing, restarting app`);
        await resetAndLaunch(adbx, opts);
      }
      adbx(['shell', 'rm', '-f', `${SHOT_DIR}/*`]);
    }
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
