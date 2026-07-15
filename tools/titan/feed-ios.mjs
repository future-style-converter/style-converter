#!/usr/bin/env node
//
// tools/titan/feed-ios.mjs — TITAN Phase 3 iOS WPT feeder (host side).
//
// The app half (apps/ios-harness .../Screenshot/InboxCaptureView.swift) boots
// ONCE in inbox mode and polls Documents/inbox for IR documents. This script
// is the producer that drives it: it locates the ALREADY-BOOTED simulator
// (it never boots one), installs+launches the app in inbox mode, then per
// fixture pushes the IR JSON into the on-device inbox, waits for the expected
// per-component PNGs to appear, pulls them to --out with the exact
// `<NNN>_<safeName>.png` naming the compare pipeline globs
// (tools/titan/inject-wpt-block.mjs diffPlatformVsRef), and clears device
// state before the next fixture. Cost per fixture is the SwiftUI render only
// (~0.5-2 s warm) instead of a 30 s rebuild+install+launch cycle.
//
// Usage:
//   feed-ios.mjs --fixtures <dir|a.json,b.json> --out <hostScreenshotsDir>
//                [--timeout-per-fixture <seconds>] [--udid <UDID>]
//                [--no-build] [--app <StyleConverterTest.app>]
//
// --timeout-per-fixture is in SECONDS, matching feed-android.mjs (both feeders
// take the same unit so run-titan.sh passes the same number to each). It was
// milliseconds here historically; the seconds unit is friendlier and removes
// the cross-feeder unit mismatch.
//
// --no-build reuses the app already installed on the device (skips xcodebuild
// and install) but still relaunches it in inbox mode — used for the
// idempotence re-run so run 1's residual on-device state is preserved and
// proven harmless.
//

import { promises as fs } from 'node:fs';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, resolve, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { PNG } from 'pngjs';
// The ONE canonical compare-pipeline sanitiser (dot KEPT), shared with
// feed-lib.mjs, inject-wpt-block.mjs, split-combined-ir.mjs and the web
// capture drivers so every host-side filename is sanitised identically.
import { safe } from './safe-name.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');
const IOS_DIR = join(REPO_ROOT, 'apps', 'ios-harness');
const BUNDLE_ID = 'com.styleconverter.test';

// ── Pure helpers (unit-tested in feed-ios.test.mjs) ──────────────────────────

/** Compare-pipeline filename sanitiser — the shared `safe()` (dot KEPT),
 *  re-exported under this module's historical name. It is what the
 *  orchestrator globs for (`*_<safeName>.png`), so it is what we WRITE to
 *  --out. Identical function object as inject-wpt-block.mjs's `safe`. */
export const safeName = safe;
// Re-export the canonical helper directly too, so callers/tests can assert
// `import { safe }` from any pipeline module resolves to one implementation.
export { safe };

/** On-device filename sanitiser. Mirrors ScreenshotManager.save (Swift):
 *  only `/` and space are replaced with `_`. This predicts the exact name
 *  the app wrote, so we know which file to pull. For WPT component names
 *  (alnum/`-`/`_`) it equals safeName; kept separate so a name with an
 *  exotic char is still located on device, then renamed to safeName on pull. */
export function deviceSafeName(name) {
  return String(name).replace(/[/ ]/g, '_');
}

/** Zero-pad a capture index to the app's `%03d` filename rule. */
export function pad3(n) {
  return String(n).padStart(3, '0');
}

// Extract a string from an IR value that may be a bare string or `{value}`.
function strVal(v) {
  if (typeof v === 'string') return v;
  if (v && typeof v === 'object' && typeof v.value === 'string') return v.value;
  return null;
}
// Extract an opacity scalar across the historical IR carrier shapes.
function opacityVal(v) {
  if (typeof v === 'number') return v;
  if (v && typeof v === 'object') {
    if (typeof v.alpha === 'number') return v.alpha;
    if (typeof v.value === 'number') return v.value;
    if (v.original && typeof v.original.value === 'number') return v.original.value;
  }
  return null;
}

/** Does a component create a paint context its children depend on? Mirrors
 *  parentCreatesContext in ScreenshotCaptureView.swift / CaptureGallery.tsx
 *  so the feeder's flatten matches the app's capture list exactly. Inert on
 *  IR v2 (children is always absent — flat doc) but kept for parity and to
 *  stay correct if a future wire shape re-nests. */
export function parentCreatesContext(component) {
  const props = component.properties || [];
  if (props.length === 0) return false;
  for (const p of props) {
    switch (p.type) {
      case 'ClipPath': case 'Mask': case 'MaskImage':
      case 'Filter': case 'BackdropFilter':
      case 'Rotate': case 'Scale': case 'Translate':
        return true;
      case 'Overflow': case 'OverflowX': case 'OverflowY': {
        const v = strVal(p.data)?.toLowerCase();
        if (v === 'clip' || v === 'hidden') return true;
        break;
      }
      case 'MixBlendMode': {
        const v = strVal(p.data)?.toLowerCase();
        if (v && v !== 'normal') return true;
        break;
      }
      case 'Transform':
        if (Array.isArray(p.data) && p.data.length > 0) return true;
        break;
      case 'Opacity': {
        const v = opacityVal(p.data);
        if (v != null && v < 1) return true;
        break;
      }
      default:
        break;
    }
  }
  return false;
}

/** Compose the FLAT v2 wire list into slot-rooted trees — the exact
 *  transform IRComposer.compose (Swift) / the web composer run at decode.
 *  The app's ScreenshotCaptureView flattens the COMPOSED roots, not the raw
 *  wire array, so the feeder MUST compose first or it over-counts captures
 *  (a fixture whose children slot into a paint-context parent yields ONE
 *  capture, not one-per-wire-entry). Children are bucketed under their
 *  parent's `id` (slot.parent references the id, not the name); a component
 *  with no slot, a dangling/self parent, or an unreachable cycle is a root. */
export function composeComponents(flat) {
  if (!flat || flat.length === 0) return [];
  const ids = new Set(flat.map((c) => c.id));
  const childIndices = new Map(); // parent id → child flat-array indices (order preserved)
  const rootIndices = [];
  flat.forEach((c, i) => {
    const s = c.slot;
    if (s && s.parent !== c.id && ids.has(s.parent)) {
      if (!childIndices.has(s.parent)) childIndices.set(s.parent, []);
      childIndices.get(s.parent).push(i);
    } else {
      rootIndices.push(i); // no slot, dangling, or self-reference → root
    }
  });
  const visited = new Set();
  const build = (i) => {
    visited.add(i);
    const kids = (childIndices.get(flat[i].id) || [])
      .filter((j) => !visited.has(j)) // cycle guard, mirrors the Swift composer
      .map(build);
    return { ...flat[i], children: kids.length ? kids : undefined };
  };
  const roots = rootIndices.map(build);
  // Cycle sweep: promote anything unreachable from a root rather than drop it.
  for (let i = 0; i < flat.length; i++) if (!visited.has(i)) roots.push(build(i));
  return roots;
}

/** Flatten composed component trees depth-first pre-order, suppressing
 *  children of a paint-context parent — the exact rule the app's flatten()
 *  applies. Feed it COMPOSED roots (see composeComponents). */
export function flattenComponents(components) {
  const out = [];
  const walk = (c) => {
    out.push(c);
    const kids = c.children;
    if (!kids || kids.length === 0) return;
    if (parentCreatesContext(c)) return;
    kids.forEach(walk);
  };
  (components || []).forEach(walk);
  return out;
}

/** IR document → the per-component capture manifest the app will produce:
 *  { index, name, deviceFile (poll for), hostFile (write to --out) }.
 *  Compose (slot tree) THEN flatten (paint-context suppression), matching
 *  IRDocument decode + ScreenshotCaptureView.flatten on the device. */
export function expectedCaptures(doc) {
  const roots = composeComponents(doc.components || []);
  const flat = flattenComponents(roots);
  return flat.map((c, i) => {
    const name = c.name ?? '';
    return {
      index: i,
      name,
      deviceFile: `${pad3(i)}_${deviceSafeName(name)}.png`,
      hostFile: `${pad3(i)}_${safeName(name)}.png`,
    };
  });
}

// ── TITAN WPT Round 3 composed-mode helpers (unit-tested) ────────────────────

/** The WPT test key for a per-test-ir fixture path. The per-test-ir docs are
 *  named exactly `wpt__<section>__<stem>.json` (split-combined-ir.mjs), so the
 *  testKey is just the basename minus `.json`. This is the identity the app
 *  derives from the inbox filename and the name diffComposedVsRef globs. */
export function composedTestKey(fixturePath) {
  return basename(fixturePath, '.json');
}

/** The composed capture PNG name for a testKey: `<safe(testKey)>.png` — the
 *  ONE file the app writes and the compare pipeline reads. safeName() is the
 *  same sanitiser the app's ScreenshotManager.safeCaptureName applies, so the
 *  host predicts the exact on-device filename. */
export function composedPngName(testKey) {
  return `${safeName(testKey)}.png`;
}

/** Parse the CLI argv (after `node feed-ios.mjs`). `--timeout-per-fixture` is
 *  in SECONDS (default 30), matching feed-lib.mjs/feed-android.mjs — a
 *  non-numeric / non-positive value falls back to the default so the
 *  per-fixture watchdog can never be silently disabled (same guard as
 *  feed-lib's parseArgs). */
export function parseArgs(argv) {
  const out = {
    fixtures: null, out: null, timeoutPerFixture: 30,
    udid: null, appPath: null, noBuild: false, composed: false, help: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--fixtures') out.fixtures = argv[++i];
    else if (a === '--out' || a === '-o') out.out = argv[++i];
    else if (a === '--timeout-per-fixture') out.timeoutPerFixture = Number(argv[++i]);
    else if (a === '--udid') out.udid = argv[++i];
    else if (a === '--app') out.appPath = argv[++i];
    else if (a === '--no-build') out.noBuild = true;
    // TITAN WPT Round 3: composed capture — feed each per-test doc named
    // `<testKey>.json`, launch the app with SIMCTL_CHILD_TITAN_COMPOSED=1,
    // and pull ONE `<safe(testKey)>.png` per test (no per-component stitch).
    else if (a === '--composed') out.composed = true;
    else if (a === '--help' || a === '-h') out.help = true;
  }
  // Guard against a non-numeric / non-positive timeout silently disabling the
  // per-fixture watchdog (mirrors feed-lib.parseArgs). Seconds; default 30.
  if (!(Number.isFinite(out.timeoutPerFixture) && out.timeoutPerFixture > 0)) {
    out.timeoutPerFixture = 30;
  }
  return out;
}

/** Pick the target simulator from `simctl list devices --json` output.
 *  Never boots — throws loudly when the requested (or any) device is not
 *  already Booted, so the caller boots it themselves. */
export function pickBootedUdid(devicesJson, requestedUdid = null) {
  const all = [];
  for (const runtime of Object.keys(devicesJson.devices || {})) {
    for (const d of devicesJson.devices[runtime]) {
      all.push({ udid: d.udid, state: d.state, name: d.name });
    }
  }
  if (requestedUdid) {
    const m = all.find((d) => d.udid === requestedUdid);
    if (!m) throw new Error(`--udid ${requestedUdid} not in simctl device list`);
    if (m.state !== 'Booted') {
      throw new Error(`--udid ${requestedUdid} is '${m.state}', not Booted — boot it first (this feeder never boots a device)`);
    }
    return requestedUdid;
  }
  const booted = all.filter((d) => d.state === 'Booted');
  if (booted.length === 0) {
    throw new Error('no Booted simulator found — boot one first (this feeder never boots a device)');
  }
  // Prefer an iPhone when several are booted.
  return (booted.find((d) => /iPhone/i.test(d.name)) || booted[0]).udid;
}

// ── Side-effecting helpers ───────────────────────────────────────────────────

// Thin spawnSync wrapper; returns { status, stdout, stderr }.
function run(cmd, args, opts = {}) {
  return spawnSync(cmd, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...opts });
}

// Resolve --fixtures into an ordered list of IR JSON paths.
function resolveFixtures(arg) {
  if (arg.includes(',')) return arg.split(',').map((s) => resolve(s.trim())).filter(Boolean);
  const p = resolve(arg);
  if (existsSync(p) && statSync(p).isDirectory()) {
    return readdirSync(p).filter((f) => f.endsWith('.json')).sort().map((f) => join(p, f));
  }
  return [p];
}

// Remove every *.png (and the capture-config marker) from a device dir so a
// stale capture can never satisfy the next fixture's poll.
async function clearPngs(dir) {
  let entries = [];
  try { entries = await fs.readdir(dir); } catch { return; }
  await Promise.all(entries
    .filter((f) => f.endsWith('.png') || f === 'capture-config.json')
    .map((f) => fs.rm(join(dir, f), { force: true })));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Poll `shotsDir` until every expected deviceFile exists, or timeout.
async function waitForCaptures(shotsDir, expected, timeoutMs) {
  const wanted = new Set(expected.map((e) => e.deviceFile));
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    let present = new Set();
    try { present = new Set(await fs.readdir(shotsDir)); } catch { /* dir not yet created */ }
    if ([...wanted].every((f) => present.has(f))) return true;
    if (Date.now() > deadline) return false;
    await sleep(150);
  }
}

// Copy one device PNG to --out and verify it parses; retry the copy once on a
// truncation/parse flake before giving up on that component.
async function pullVerified(srcPath, destPath) {
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      await fs.copyFile(srcPath, destPath);
      PNG.sync.read(await fs.readFile(destPath)); // throws on a truncated/invalid PNG
      return true;
    } catch (err) {
      if (attempt === 1) {
        console.error(`  ! pull failed for ${basename(destPath)}: ${err.message}`);
        return false;
      }
      await sleep(120); // brief backoff, then one retry
    }
  }
  return false;
}

// ── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || !args.fixtures || !args.out) {
    console.log('usage: feed-ios.mjs --fixtures <dir|a.json,b.json> --out <dir> [--timeout-per-fixture <seconds>] [--udid <UDID>] [--no-build] [--composed] [--app <path>]');
    process.exit(args.help ? 0 : 2);
  }

  const fixtures = resolveFixtures(args.fixtures);
  const outDir = resolve(args.out);
  await fs.mkdir(outDir, { recursive: true });
  console.log(`[feed-ios] ${fixtures.length} fixture(s) → ${outDir}`);

  // 1. Locate the already-running simulator (never boot one).
  const listed = run('xcrun', ['simctl', 'list', 'devices', '--json']);
  if (listed.status !== 0) throw new Error(`simctl list failed: ${listed.stderr}`);
  const udid = pickBootedUdid(JSON.parse(listed.stdout), args.udid);
  console.log(`[feed-ios] target simulator: ${udid}`);

  // 2. Build + install (unless reusing an existing install for a re-run).
  let appPath = args.appPath ? resolve(args.appPath)
    : join(IOS_DIR, 'build', 'Build', 'Products', 'Debug-iphonesimulator', 'StyleConverterTest.app');
  if (!args.noBuild) {
    const buildDir = join(IOS_DIR, 'build', 'Build', 'Products', 'Debug-iphonesimulator');
    console.log('[feed-ios] building app (xcodebuild)…');
    const b = run('xcodebuild', [
      '-project', join(IOS_DIR, 'StyleConverterTest.xcodeproj'),
      '-target', 'StyleConverterTest', '-configuration', 'Debug',
      '-sdk', 'iphonesimulator', '-arch', 'arm64',
      `CONFIGURATION_BUILD_DIR=${buildDir}`, 'CODE_SIGNING_ALLOWED=NO', 'build',
    ], { stdio: ['ignore', 'pipe', 'pipe'] });
    if (b.status !== 0) {
      console.error((b.stdout || '').split('\n').slice(-25).join('\n'));
      console.error((b.stderr || '').split('\n').slice(-10).join('\n'));
      throw new Error('xcodebuild failed');
    }
    // Clean install: uninstall wipes the container so the run starts from an
    // empty inbox/screenshots (no cross-run stale state on the FIRST run).
    run('xcrun', ['simctl', 'terminate', udid, BUNDLE_ID]);
    run('xcrun', ['simctl', 'uninstall', udid, BUNDLE_ID]);
    const inst = run('xcrun', ['simctl', 'install', udid, appPath]);
    if (inst.status !== 0) throw new Error(`simctl install failed: ${inst.stderr}`);
    console.log('[feed-ios] installed');
  }

  // 3. Launch in inbox mode. simctl forwards SIMCTL_CHILD_* into the app env
  //    (same transport as SIMCTL_CHILD_CAPTURE_ANIMATION_TIME). In composed
  //    mode we ALSO forward SIMCTL_CHILD_TITAN_COMPOSED=1 so the app renders
  //    each fed doc as ONE composed `<safe(testKey)>.png` (WPT Round 3)
  //    instead of the legacy per-component captures.
  run('xcrun', ['simctl', 'terminate', udid, BUNDLE_ID]);
  const launchEnv = { ...process.env, SIMCTL_CHILD_TITAN_INBOX: '1' };
  if (args.composed) launchEnv.SIMCTL_CHILD_TITAN_COMPOSED = '1';
  const launch = run('xcrun', ['simctl', 'launch', udid, BUNDLE_ID], { env: launchEnv });
  if (launch.status !== 0) throw new Error(`simctl launch failed: ${launch.stderr}`);
  console.log(`[feed-ios] launched in inbox mode${args.composed ? ' (composed)' : ''}`);

  // 4. Resolve the app data container → inbox + screenshots dirs.
  const cont = run('xcrun', ['simctl', 'get_app_container', udid, BUNDLE_ID, 'data']);
  if (cont.status !== 0) throw new Error(`get_app_container failed: ${cont.stderr}`);
  const container = cont.stdout.trim();
  const inboxDir = join(container, 'Documents', 'inbox');
  const shotsDir = join(container, 'Documents', 'test_screenshots');
  await fs.mkdir(inboxDir, { recursive: true }); // exist before first push (app creates lazily)
  await clearPngs(shotsDir);
  // Drain any stragglers from a prior run so the queue starts clean.
  try {
    for (const f of await fs.readdir(inboxDir)) {
      if (f.endsWith('.json')) await fs.rm(join(inboxDir, f), { force: true });
    }
  } catch { /* inbox may not exist yet */ }

  // 5. Per-fixture feed loop.
  const results = [];
  const times = [];
  for (let n = 0; n < fixtures.length; n++) {
    const fx = fixtures[n];
    const label = basename(fx);
    let doc;
    try {
      doc = JSON.parse(await fs.readFile(fx, 'utf8'));
    } catch (err) {
      console.error(`[feed-ios] ${label}: unreadable IR (${err.message}) — skipping`);
      results.push({ fixture: label, ok: false, reason: 'unreadable-ir', pulled: 0, expected: 0 });
      continue;
    }
    // Capture manifest + inbox filename depend on the mode:
    //   • composed: ONE `<safe(testKey)>.png` per test; the inbox file is
    //     named `<testKey>.json` so the app derives the testKey from it.
    //   • per-component (legacy): one `%03d_<name>.png` per flattened
    //     component; the inbox file is a generic `fixture-<n>.json`.
    const testKey = args.composed ? composedTestKey(fx) : null;
    const expected = args.composed
      ? (() => {
          const png = composedPngName(testKey);   // <safe(testKey)>.png
          return [{ index: 0, name: testKey, deviceFile: png, hostFile: png }];
        })()
      : expectedCaptures(doc);
    if (expected.length === 0) {
      results.push({ fixture: label, ok: true, pulled: 0, expected: 0 });
      continue;
    }
    // In composed mode the inbox filename IS the testKey (the app derives
    // the PNG name from it); otherwise a generic per-run name is fine.
    const inboxBase = args.composed ? testKey : `fixture-${n}`;

    await clearPngs(shotsDir);           // idempotence: no stale PNGs from fixture n-1
    const t0 = Date.now();
    // Push via a temp name + rename so the app never reads a half-written JSON
    // (nextFixtureURL only sees `.json` files; the rename is atomic in-dir).
    // The tmp is dot-prefixed so its `.tmp` extension is skipped by the
    // app's nextFixtureURL (it globs `.json` only) even mid-write.
    const tmp = join(inboxDir, `.${inboxBase}.json.tmp`);
    const dest = join(inboxDir, `${inboxBase}.json`);
    await fs.writeFile(tmp, JSON.stringify(doc));
    await fs.rename(tmp, dest);

    // timeoutPerFixture is SECONDS (see parseArgs); waitForCaptures wants ms.
    const done = await waitForCaptures(shotsDir, expected, args.timeoutPerFixture * 1000);
    if (!done) {
      const elapsed = ((Date.now() - t0) / 1000).toFixed(2);
      console.error(`[feed-ios] ${label}: TIMEOUT after ${elapsed}s (expected ${expected.length} PNG) — recording failure`);
      results.push({ fixture: label, ok: false, reason: 'timeout', pulled: 0, expected: expected.length });
      // A timeout means the on-device capture WEDGED — the app's inbox loop is
      // now stuck on this fixture and won't process the next one. Terminate +
      // relaunch (draining the stuck inbox file + partials first) so the wedge
      // doesn't cascade-timeout the whole tail of the batch. Mirrors
      // feed-android's resetAndLaunch recovery. Skip on the last fixture —
      // nothing left to protect.
      if (n < fixtures.length - 1) {
        console.error('[feed-ios] restarting app to clear the wedge');
        try {
          for (const f of await fs.readdir(inboxDir)) {
            if (f.endsWith('.json')) await fs.rm(join(inboxDir, f), { force: true });
          }
        } catch { /* inbox may be empty */ }
        await clearPngs(shotsDir);
        run('xcrun', ['simctl', 'terminate', udid, BUNDLE_ID]);
        const relaunch = run('xcrun', ['simctl', 'launch', udid, BUNDLE_ID], { env: launchEnv });
        if (relaunch.status !== 0) console.error(`[feed-ios] relaunch failed: ${relaunch.stderr}`);
      }
      continue;
    }

    // Pull + verify each expected capture into --out with the compare name.
    let pulled = 0;
    for (const e of expected) {
      if (await pullVerified(join(shotsDir, e.deviceFile), join(outDir, e.hostFile))) pulled++;
    }
    const secs = (Date.now() - t0) / 1000;
    times.push(secs);
    const ok = pulled === expected.length;
    results.push({ fixture: label, ok, pulled, expected: expected.length, seconds: +secs.toFixed(2) });
    console.log(`[feed-ios] ${label}: ${pulled}/${expected.length} PNG in ${secs.toFixed(2)}s ${ok ? '✓' : '✗'}`);
    await clearPngs(shotsDir);           // leave device clean for the next fixture
  }

  // 6. Summary + warm seconds-per-fixture (mean excluding the first, which
  //    carries app cold-start cost).
  const okCount = results.filter((r) => r.ok).length;
  const warm = times.slice(1);
  const warmAvg = warm.length ? warm.reduce((a, b) => a + b, 0) / warm.length : (times[0] ?? 0);
  const summary = {
    total: fixtures.length,
    ok: okCount,
    failed: fixtures.length - okCount,
    warmSecPerFixture: +warmAvg.toFixed(3),
    firstFixtureSec: times.length ? +times[0].toFixed(3) : null,
    results,
  };
  console.log('[feed-ios] SUMMARY ' + JSON.stringify(summary));
  process.exit(okCount === fixtures.length ? 0 : 1);
}

// CLI guard — importing this module (feed-ios.test.mjs) must not run main().
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((err) => { console.error('[feed-ios] FATAL:', err.message); process.exit(1); });
}
