#!/usr/bin/env node
//
// tools/titan/wpt-white-canvas.test.mjs — the corpus-v4 WHITE-canvas
// mode-split pins (TITAN-WHITE lane).
//
// THE CONTRACT UNDER TEST: from corpus-v4 the WPT capture canvas is WHITE
// on every surface that feeds a browser-ref diff — the Chromium ref render
// AND all three platform harness canvases — while the property-fixture
// (327-pair) capture path keeps the historical dark #1A1A2E stage
// byte-identically. WPT reftests are authored against the spec-default
// white page: white ink (borders/backgrounds — e.g. the abspos-autopos
// `border: solid white` frames) must VANISH into the canvas identically on
// both sides of the diff, which the dark stage broke (a systematic
// penalty; the measured ~5,200px of white-frame ink over the 6
// abspos-autopos tests was the single largest white-ink lever).
//
// WHY SOURCE SCANS: the split lives partly in harness-app code
// (apps/web-harness TSX + HTML, apps/*-harness native canvases) that no
// JVM/XCTest/vitest suite loads, and a half-flipped state — one platform
// white, another dark — is the exact failure mode that silently ruins a
// corpus run. These scans pin each platform's split from ONE place so any
// drift fails `node --test tools/titan/*.test.mjs` (the same style as
// inject-wpt-block.test.mjs's stale-path defect-1 pin). The pure decision
// helpers behind the native splits are ADDITIONALLY unit-pinned in their
// own suites (Compose WptCanvasBackgroundTest.kt, SwiftUI
// WPTCaptureModeTests.swift) — these scans hold the wiring, those hold
// the semantics.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');

/** Read a repo file as UTF-8 (throws loudly if the path moved — a moved
 *  canvas file must fail this suite, not silently skip its pins). */
const src = (rel) => readFileSync(join(REPO_ROOT, rel), 'utf8');

// ── browser-ref capture (the comparison target) ─────────────────────────────

test('capture-browser-ref: white CANVAS_BG + cache keyed by CANVAS_REV', () => {
  const s = src('tools/titan/capture-browser-ref.mjs');
  // The ref canvas is the corpus-v4 white.
  assert.match(s, /export const CANVAS_BG = '#FFFFFF'/, 'ref canvas must be white');
  // The cache revision segment keeps pre-v4 dark refs out of white diffs.
  assert.match(s, /export const CANVAS_REV = 'white'/, 'cache revision segment missing');
  assert.match(s, /join\(REFS_ROOT, wptRef, CANVAS_REV, section/, 'cachePathFor must key on CANVAS_REV');
});

test('run-titan.sh + section-runner.sh point --refs-root at the white revision', () => {
  // Both orchestrators derive the refs root independently of cachePathFor —
  // the /white segment must match CANVAS_REV or every diff sees no ref.
  for (const sh of ['tools/titan/run-titan.sh', 'tools/titan/section-runner.sh']) {
    const s = src(sh);
    assert.match(s, /--refs-root "\$WPT_DIR\/refs\/\$WPT_REF\/white"/, `${sh}: refs-root missing /white`);
    assert.doesNotMatch(s, /--refs-root "\$WPT_DIR\/refs\/\$WPT_REF" /, `${sh}: un-segmented refs-root resurfaced`);
  }
});

test('inject-wpt-block pads/stitches WPT diffs on the white canvas', () => {
  const s = src('tools/titan/inject-wpt-block.mjs');
  // padToCanvas extends with white (sharp background object).
  assert.match(s, /background: \{ r: 0xFF, g: 0xFF, b: 0xFF, alpha: 1 \}/, 'padToCanvas must pad white');
  // The stitch canvas initialises to white, not #1A1A2E.
  assert.doesNotMatch(s, /composed\.data\[i\]\s*=\s*0x1A/, 'stitch still initialises the dark canvas');
});

// ── web harness ─────────────────────────────────────────────────────────────

test('web: WPT canvases are white; the 327-pair canvas keeps the dark stage', () => {
  const gallery = src('apps/web-harness/src/ui/CaptureGallery.tsx');
  // The WPT canvas (…?wpt=1) paints white…
  const wpt = gallery.slice(gallery.indexOf('const wptCanvasStyle'));
  assert.match(wpt, /background: '#FFFFFF'/, 'wptCanvasStyle must be white');
  // …while the legacy per-component canvas (the committed-baseline
  // contract) still paints the dark stage byte-identically.
  const legacy = gallery.slice(gallery.indexOf('const canvasStyle'), gallery.indexOf('const wptCanvasStyle'));
  assert.match(legacy, /background: '#1A1A2E'/, '327-pair canvasStyle must stay dark');

  const composed = src('apps/web-harness/src/ui/ComposedCaptureGallery.tsx');
  // Composed default = the white ref canvas; author body background wins.
  assert.match(composed, /const CANVAS_BG_DEFAULT = '#FFFFFF'/, 'composed default must be white');
  assert.doesNotMatch(composed, /background: '#1A1A2E'/, 'composed gallery still paints a dark canvas');
});

test('web: index.html flips the stage white only under the wpt body classes', () => {
  const html = src('apps/web-harness/index.html');
  // The WPT stage rule exists and covers both WPT modes + #root.
  assert.match(html, /body\.wpt-mode, body\.wpt-mode #root,\s*\n\s*body\.wpt-composed-mode, body\.wpt-composed-mode #root \{\s*\n\s*background: #fff;/,
    'wpt-mode white stage rule missing');
  // The plain capture-mode stage (327-pair) stays dark.
  assert.match(html, /body\.capture-mode #root \{[^}]*background: #1a1a2e;/s, '327-pair capture stage must stay dark');
});

// ── Android (Compose runtime helper + harness wiring) ───────────────────────

test('compose: WPT_CANVAS_BACKGROUND is white and the harness routes through the split', () => {
  const runtime = src('runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/WptCaptureMode.kt');
  // The runtime owns the white constant + the pure mode-split helper.
  assert.match(runtime, /val WPT_CANVAS_BACKGROUND = Color\(0xFFFFFFFF\)/, 'compose white constant missing');
  assert.match(runtime, /fun captureCanvasBackground\(wptCaptureMode: Boolean, defaultBackground: Color\): Color =\s*\n\s*if \(wptCaptureMode\) WPT_CANVAS_BACKGROUND else defaultBackground/,
    'compose mode-split helper missing/changed');

  const harness = src('apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt');
  // The per-component canvas consumes the split (not a bare dark bg).
  assert.match(harness, /captureCanvasBackground\(LocalWptCaptureMode\.current, CaptureCanvasBg\)/,
    'Android per-component canvas must route through the split');
  // The dark stage constant survives for the 327-pair path…
  assert.match(harness, /private val CaptureCanvasBg\s*=\s*Color\(0xFF1A1A2E\)/, 'Android dark stage constant lost');
  // …and the WPT-only composed fallback is the runtime white.
  assert.match(harness, /\?: return WPT_CANVAS_BACKGROUND/, 'composed body-root-less fallback must be white');
  assert.match(harness, /return bg \?: WPT_CANVAS_BACKGROUND/, 'composed no-background fallback must be white');
});

// ── iOS (SwiftUI runtime helper + harness wiring) ───────────────────────────

test('swiftui: WPTCanvas is white and the harness routes through the split', () => {
  const runtime = src('runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/WPTCaptureMode.swift');
  // The runtime owns the white constant + the pure mode-split helper.
  assert.match(runtime, /public static let background = Color\(white: 1\)/, 'swiftui white constant missing');
  assert.match(runtime, /wptCaptureMode \? background : defaultBackground/, 'swiftui mode-split helper missing/changed');

  const harness = src('apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift');
  // The per-component canvas reads the environment flag and consumes the
  // split for BOTH branches (out-of-flow card + block flow).
  assert.match(harness, /@Environment\(\\\.wptCaptureMode\) private var wptCaptureMode/, 'iOS canvas must read wptCaptureMode');
  assert.match(harness, /WPTCanvas\.captureBackground\(wptCaptureMode: wptCaptureMode,\s*\n\s*defaultBackground: CaptureCanvas\.backgroundColor\)/,
    'iOS canvas must route through the split');
  // Three canvasBackground consumers: the per-component canvas's two
  // branches (out-of-flow card + block flow) and the composed canvas's own
  // resolved body-root/WPT-white background.
  const uses = harness.match(/\.background\(canvasBackground\)/g) ?? [];
  assert.equal(uses.length, 3, 'all three canvas paints must use the resolved background');
  // The dark stage constant survives for the 327-pair path…
  assert.match(harness, /blue:\s*0x2E \/ 255\.0/, 'iOS dark stage constant lost');
  // …and the WPT-only composed fallback is the runtime white.
  assert.match(harness, /else \{ return WPTCanvas\.background \}/, 'iOS composed fallback must be white');
});
