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

test('Step 5b feeds BOTH natives in composed mode behind a global device lock', () => {
  assert.match(src, /feed-android\.mjs" --fixtures "\$PERTEST_DIR" --composed/, 'android composed feeder dropped');
  assert.match(src, /feed-ios\.mjs" --fixtures "\$PERTEST_DIR" --composed/, 'ios composed feeder dropped');
  // The single emulator + single simulator are process-global singletons, so
  // parallel section-runners MUST serialize native feeding through one lock.
  assert.match(src, /DEV_LOCK="\/tmp\/titan-native-device\.lock"/, 'global device lock dropped');
  assert.match(src, /_dev_acquire/, 'device-lock acquire helper dropped');
  assert.match(src, /_dev_release/, 'device-lock release helper dropped');
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
