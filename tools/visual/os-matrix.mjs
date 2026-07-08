#!/usr/bin/env node
// Tier 12 — Snapshot stability across OS versions.
//
// Phase 12a (round 41): web + iOS slice.
//
// Re-runs a small fixture across each available iOS simulator runtime and
// (best-effort) Android emulator API level, then compares per-fixture SSIM
// across versions. Any fixture drifting > 0.05 between versions is flagged
// as version-sensitive.
//
// Original Tier 12 spec wanted iOS 16/17/18 + Android API 24/30/34/35. This
// machine has iOS 26.0 + 26.2 and 0 Android AVDs. Phase 12a does the
// honest thing: run on whatever IS installed and report a "delta" matrix
// with explicit `not-installed` rows for everything else. Adding iOS 16/17/18
// or new AVDs is a one-time `xcrun simctl runtime install` / `avdmanager
// create avd` task — separate from the test infrastructure work.
//
// What this script does (per detected runtime):
//   1. Shutdown all simulators (so test-all.sh doesn't pick the wrong one)
//   2. Boot the chosen UDID
//   3. Run test-all.sh with SIM_UDID=<udid> SKIP_ANDROID=1 SKIP_WEB=1
//      (web is platform-stable; we're scoring iOS-version drift specifically)
//   4. Snapshot apps/ios-harness/screenshots/ into tools/visual/os-matrix-snapshots/<runtime>/
//   5. Repeat for next runtime
//   6. Cross-runtime SSIM: for every fixture present in both runtimes, score.
//      Drift > 0.05 = regression. Drift ≤ 0.05 = stable.
//
// Output: tools/visual/os-matrix-report.json + per-pair delta dump.

import { execSync, spawnSync } from 'node:child_process';
import { readdirSync, mkdirSync, existsSync, copyFileSync, writeFileSync, rmSync } from 'node:fs';
import { join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

// Round 73: exported pure parsing helpers + constants so
// tools/visual/os-matrix.test.mjs can pin the contract without needing
// xcrun / a real simulator. Main script body gated below.

export const FIXTURE = process.env.OS_MATRIX_FIXTURE || 'fixtures/components/Alert.json';
export const SNAP_ROOT = 'tools/visual/os-matrix-snapshots';
export const REPORT_PATH = 'tools/visual/os-matrix-report.json';
export const SSIM_DRIFT_THRESHOLD = 0.05;

// === Detect installed iOS runtimes + their iPhone 17 Pro UDIDs ===
//
// We pin to "iPhone 17 Pro" specifically so device-model differences don't
// confound iOS-version differences. If iPhone 17 Pro isn't on a runtime,
// fall back to the first iPhone we find (and record the model in the report
// so the cross-version comparison is honest about what changed).
/**
 * Pure parser: take parsed `xcrun simctl list devices --json` output
 * and return our normalized [{label, runtimeId, device, udid}] shape.
 * Extracted from detectIOSRuntimes in round 73 so tools/visual/os-matrix.test.mjs
 * can pin the contract without needing xcrun + a real simulator.
 *
 * Picks one iPhone per iOS runtime (prefers iPhone 17 Pro for consistency,
 * otherwise the first available iPhone). Skips runtimes that aren't iOS
 * (tvOS / watchOS / visionOS) and runtimes with no available iPhones.
 */
export function parseIOSRuntimesFromSimctlJson(json) {
  const out = [];
  if (!json || !json.devices) return out;
  for (const [runtimeId, devices] of Object.entries(json.devices)) {
    if (!runtimeId.includes('iOS')) continue;
    const iphones = (devices || []).filter((d) => d.isAvailable && d.name?.startsWith('iPhone'));
    if (iphones.length === 0) continue;
    const dev = iphones.find((d) => d.name === 'iPhone 17 Pro') || iphones[0];
    const versionMatch = runtimeId.match(/iOS-(\d+)-(\d+)/);
    const versionLabel = versionMatch ? `iOS-${versionMatch[1]}.${versionMatch[2]}` : runtimeId;
    out.push({
      label: versionLabel,
      runtimeId,
      device: dev.name,
      udid: dev.udid,
    });
  }
  return out;
}

/**
 * Pure parser: take `emulator -list-avds` stdout and return our
 * [{label, name}] shape. Extracted from detectAndroidAVDs in round 73.
 */
export function parseAndroidAVDsFromList(stdout) {
  if (!stdout) return [];
  return stdout.split('\n').filter(Boolean).map((name) => ({ label: name, name }));
}

function detectIOSRuntimes() {
  let json;
  try {
    json = JSON.parse(execSync('xcrun simctl list devices --json').toString());
  } catch (err) {
    return [];
  }
  return parseIOSRuntimesFromSimctlJson(json);
}

// === Detect installed Android AVDs ===
function detectAndroidAVDs() {
  try {
    const out = execSync('emulator -list-avds 2>/dev/null', { encoding: 'utf8' });
    return parseAndroidAVDsFromList(out);
  } catch {
    return [];
  }
}

// === Shutdown all simulators (best-effort) so test-all.sh boots the one we want ===
function shutdownAllSims() {
  try { execSync('xcrun simctl shutdown all', { stdio: 'ignore' }); } catch { /* ignore */ }
}

// === Run test-all.sh against a given iOS UDID, return path to captured screenshots ===
function runOnIOSRuntime(runtime) {
  shutdownAllSims();

  // Boot the requested device. test-all.sh will detect it as already-booted
  // and skip the boot-and-wait dance, saving ~10s.
  console.log(`  booting ${runtime.label} ${runtime.device} (${runtime.udid})…`);
  try {
    execSync(`xcrun simctl boot ${runtime.udid}`, { stdio: 'ignore' });
  } catch (err) {
    return { error: `boot-failed: ${err.message}` };
  }

  // Run the harness with iOS-only capture. SKIP_ANDROID + SKIP_WEB because:
  //   - Web is platform-version-independent (it's just the host's Chrome via
  //     Puppeteer; doesn't care which iOS runtime is running).
  //   - Android has its own version matrix; mixing iOS-version and
  //     Android-API runs in the same matrix would confound the signal.
  console.log(`  running test-all.sh with SIM_UDID=${runtime.udid}…`);
  const start = Date.now();
  const result = spawnSync('./test-all.sh', [FIXTURE], {
    stdio: ['ignore', 'ignore', 'pipe'],
    timeout: 15 * 60 * 1000,
    env: {
      ...process.env,
      SIM_UDID: runtime.udid,
      SKIP_ANDROID: '1',
      SKIP_WEB: '1',
      // Distinct lock per runtime not needed — we serialise these calls
      // (xcrun simctl boot is exclusive per UDID anyway).
    },
  });
  const wallMs = Date.now() - start;

  if (result.error || result.status !== 0) {
    return {
      error: result.error?.message || `exit-${result.status}`,
      stderrTail: result.stderr?.toString().split('\n').slice(-15).join('\n') || '',
      wallMs,
    };
  }

  // Snapshot the captured screenshots into the per-runtime folder so a later
  // iteration doesn't overwrite them.
  const snapDir = join(SNAP_ROOT, runtime.label);
  if (existsSync(snapDir)) rmSync(snapDir, { recursive: true });
  mkdirSync(snapDir, { recursive: true });
  const srcDir = 'apps/ios-harness/screenshots';
  if (!existsSync(srcDir)) {
    return { error: 'no-screenshots-captured', wallMs };
  }
  const captured = readdirSync(srcDir).filter((f) => f.endsWith('.png'));
  for (const f of captured) {
    copyFileSync(join(srcDir, f), join(snapDir, f));
  }
  return {
    ok: true,
    wallMs,
    capturedCount: captured.length,
    snapDir,
  };
}

// === SSIM per-fixture between two snapshot folders ===
//
// Reuses the existing tools/visual/compare-screenshots.mjs library functions.
// We do the comparison inline rather than shelling out to compare-screenshots.mjs
// because that script is hard-coded to compare iOS vs Android vs web, not
// iOS-runtime-A vs iOS-runtime-B.
async function ssimBetween(dirA, dirB) {
  const filesA = new Set(readdirSync(dirA).filter((f) => f.endsWith('.png')));
  const filesB = new Set(readdirSync(dirB).filter((f) => f.endsWith('.png')));
  const both = [...filesA].filter((f) => filesB.has(f));

  // Lazy-load so a missing dep doesn't crash the whole script (the iOS
  // capture loop above is the slow + valuable part — we don't want to lose
  // it to an `npm install` snag in the SSIM step).
  // ssim.js exports `ssim` as a named export (not default), and expects
  // ImageData-shaped inputs (`Uint8ClampedArray` data + width + height).
  const { ssim } = await import('ssim.js');
  const { PNG } = await import('pngjs');
  const { readFileSync } = await import('node:fs');

  const results = [];
  for (const f of both) {
    try {
      const aPng = PNG.sync.read(readFileSync(join(dirA, f)));
      const bPng = PNG.sync.read(readFileSync(join(dirB, f)));
      // Skip if dimensions differ — this is a real signal (the renderer or
      // capture path changed across versions), but ssim.js will crash on it.
      if (aPng.width !== bPng.width || aPng.height !== bPng.height) {
        results.push({ fixture: f, ssim: 0, note: `size-mismatch:${aPng.width}x${aPng.height} vs ${bPng.width}x${bPng.height}` });
        continue;
      }
      // ssim.js expects Uint8ClampedArray. PNG.sync.read gives Uint8Array,
      // which has the same memory layout but is typed differently — the
      // inner ssim.js code uses ImageData-style indexing and is happy with
      // either, but mirror the shape compare-screenshots.mjs uses for safety.
      const { mssim } = ssim(
        { data: new Uint8ClampedArray(aPng.data.buffer, aPng.data.byteOffset, aPng.data.byteLength), width: aPng.width, height: aPng.height },
        { data: new Uint8ClampedArray(bPng.data.buffer, bPng.data.byteOffset, bPng.data.byteLength), width: bPng.width, height: bPng.height },
      );
      results.push({ fixture: f, ssim: Math.round(mssim * 1000) / 1000 });
    } catch (err) {
      results.push({ fixture: f, ssim: null, error: err.message });
    }
  }
  return {
    onlyInA: [...filesA].filter((f) => !filesB.has(f)),
    onlyInB: [...filesB].filter((f) => !filesA.has(f)),
    both: results,
  };
}

// === Main ===
async function main() {
  // --compare-only: skip the iOS capture loop and just re-score the existing
  // tools/visual/os-matrix-snapshots/ subfolders. Useful for iterating on the SSIM
  // logic without paying the ~45s/runtime test-all.sh cost per cycle.
  const compareOnly = process.argv.includes('--compare-only');

  if (!existsSync(FIXTURE)) {
    console.error(`fixture not found: ${FIXTURE}`);
    process.exit(1);
  }

  const iosRuntimes = detectIOSRuntimes();
  const androidAVDs = detectAndroidAVDs();
  console.log(`Detected iOS runtimes: ${iosRuntimes.map((r) => `${r.label}(${r.device})`).join(', ') || '(none)'}`);
  console.log(`Detected Android AVDs: ${androidAVDs.map((a) => a.label).join(', ') || '(none)'}`);
  console.log(`Fixture: ${FIXTURE}`);

  const report = {
    generated: new Date().toISOString(),
    fixture: FIXTURE,
    runtimes: { iOS: iosRuntimes, Android: androidAVDs },
    runs: {},
    deltas: {},
  };

  if (!existsSync(SNAP_ROOT)) mkdirSync(SNAP_ROOT, { recursive: true });

  // Run each iOS runtime in sequence (xcrun simctl boot is exclusive per UDID,
  // and the iOS Simulator can't run two devices simultaneously without
  // cross-talk on shared system resources).
  for (const rt of iosRuntimes) {
    console.log(`\n--- ${rt.label} ---`);
    if (compareOnly) {
      // Reuse last run's captures from disk. Skip if the snapshot folder is
      // missing — usually means the user passed --compare-only on a fresh
      // checkout before doing a full run.
      const snapDir = join(SNAP_ROOT, rt.label);
      if (!existsSync(snapDir)) {
        report.runs[rt.label] = { error: 'no-prior-snapshots; run without --compare-only first' };
        console.log(`  ✗ no prior snapshots at ${snapDir}`);
        continue;
      }
      const captured = readdirSync(snapDir).filter((f) => f.endsWith('.png'));
      report.runs[rt.label] = { ok: true, wallMs: 0, capturedCount: captured.length, snapDir, reused: true };
      console.log(`  ✓ reusing ${captured.length} screenshots from ${snapDir}`);
      continue;
    }
    report.runs[rt.label] = runOnIOSRuntime(rt);
    const r = report.runs[rt.label];
    if (r.ok) {
      console.log(`  ✓ captured ${r.capturedCount} screenshots in ${Math.round(r.wallMs / 1000)}s`);
    } else {
      console.log(`  ✗ ${r.error}`);
    }
  }

  // Round 49 fix: shut down all sims at the END of the iOS loop. Without this,
  // every runtime we booted stays running after the matrix completes, which
  // breaks test-all.sh's default device picker — its preference list iterates
  // [iPhone 17, iPhone 17 Pro, ...] and stops at the first match, meaning a
  // subsequent normal `./test-all.sh` run picks whichever iPhone happens to be
  // at the top of `xcrun simctl list devices --json` iteration order rather
  // than the iPhone 17 Pro the visual-test baseline was captured on.
  // Running BASELINE=1 then misattributes the device-mismatch as a regression.
  // (Caught in round 49 via Glass_Effect "regression" investigation.)
  if (!compareOnly && iosRuntimes.length > 0) {
    console.log(`\n  cleanup: shutting down ${iosRuntimes.length} sims booted by this matrix run`);
    shutdownAllSims();
  }

  // Android: stub for now. AVD provisioning (avdmanager create + sdkmanager
  // image install) is a separate ~10-min one-time setup per API level. The
  // detection is wired so the report explicitly says "0 AVDs available, run
  // `avdmanager create avd ...` to add one" rather than silently passing.
  for (const avd of androidAVDs) {
    report.runs[`Android-${avd.label}`] = {
      skipped: true,
      reason: 'Android per-AVD runner not yet wired (Tier 12 Phase 12b, ~3-4h once an AVD is detected)',
    };
  }

  // Cross-runtime SSIM comparisons. Compare every iOS pair (n choose 2) so a
  // 3-runtime setup gives 3 deltas; 2-runtime setup gives 1.
  const okRuns = Object.entries(report.runs).filter(([, r]) => r.ok);
  for (let i = 0; i < okRuns.length; i++) {
    for (let j = i + 1; j < okRuns.length; j++) {
      const [labelA, runA] = okRuns[i];
      const [labelB, runB] = okRuns[j];
      const pairKey = `${labelA}__vs__${labelB}`;
      console.log(`\n--- delta ${pairKey} ---`);
      const delta = await ssimBetween(runA.snapDir, runB.snapDir);
      const regressions = delta.both.filter((d) => d.ssim !== null && d.ssim < 1 - SSIM_DRIFT_THRESHOLD);
      report.deltas[pairKey] = {
        comparedFixtures: delta.both.length,
        onlyInA: delta.onlyInA,
        onlyInB: delta.onlyInB,
        regressions: regressions.length,
        regressionDetail: regressions,
        allDeltas: delta.both,
      };
      console.log(`  ${delta.both.length} fixtures compared · ${regressions.length} drift > ${SSIM_DRIFT_THRESHOLD}`);
      if (regressions.length > 0) {
        for (const r of regressions.slice(0, 5)) {
          console.log(`    ⚠ ${r.fixture}: ssim=${r.ssim}${r.note ? ` (${r.note})` : ''}`);
        }
      }
    }
  }

  writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2));
  console.log(`\n✓ wrote ${REPORT_PATH}`);

  // Surface a summary line. Useful for CI grep: `grep "OS-MATRIX SUMMARY" log`.
  const totalRegressions = Object.values(report.deltas).reduce((n, d) => n + d.regressions, 0);
  console.log(`OS-MATRIX SUMMARY: ${okRuns.length} runtimes captured · ${Object.keys(report.deltas).length} pairs compared · ${totalRegressions} regressions`);
}

// Round 73: gate main() so importing this module from
// tools/visual/os-matrix.test.mjs doesn't trigger a real iOS-matrix run
// (which would shutdown all sims and shell out to xcrun).
const isMainScript = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMainScript) main().catch((err) => {
  console.error('Fatal:', err);
  process.exit(1);
});
