#!/usr/bin/env node
//
// Pins for tools/titan/section-runner.sh — the per-section WPT orchestrator.
//
// section-runner.sh is bash, so these are source-scan pins in the style of
// run-titan.test.mjs / inject-wpt-block.test.mjs: they assert the
// load-bearing lines a refactor is most likely to drop. The subject is the
// "honest composed 3-platform capture" re-plumb (Big Rock #1): before it,
// section-runner captured web-only and hardcoded EMPTY_DIR for the iOS /
// Android inject dirs, so every per-section manifest showed native n/a. The
// re-plumb (a) runs the CANONICAL in-place web driver in WPT_COMPOSED mode,
// (b) added Step 5b native feeders behind a global device lock, and (c)
// pointed inject at the per-section native capture dirs.
//
// The companion fix lives in the three puppeteer capture drivers: under
// headless:'new' on this toolchain the GPU raster path deadlocks
// Page.captureScreenshot, so every driver must launch with --disable-gpu or
// the web capture silently yields "0 screenshots". Pinned below too.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';

const src = await fs.readFile(new URL('./section-runner.sh', import.meta.url), 'utf8');

test('web capture runs the CANONICAL in-place driver in WPT_COMPOSED mode', () => {
  // Must invoke apps/web-harness/capture-screenshots.mjs from $PROJECT_ROOT —
  // NOT the rsync'd copy via `cd "$WEB_ROOT" && node capture-screenshots.mjs`.
  // The copy sits two dirs deeper, where the driver's relative import of
  // ../../tools/titan/safe-name.mjs resolves to a nonexistent path and the
  // whole capture crashes at import time (the "0 web screenshots" bug).
  assert.match(
    src,
    /cd "\$PROJECT_ROOT" && WPT_MODE=1 WPT_COMPOSED=1 node apps\/web-harness\/capture-screenshots\.mjs/,
    'web capture must run the canonical in-place driver with WPT_COMPOSED=1',
  );
  assert.doesNotMatch(
    src,
    /cd "\$WEB_ROOT" && [^\n]*node capture-screenshots\.mjs/,
    'must NOT run the rsync-copied driver (breaks the safe-name.mjs relative import)',
  );
});

test('Step 1 extraction engages post-load augmentation itself (RC-A5a)', () => {
  // extract-fixture.mjs only augments wall-tagged tests (requires-script-
  // mutation / requires-script-driven-scroll) when POST_LOAD_EXTRACT=1 is in
  // its environment. section-runner used to rely on the CALLER's ambient
  // shell for that, so a fresh section run silently re-extracted those tests
  // static-only — clobbering any earlier post-load stamp and leaving e.g.
  // css-flexbox/abspos/dynamic-align-self-001 rendering its pre-mutation
  // state (postLoadExtracted:false). The env must be set on the invocation.
  assert.match(
    src,
    /POST_LOAD_EXTRACT=1 (?:[A-Z_]+=\S+ )*node "\$TITAN_DIR\/extract-fixture\.mjs"/,
    'Step 1 must run extract-fixture.mjs with POST_LOAD_EXTRACT=1 inline',
  );
});

test('Step 1 extraction engages the bidi bake itself (wave-30 fix-T3)', () => {
  // Same contract, same failure mode as POST_LOAD_EXTRACT above: the bidi
  // bake is opt-in (`--bidi-bake` / BIDI_BAKE=1, see extract-fixture main()),
  // and left to the caller's ambient shell it engaged only by accident. A
  // fresh `section-runner.sh selectors` without it silently reproduced
  // dir-selector-change-003/004 in LOGICAL order where wave29-final had the
  // measured visual order, with no marker to explain the drift.
  assert.match(
    src,
    /(?:^|\s)BIDI_BAKE=1 (?:[A-Z_]+=\S+ )*node "\$TITAN_DIR\/extract-fixture\.mjs"/m,
    'Step 1 must run extract-fixture.mjs with BIDI_BAKE=1 inline',
  );
  // Both pins ride the SAME invocation — a second, separate extract call
  // would re-extract and clobber, so pin them to one line.
  const line = src.split('\n').find((l) => l.includes('node "$TITAN_DIR/extract-fixture.mjs"'));
  assert.ok(line, 'Step 1 extract invocation not found');
  assert.ok(line.includes('POST_LOAD_EXTRACT=1') && line.includes('BIDI_BAKE=1'),
    `both env pins must be on the one extract invocation, got: ${line}`);
});

test('Step 5b feeds BOTH natives in composed mode through per-device pool slots', () => {
  assert.match(src, /feed-android\.mjs" --fixtures "\$PERTEST_DIR" --composed/, 'android composed feeder dropped');
  assert.match(src, /feed-ios\.mjs" --fixtures "\$PERTEST_DIR" --composed/, 'ios composed feeder dropped');
  // Devices are a POOL: each parallel section-runner acquires one free booted
  // device per platform via a per-device mkdir-atomic slot lock (stale-healed).
  // With one emulator + one simulator this degrades to the old serialization;
  // with a provisioned fleet N sections feed natives concurrently.
  assert.match(src, /POOL_ROOT="\/tmp\/titan-device-pool"/, 'device pool root dropped');
  assert.match(src, /_pool_acquire/, 'pool acquire helper dropped');
  assert.match(src, /_pool_release/, 'pool release helper dropped');
  // Feeders must target their acquired slot, not whatever device is default.
  assert.match(src, /--udid "\$ANDROID_DEV"/, 'android feeder must target the acquired slot');
  assert.match(src, /--udid "\$IOS_DEV"/, 'ios feeder must target the acquired slot');
});

test('Step 5b runs the two native feeders CONCURRENTLY (independent hardware)', () => {
  // Android and iOS are separate devices — feeding them serially under one
  // lock wasted half the native wall-clock. Both feeders launch as background
  // jobs and are awaited; a wedge/timeout on one platform must not starve the
  // other (each has its own wait + warn).
  assert.match(src, /FEED_ANDROID_PID=\$!/, 'android feeder no longer backgrounded');
  assert.match(src, /FEED_IOS_PID=\$!/, 'ios feeder no longer backgrounded');
  const waits = src.match(/wait "\$FEED_(ANDROID|IOS)_PID"/g) ?? [];
  assert.equal(waits.length, 2, `both feeder pids must be awaited, found ${waits.length}`);
});

test('provisioned devices skip in-feeder builds (concurrent-build collision guard)', () => {
  // Two simultaneous gradle installs / xcodebuilds in one checkout can
  // collide. provision-devices.sh pre-installs the app on every pool device
  // and writes marker files; Step 5b must consult them and pass the
  // build-skipping flags so concurrent feeds never trigger parallel builds.
  assert.match(src, /provisioned-android/, 'android provision marker not consulted');
  assert.match(src, /provisioned-ios/, 'ios provision marker not consulted');
  assert.match(src, /--skip-install/, 'android prebuilt flag dropped');
  assert.match(src, /--no-build/, 'ios prebuilt flag dropped');
});

test('inject points at the per-section native dirs, never EMPTY_DIR', () => {
  // Both the main inject (Step 7) and the recovery inject (Step 7.5) must
  // pass the real per-section capture dirs so native columns are scored.
  const iosHits = src.match(/--ios-dir "\$IOS_SHOTS_DIR"/g) ?? [];
  const androidHits = src.match(/--android-dir "\$ANDROID_SHOTS_DIR"/g) ?? [];
  assert.ok(iosHits.length >= 2, `expected --ios-dir on main+recovery inject, found ${iosHits.length}`);
  assert.ok(androidHits.length >= 2, `expected --android-dir on main+recovery inject, found ${androidHits.length}`);
  assert.doesNotMatch(src, /--ios-dir "\$EMPTY_DIR"/, 'native inject must not be hardcoded to EMPTY_DIR');
  assert.doesNotMatch(src, /--android-dir "\$EMPTY_DIR"/, 'native inject must not be hardcoded to EMPTY_DIR');
});

test('per-section native capture dirs are defined', () => {
  assert.match(src, /IOS_SHOTS_DIR="\$WORK_DIR\/ios-screenshots"/, 'IOS_SHOTS_DIR def dropped');
  assert.match(src, /ANDROID_SHOTS_DIR="\$WORK_DIR\/android-screenshots"/, 'ANDROID_SHOTS_DIR def dropped');
});

test('provision-devices.sh writes the exact markers Step 5b consumes', async () => {
  // Producer/consumer contract: section-runner greps
  // $POOL_ROOT/provisioned-{android,ios} to decide --skip-install/--no-build.
  // If either side renames the marker, concurrent feeds silently fall back to
  // in-feeder builds — reintroducing the parallel-build collision.
  const prov = await fs.readFile(new URL('./provision-devices.sh', import.meta.url), 'utf8');
  assert.match(prov, /POOL_ROOT="\/tmp\/titan-device-pool"/, 'provision pool root must match Step 5b');
  assert.match(prov, /provisioned-android/, 'android marker dropped from provision');
  assert.match(prov, /provisioned-ios/, 'ios marker dropped from provision');
  // Extra emulator instances must be -read-only clones of ONE AVD (identical
  // rendering config = pixel parity across the pool) and headless (silent).
  assert.match(prov, /-read-only/, 'extra emulators must be -read-only instances of the same AVD');
  assert.match(prov, /-no-window/, 'extra emulators must be headless');
  // Two hangs/failures that each cost a full provisioning run once:
  // (1) `simctl bootstatus -b` blocks FOREVER on some freshly-created sims
  //     (hung 7 hours) — only bounded polling is allowed;
  // (2) emulator launch errors went to /dev/null, so a refused instance
  //     ("run all emulators with -read-only") was invisible — launches must
  //     log to a file.
  // (line-anchored + comment-excluded: the script legitimately DOCUMENTS the
  //  ban in a comment; only a real, non-comment invocation may trip this)
  assert.doesNotMatch(prov, /^(?!\s*#)[^\n]*bootstatus[^\n]*-b\b/m, 'unbounded `simctl bootstatus -b` is banned (7h hang)');
  assert.match(prov, /emulator-launch-\$i\.log/, 'emulator launches must log to a file, not /dev/null');
  // (3) a zombie sim wedged `simctl terminate` for 38 minutes: EVERY
  //     per-device simctl/adb mutation must run under the _bounded watchdog
  //     (macOS has no `timeout`), so no single sick device can hang the run.
  assert.match(prov, /_bounded\(\)/, 'the _bounded watchdog helper was dropped');
  assert.match(prov, /_bounded \d+ xcrun simctl install/, 'simctl install must be watchdogged');
  assert.match(prov, /_bounded \d+ xcrun simctl terminate/, 'simctl terminate must be watchdogged');
  assert.match(prov, /_bounded \d+ "\$ADB" -s "\$s" install/, 'adb install must be watchdogged');
});

for (const driver of [
  '../../apps/web-harness/capture-screenshots.mjs',
  '../../apps/web-harness/capture-screenshots-hires.mjs',
  './capture-browser-ref.mjs',
]) {
  test(`${driver} launches puppeteer with --disable-gpu (GPU-raster deadlock fix)`, async () => {
    const s = await fs.readFile(new URL(driver, import.meta.url), 'utf8');
    assert.match(s, /'--disable-gpu'/, `${driver} must force CPU raster or Page.captureScreenshot deadlocks`);
  });
}
