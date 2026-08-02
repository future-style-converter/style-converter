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
  // The cache revision segment keeps refs from every EARLIER contract (the
  // line-height-less black-ink scratch refs at /white-black-ink-font/, v4.0
  // white-ink refs at /white/, pre-v4 dark refs at the un-segmented path,
  // and now the CSS-padded /white-black-ink-font-lh tree) out of the live
  // diff. '…-imgpad' == corpus-v4.1 typography PLUS the wave-25 CAL-RC1
  // image-space frame, which moves every abspos/fixed overlay back into
  // alignment with the in-flow content it annotates.
  assert.match(s, /export const CANVAS_REV = 'white-black-ink-font-lh-imgpad'/, 'cache revision segment missing');
  assert.match(s, /join\(REFS_ROOT, wptRef, CANVAS_REV, section/, 'cachePathFor must key on CANVAS_REV');
});

test('the shell --refs-root literal is normalised by inject-wpt-block, not pinned to CANVAS_REV', () => {
  // Both orchestrators pass a LITERAL refs root. Through wave-24 that literal
  // had to be edited in lock-step with CANVAS_REV or the scorer silently
  // diffed against the previous contract's refs — a two-file coupling with no
  // mechanical enforcement outside this test. wave-25 CAL-RC1 removes the
  // coupling instead of re-asserting it: inject-wpt-block.mjs rewrites a
  // KNOWN-STALE canvas-rev tail to the live rev (normalizeRefsRoot). So what
  // this test guards now is that the literal the scripts DO pass is one the
  // normaliser recognises — an unrecognised segment is left alone, which
  // would be the silent-stale-ref failure all over again.
  const stale = /export const KNOWN_STALE_CANVAS_REVS = \[([\s\S]*?)\]/
    .exec(src('tools/titan/inject-wpt-block.mjs'))?.[1] ?? '';
  for (const sh of ['tools/titan/run-titan.sh', 'tools/titan/section-runner.sh']) {
    const s = src(sh);
    const seg = /--refs-root "\$WPT_DIR\/refs\/\$WPT_REF\/([A-Za-z0-9._-]+)"/.exec(s)?.[1];
    assert.ok(seg, `${sh}: --refs-root must carry a canvas-rev segment`);
    // Either it already IS the live rev, or the normaliser knows how to
    // upgrade it. Nothing else may ship.
    assert.ok(seg === 'white-black-ink-font-lh-imgpad' || stale.includes(`'${seg}'`),
      `${sh}: refs-root segment '${seg}' is neither the live rev nor a KNOWN_STALE_CANVAS_REVS entry`);
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
  // …and the composed background routes through the wave-15 alpha-composite
  // helper (composedCanvasBackground: nil -> white; alpha<1 -> source-over
  // white; opaque -> identity) instead of the old raw ?: white fallbacks —
  // the raw path painted a sampled rgba(0,0,0,0) body VERBATIM as the
  // canvas (the measured Android 0.217 row).
  assert.match(harness, /import com\.styleconverter\.runtime\.core\.renderer\.composedCanvasBackground/,
    'Android composed resolver must import the alpha-composite helper');
  assert.match(harness, /return composedCanvasBackground\(bg\)/,
    'Android composed background must route through composedCanvasBackground');
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
  // …and the composed background routes through the wave-15 alpha-composite
  // helper (WPTCanvas.composedBackground: nil -> white; alpha<1 ->
  // source-over white; opaque -> identity) — the old direct fallback let a
  // sampled rgba(0,0,0,0) body paint verbatim (the measured iOS 0.000 row).
  assert.match(harness, /return WPTCanvas\.composedBackground\(resolved: resolved\)/,
    'iOS composed background must route through composedBackground');
});

// ── corpus-v4.1: the BLACK default-ink sub-boundary ─────────────────────────
//
// Within the v4 white-canvas era the WPT default TEXT INK flipped from the
// harness near-white family to the spec BLACK. Real WPT pages paint default
// prose in the UA `color: CanvasText` black; through corpus-v4.0 BOTH sides
// of every diff hid default-ink prose on the white canvas (ref injection
// `color:#fff`, web body #eee, native #eee-family bottom-outs) — so prose
// tests matched VACUOUSLY (neither side showed the text). The flip must move
// all four surfaces TOGETHER, WPT mode only; a half-flipped state (black ref
// prose vs invisible near-white captures, or vice versa) is an asymmetric
// text penalty that silently ruins a corpus run — exactly the failure mode
// these one-place-per-surface scans exist to catch. The pure ink-decision
// semantics are additionally unit-pinned in Compose WptCanvasBackgroundTest.kt
// and SwiftUI WPTCaptureModeTests.swift; these scans hold the wiring.

test('corpus-v4.1: the ref injection paints spec-black default ink', () => {
  const s = src('tools/titan/capture-browser-ref.mjs');
  // The injected zero-specificity body default is the UA CanvasText black.
  assert.match(s, /min-height: 100vh; color: #000;/, 'ref injection must pin the spec-black default ink');
  // The v4.0 white-ink injection must never resurface.
  assert.doesNotMatch(s, /min-height: 100vh; color: #fff;/, 'v4.0 white default ink resurfaced in the ref injection');
});

// corpus-v4.1 FONT half: black ink made default prose VISIBLE, exposing the
// face divergence — the ref rendered it in Chromium's default SERIF while
// all three harnesses render the bundled Inter sans, so wrap points (and
// everything below the prose) shifted vertically (the a98rgb cluster's
// whole failure; its color math is pixel-exact). The ref injection pins
// the harness font stack at zero specificity AND embeds the harness's own
// Inter faces so the stack's first entry actually resolves. The natives
// need no font hook: Compose (InterFontFamily) and iOS (registered
// "Inter") already default to the bundled Inter UNCONDITIONALLY — WPT and
// non-WPT modes alike — so only ref + web carry explicit pins.

test('corpus-v4.1 font: the ref injection pins the harness Inter stack + embeds the faces', () => {
  const s = src('tools/titan/capture-browser-ref.mjs');
  // The stack constant exists and leads with the bundled Inter.
  assert.match(s, /export const REF_FONT_STACK =\s*\n\s*"'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif"/,
    'REF_FONT_STACK missing/changed');
  // The :where(body) injection consumes it (zero specificity — author
  // font rules in the ref must still win, like a UA default). The block no
  // longer closes on this declaration — the v4.1 line-height pin follows it
  // (its own scan below holds that ordering).
  assert.match(s, /font-family: \$\{REF_FONT_STACK\};/, 'ref injection must apply REF_FONT_STACK');
  // The harness's own Inter faces are embedded so 'Inter' resolves in the
  // ref browser (raw WPT HTML has no @font-face of its own).
  assert.match(s, /\$\{await interFontFaceCss\(\)\}/, 'ref injection must embed the Inter @font-face preamble');
  assert.match(s, /\['Inter-Regular\.ttf', 400\],\s*\n\s*\['Inter-Bold\.ttf', 700\],/,
    'embedded weights must cover UA-reachable prose (400 + 700)');
});

test('corpus-v4.1 font: ref stack and web harness stack are byte-identical', () => {
  // The whole point of the pin: ref prose and harness prose hit the SAME
  // face and wrap at the SAME points. Extract both stacks from source and
  // compare them as strings so either side drifting fails here.
  const ref = src('tools/titan/capture-browser-ref.mjs');
  const refStack = /export const REF_FONT_STACK =\s*\n\s*"([^"]+)"/.exec(ref)?.[1];
  assert.ok(refStack, 'REF_FONT_STACK not found in capture-browser-ref.mjs');
  const html = src('apps/web-harness/index.html');
  // The harness's global default stack (html, body rule)…
  assert.ok(html.includes(`font-family: ${refStack};`),
    'web harness html/body font stack must equal REF_FONT_STACK');
  // …and the WPT-stage re-pin carry the same stack (the wpt-mode rule
  // re-states it so a future change to the global rule cannot silently
  // detach the WPT surface from the ref).
  const wptRule = /body\.wpt-mode, body\.wpt-mode #root,\s*\n\s*body\.wpt-composed-mode, body\.wpt-composed-mode #root \{[^}]*\}/s.exec(html)?.[0];
  assert.ok(wptRule, 'wpt-mode stage rule not found in index.html');
  assert.ok(wptRule.includes(`font-family: ${refStack};`),
    'wpt-mode stage rule must re-pin the REF_FONT_STACK');
});

test('corpus-v4.1 web: wpt body classes flip the default ink black; body keeps #eee', () => {
  const html = src('apps/web-harness/index.html');
  // The WPT stage rule carries the black ink alongside the white canvas.
  assert.match(html, /body\.wpt-composed-mode, body\.wpt-composed-mode #root \{\s*\n\s*background: #fff;\s*\n\s*color: #000;/,
    'wpt-mode black default-ink rule missing');
  // The base stage contract stays: body inherits #eee for the 327-pair path.
  assert.match(html, /color: #eee;/, 'the dark-stage body #eee contract must survive');

  // The placeholder span pins its own color, so the body rule alone is not
  // enough — the WPT_MODE branch must bottom out at opaque black too.
  const tsx = src('apps/web-harness/src/sdui/ComponentRenderer.tsx');
  assert.match(tsx, /: WPT_MODE\s*\n\s*\? '#000000'/, 'PlaceholderContent WPT_MODE black ink missing');
  // …while the dark-stage contrast pick survives verbatim (327 baselines).
  assert.match(tsx, /rgba\(237, 237, 237, 0\.7\)/, 'the dark-stage light contrast pick must survive');
});

test('corpus-v4.1 compose: WPT_DEFAULT_TEXT_INK is black and both bottom-outs route through the split', () => {
  const runtime = src('runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/WptCaptureMode.kt');
  // The runtime owns the black constant + the pure ink-split helper.
  assert.match(runtime, /val WPT_DEFAULT_TEXT_INK = Color\(0xFF000000\)/, 'compose black ink constant missing');
  assert.match(runtime, /fun defaultTextInk\(wptCaptureMode: Boolean, defaultInk: Color\): Color =\s*\n\s*if \(wptCaptureMode\) WPT_DEFAULT_TEXT_INK else defaultInk/,
    'compose ink-split helper missing/changed');

  const renderer = src('runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt');
  // The currentColor bottom-out consumes the split…
  assert.match(renderer, /inheritedColor \?\: defaultTextInk\(wptCaptureMode, DEFAULT_TEXT_COLOR\)/,
    'compose currentColor bottom-out must route through the ink split');
  // …and so does the no-color placeholder default.
  assert.match(renderer, /defaultTextInk\(LocalWptCaptureMode\.current, run \{/,
    'compose placeholder default must route through the ink split');
  // The dark-stage #eee contract survives for the 327-pair path.
  assert.match(renderer, /internal val DEFAULT_TEXT_COLOR = Color\(0xFFEEEEEE\)/, 'compose dark-stage #eee default lost');
});

test('corpus-v4.1 swiftui: WPTCanvas.textInk is black and both bottom-outs route through the split', () => {
  const runtime = src('runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/WPTCaptureMode.swift');
  // The runtime owns the black constant + the pure ink-split helper.
  assert.match(runtime, /public static let textInk = Color\(white: 0\)/, 'swiftui black ink constant missing');
  assert.match(runtime, /wptCaptureMode \? textInk : defaultInk/, 'swiftui ink-split helper missing/changed');

  const renderer = src('runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift');
  // Both currentColor bottom-out call sites (leading-text + leaf label)
  // route through the split — count them so a third un-routed consumer of
  // defaultTextColor can't slip back in.
  const routed = renderer.match(/WPTCanvas\.captureTextInk\(\s*\n\s*wptCaptureMode: wptCaptureMode,\s*\n\s*defaultInk: InheritedText\.defaultTextColor\)/g) ?? [];
  assert.equal(routed.length, 2, 'both iOS currentColor bottom-outs must route through the ink split');
  assert.doesNotMatch(renderer, /\? InheritedText\.defaultTextColor : nil/,
    'an iOS currentColor bottom-out bypasses the ink split');
  // The no-color PlaceholderLabel fallback is black in WPT mode…
  assert.match(renderer, /if wptCaptureMode \{ return WPTCanvas\.textInk \}/,
    'iOS PlaceholderLabel no-color fallback must be black in WPT mode');
  // …while the dark-stage 0.93-white default survives (327 baselines).
  const inherited = src('runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/InheritedText.swift');
  assert.match(inherited, /static let defaultTextColor = Color\(white: 0\.93\)/, 'swiftui dark-stage default ink lost');
});

// corpus-v4.1 LINE-HEIGHT leg (the third of the ink+font+line-height
// sub-boundary): with black ink and the shared Inter face landed, ref-vs-
// capture prose diverged ONLY in vertical rhythm — the ref's `line-height:
// normal` Inter paragraphs advanced 36px top-to-top (line box ~20px @16px)
// while every harness capture advanced 34px (the Round-4 composed ~18px
// calibration, tuned against the OLD default-serif ref), accumulating 2px
// per paragraph (20px over a 10-bar test — the whole css-color/css-break/
// css-flexbox collapse of the first v4.1 run). Neither side may depend on
// font-`normal` metrics: the ref injects an explicit unitless 1.25
// (REF_LINE_HEIGHT — 20px @16px, Chromium's measured natural Inter rhythm)
// and ALL harness composed calibrations pin the SAME 20px box. All pins are
// WPT-mode-gated (author-declared line-height still wins everywhere — the
// pin is a DEFAULT: :where zero specificity on ref, stylesheet-vs-inline on
// web, declared-value deferral on the natives); the dark-stage 327 path
// keeps its font-`normal`/1.2x line boxes byte-identically. A half-pinned
// state (ref at 20px, one platform still at 18px) re-opens the compounding
// rhythm drift — exactly what these one-place-per-surface scans catch.

test('corpus-v4.1 line-height: the ref injection pins a deterministic unitless 1.25', () => {
  const s = src('tools/titan/capture-browser-ref.mjs');
  // The pin constant exists at the lock-step value (20px @ the 16px root).
  assert.match(s, /export const REF_LINE_HEIGHT = '1\.25'/, 'REF_LINE_HEIGHT missing/changed');
  // The :where(body) injection consumes it (zero specificity — author
  // line-height rules in the ref must still win, like a UA default), on the
  // same declaration block as the ink + font pins.
  assert.match(s, /font-family: \$\{REF_FONT_STACK\};\s*\n\s*line-height: \$\{REF_LINE_HEIGHT\}; \}/,
    'ref injection must apply REF_LINE_HEIGHT at zero specificity');
});

test('corpus-v4.1 line-height: web wpt stage + composed placeholder pin the ref value', () => {
  // Extract the ref pin from source so all web assertions track it — either
  // side drifting fails here, not silently in a corpus run.
  const ref = src('tools/titan/capture-browser-ref.mjs');
  const refLh = /export const REF_LINE_HEIGHT = '([^']+)'/.exec(ref)?.[1];
  assert.ok(refLh, 'REF_LINE_HEIGHT not found in capture-browser-ref.mjs');

  const html = src('apps/web-harness/index.html');
  // The WPT stage rule re-pins the same unitless number (inheritance-based
  // default only — IR-declared line-heights arrive inline and win)…
  const wptRule = /body\.wpt-mode, body\.wpt-mode #root,\s*\n\s*body\.wpt-composed-mode, body\.wpt-composed-mode #root \{[^}]*\}/s.exec(html)?.[0];
  assert.ok(wptRule, 'wpt-mode stage rule not found in index.html');
  assert.ok(wptRule.includes(`line-height: ${refLh};`), 'wpt-mode stage rule must pin the ref line-height');
  // …while the base html/body rule (the 327-pair stage) declares NO
  // line-height at all, so the dark-stage captures keep font-normal boxes.
  const baseRule = /html, body \{[^}]*\}/s.exec(html)?.[0];
  assert.ok(baseRule, 'base html/body rule not found in index.html');
  assert.doesNotMatch(baseRule, /line-height/, 'the dark-stage html/body rule must not grow a line-height');

  // The composed placeholder default mirrors the same unitless value (the
  // span re-states it so an ancestor's line-height can never detach it),
  // still deferring to an IR-declared line-height first.
  const tsx = src('apps/web-harness/src/sdui/ComponentRenderer.tsx');
  assert.match(tsx, new RegExp(`WPT_COMPOSED_MODE \\? \\{ lineHeight: irLineHeight \\?\\? '${refLh.replace('.', '\\.')}' \\} : \\{\\}`),
    'composed placeholder default must pin the ref line-height (after IR deferral)');
  // The Round-4 default-serif 18px calibration must never resurface.
  assert.doesNotMatch(tsx, /irLineHeight \?\? '18px'/, 'stale Round-4 18px composed pin resurfaced');
});

test('corpus-v4.1 line-height: compose composed ratio is 1.25 and the native default survives', () => {
  const runtime = src('runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/WptCaptureMode.kt');
  // The composed calibration ratio mirrors the ref pin (1.25 → 20px @16px).
  assert.match(runtime, /const val REF_DEFAULT_FONT_LINE_HEIGHT_RATIO: Float = 1\.25f/,
    'compose composed line-box ratio must be the ref 1.25');
  // The dark-stage native default box survives byte-identically (327 path).
  assert.match(runtime, /const val NATIVE_DEFAULT_LINE_HEIGHT_RATIO: Float = 1\.2f/,
    'compose dark-stage 1.2x default lost');
  // The mode split itself is unchanged: composed WPT → ref ratio, else 1.2x.
  assert.match(runtime, /if \(composedWpt\) REF_DEFAULT_FONT_LINE_HEIGHT_RATIO\s*\n\s*else NATIVE_DEFAULT_LINE_HEIGHT_RATIO/,
    'compose composed line-box mode split missing/changed');
});

// ── wave-21: the composed UA BOX-MODEL contract (padding + box-sizing) ──────
//
// THE CONTRACT UNDER TEST: on the composed WPT surface, test content must
// carry the UA-origin box model — the same box model the browser-ref page
// gets by rendering raw WPT HTML under the full UA stylesheet. The ref side
// keeps UA control padding (button 1px 6px, input 1px 2px, textarea 2px…)
// and the controls' UA `box-sizing: border-box` because its injection only
// frames html/body at zero specificity; the harness side must therefore
// REVERT its own universal reset (`* { margin:0; padding:0; box-sizing:
// border-box }`) and the wpt-mode `content-box` override back to the UA
// origin on IR-rendered elements. A half-held state — harness reset intact
// while the ref keeps UA metrics, or a reset creeping into the ref
// injection — re-opens the measured wave-20 failure: every sourceTag-
// preserved widget rendered tighter than the ref (narrower buttons, rows
// drifting upward cumulatively), a flat 3.96% / 9,274px penalty on all five
// appearance-alias tests sharing appearance-auto-ref.html (web-ref SSIM
// 0.8267 → 1.0000 pixel-exact once reverted). Same style as the CANVAS_REV
// pins above: each side's term is scanned from its ONE source of truth so
// either side drifting fails `node --test tools/titan/*.test.mjs`.

test('wave-21 box model: wpt-composed-mode reverts margin+padding+box-sizing to the UA origin', () => {
  const html = src('apps/web-harness/index.html');
  // Extract the composed override rule body (selector through closing brace)
  // so all three declarations are pinned INSIDE this one rule — a revert
  // moved to some other selector would not satisfy the composed contract.
  const rule = /body\.wpt-composed-mode \[data-component-id\],\s*\n\s*body\.wpt-composed-mode \[data-component-id\] \* \{[\s\S]*?\n      \}/.exec(html)?.[0];
  assert.ok(rule, 'wpt-composed-mode override rule not found in index.html');
  // margin: the Round-4 GAP-1 term (UA <p>/<h*> margins) must survive…
  assert.match(rule, /margin: revert;/, 'composed rule lost margin: revert');
  // …and the wave-21 terms: UA control padding + UA border-box come back.
  assert.match(rule, /padding: revert;/, 'composed rule lost padding: revert');
  assert.match(rule, /box-sizing: revert;/, 'composed rule lost box-sizing: revert');

  // CASCADE ORDER is load-bearing: the composed rule and the wpt-mode
  // `box-sizing: content-box` rule tie on specificity, so `revert` wins in
  // composed mode ONLY because it is declared LATER in the stylesheet.
  // Swapping the two rules would silently hand composed controls back to
  // content-box (the exact wave-20 penalty) while every scan above passes.
  const wptModeIdx = html.indexOf('body.wpt-mode [data-component-id]');
  const composedIdx = html.indexOf('body.wpt-composed-mode [data-component-id]');
  assert.ok(wptModeIdx >= 0, 'wpt-mode box-sizing rule not found in index.html');
  assert.ok(composedIdx > wptModeIdx, 'composed revert rule must be declared AFTER the wpt-mode content-box rule (equal specificity — later wins)');

  // The per-component `?wpt=1` surface keeps its own contract byte-for-byte:
  // swarm-003's `content-box` (spec-default box model for width/border
  // arithmetic) with NO revert terms — composed is the only surface that
  // reproduces full UA layout.
  const wptRule = /body\.wpt-mode \[data-component-id\],\s*\n\s*body\.wpt-mode \[data-component-id\] \* \{[\s\S]*?\n      \}/.exec(html)?.[0];
  assert.ok(wptRule, 'wpt-mode override rule not found in index.html');
  assert.match(wptRule, /box-sizing: content-box;/, 'wpt-mode per-component rule must keep content-box');
  assert.doesNotMatch(wptRule, /revert/, 'revert leaked into the per-component wpt-mode rule');

  // The universal reset itself must survive verbatim — it is what the 327
  // committed baselines were captured under, AND it is the cascade origin
  // `revert` rolls back FROM (delete it and `revert` becomes a no-op with a
  // silently different meaning).
  assert.match(html, /\* \{\s*\n\s*margin: 0;\s*\n\s*padding: 0;\s*\n\s*box-sizing: border-box;\s*\n\s*\}/,
    'the universal reset (327-pair contract) must survive');
});

test('wave-21 box model: the ref injection frames only html/body — UA control metrics stay intact', () => {
  const s = src('tools/titan/capture-browser-ref.mjs');
  // Extract the ONE canvas-frame stylesheet so the assertions below scope to
  // what actually reaches the page, not to comments elsewhere in the file.
  // wave-25 round 3: the literal moved out of the `addStyleTag` call into the
  // exported `canvasFrameCss()` factory, because the two TEST-page bake paths
  // (post-load-extract.mjs, bidi-bake.mjs) must inject the byte-identical
  // sheet and their hand-copied versions had already drifted back to the
  // pre-CAL-RC1 CSS pad. Pinning the factory body therefore pins all THREE
  // injection sites at once — see the bake-alignment test below.
  const injected = /export async function canvasFrameCss\(\) \{\s*\n\s*return `([\s\S]*?)`;\s*\n\}/.exec(s)?.[1];
  assert.ok(injected, 'canvasFrameCss() frame stylesheet not found');
  // …and the ref capture must actually USE it (a factory nobody calls would
  // let the real injection drift while every assertion below still passes).
  assert.match(s, /page\.addStyleTag\(\{\s*\n\s*content: await canvasFrameCss\(\),/,
    'the ref capture must inject canvasFrameCss(), not a private literal');
  // The box-model rules are EXACTLY the zero-specificity html/body frame —
  // pin both literals so any widening (extra selectors, changed values)
  // fails here rather than silently re-boxing the ref.
  assert.ok(injected.includes(':where(html, body) { margin: 0; padding: 0; background: ${CANVAS_BG}; }'),
    'ref html/body frame rule missing/changed');
  // wave-25 CAL-RC1: the 16px canvas pad is GONE from CSS — it is applied to
  // the raster (padPngBuffer) so abspos/fixed overlays translate with the
  // in-flow content instead of staying pinned to the ICB origin. `flow-root`
  // replaces the one load-bearing side effect the padding had (blocking
  // margin collapse-through at the body edges) WITHOUT establishing an
  // abspos containing block. A padding declaration reappearing here would
  // silently reinstate the (-16,-16) internal misalignment.
  assert.ok(injected.includes(':where(body) { display: flow-root; box-sizing: border-box;'),
    'ref body frame rule missing/changed');
  assert.doesNotMatch(injected, /:where\(body\)[^}]*padding:/,
    'the CSS canvas pad came back on :where(body) — it belongs in image space');
  // The abspos-containing-block hazard, stated as a guard: none of these may
  // ever enter the body frame rule, because each would make the body a
  // containing block for absolutely-positioned descendants and re-introduce
  // the very offset the image-space pad removes.
  assert.doesNotMatch(injected, /:where\(body\)[^}]*position:\s*(relative|absolute|fixed|sticky)/,
    'a positioned body would capture abspos descendants');
  assert.doesNotMatch(injected, /:where\(body\)[^}]*(transform|filter|perspective|contain):/,
    'a transform/filter/contain on body would capture abspos descendants');
  // No universal reset and no element/attribute-wide box-model override may
  // ever enter the injection: the harness reverts TO the UA origin, so the
  // ref must PRESENT the UA origin — a `* { padding: 0 }` here would strip
  // the very control padding the composed revert exists to match.
  assert.doesNotMatch(injected, /^\s*\*\s*[,{]/m, 'universal selector leaked into the ref injection');
  assert.doesNotMatch(injected, /\[data-component-id\]/, 'harness-only selector leaked into the ref injection');
  // Count the box-model declarations in the injection: exactly one `margin`,
  // ONE `padding` (the html/body frame zero — the canvas pad moved to image
  // space at wave-25) and one `box-sizing`, all in the two :where frame
  // rules pinned above. Any additional declaration is a contract widening
  // that must be reviewed against the harness side.
  assert.equal((injected.match(/margin:/g) ?? []).length, 1, 'unexpected extra margin declaration in ref injection');
  assert.equal((injected.match(/padding:/g) ?? []).length, 1, 'unexpected extra padding declaration in ref injection');
  assert.equal((injected.match(/box-sizing:/g) ?? []).length, 1, 'unexpected extra box-sizing declaration in ref injection');
});

test('wave-25 round 3: both bake paths load pages in the ref CONTENT space', () => {
  // THE SKEPTIC FINDING this pins: at CAL-RC1 the ref pipeline moved to a
  // 358x568 UNPADDED render (the 16px frame became image-space padding of the
  // PNG), but post-load-extract.mjs and bidi-bake.mjs kept loading test pages
  // at 390x600 WITH `:where(body){padding:16px}`. Both bakes snapshot COMPUTED
  // geometry that is replayed inside the composed canvases' content box, so a
  // 32px-larger ICB silently inflated every viewport-relative value they bake.
  const refSrc = src('tools/titan/capture-browser-ref.mjs');
  // The two viewport numbers are DERIVED from the canvas contract, never free
  // literals — that is what stops the next revision from drifting again.
  assert.match(refSrc, /export const REF_RENDER_WIDTH = CANVAS_WIDTH - 2 \* CANVAS_PAD_PX;/,
    'REF_RENDER_WIDTH must stay derived from the canvas width and pad');
  assert.match(refSrc, /export const REF_RENDER_MIN_HEIGHT = REF_MIN_CANVAS_H - 2 \* CANVAS_PAD_PX;/,
    'REF_RENDER_MIN_HEIGHT must stay derived from the canvas height floor and pad');
  // The ref capture itself must set that viewport (it is the oracle the two
  // bakes are being aligned TO).
  assert.match(refSrc, /const innerFloor = REF_RENDER_MIN_HEIGHT;/,
    'the ref capture must render at the shared content-space height floor');
  assert.match(refSrc, /setViewport\(\{ width: REF_RENDER_WIDTH, height: innerFloor/,
    'the ref capture must render at REF_RENDER_WIDTH');

  for (const rel of ['tools/titan/post-load-extract.mjs', 'tools/titan/bidi-bake.mjs']) {
    const bake = src(rel);
    // One shared sheet — no private template literal may reappear.
    assert.match(bake, /canvasFrameCss/, `${rel} must import the shared canvas frame`);
    assert.match(bake, /css: await canvasFrameCss\(\),/,
      `${rel} must inject the shared canvas frame, not a copy`);
    // No INLINE stylesheet may be handed to the injector any more: the only
    // legal shape is `css: await canvasFrameCss(),` asserted above. Scoping
    // the guard to the `css:` argument (rather than to the selector text)
    // keeps the prose free to NAME the removed rule while explaining it.
    assert.doesNotMatch(bake, /css: `/,
      `${rel} still builds its own frame stylesheet — it must use canvasFrameCss()`);
    // The viewport: the ref's content space, by imported constant.
    assert.match(bake,
      /setViewport\(\{\s*\n?\s*width: REF_RENDER_WIDTH, height: REF_RENDER_MIN_HEIGHT/,
      `${rel} must lay pages out at the ref content-space viewport`);
    // …and the pre-CAL-RC1 numbers must be gone from the setViewport call.
    assert.doesNotMatch(bake, /setViewport\(\{ width: 390, height: 600/,
      `${rel} still loads at the outer 390x600 canvas`);
  }
});

test('corpus-v4.1 line-height: swiftui ref line box is 20 and stays WPT-gated + IR-deferring', () => {
  const renderer = src('runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift');
  // The calibration constant mirrors the ref pin (16px root x 1.25 = 20).
  assert.match(renderer, /public static let wptRefLineBoxPx: CGFloat = 20/,
    'swiftui ref line box must be the corpus-v4.1 20px');
  // The gate shape: IR-declared wins, then WPT-mode-only pin, else nil
  // (SwiftUI natural metrics — the untouched product/baseline path).
  //
  // Wave 22 (lane FONT) re-expressed the same decision as a `switch` over the
  // shared `LineHeightNormal.lineBoxSource`, which added the third
  // declared-`normal` state. The old literal `if let declared { return
  // declared }` pin no longer exists, so it is pinned here in its new form —
  // same three guarantees, one more row.
  assert.match(renderer, /case \.declared:\s*\n\s*return declared!/,
    'swiftui IR line-height deferral missing');
  assert.match(renderer, /LineHeightNormal\.lineBoxSource\(hasDeclaredValue: declared != nil/,
    'swiftui line-box pick must delegate to the shared three-state decision');
  // Declared `normal` (the `font` shorthand reset, css-fonts-4 §4.3) routes to
  // the face's own metrics — nil, NOT the 20pt pin — so the single-line
  // maxHeight cap is released with it.
  assert.match(renderer, /case \.natural:\s*\n\s*return nil/,
    'swiftui declared-normal must fall through to natural face metrics');
  // The ABSENT-line-height row is the one the whole corpus and all 327
  // committed baselines ride: pinned under WPT capture, nil elsewhere.
  // Wave 23 (lane iOS-INSETS) made the pin inheritance-aware — fontSizePx x
  // the derived ratio instead of the flat 20pt constant, so a 92px h1's
  // line box scales with its font while the 16px identity (16 x 1.25 = 20)
  // keeps every existing capture byte-stable. The three guarantees this
  // pin protects are unchanged: WPT-gated, nil product path, ratio
  // anchored to wptRefLineBoxPx (asserted above at exactly 20).
  assert.match(renderer, /return wptCaptureMode \? fontSizePx \* wptRefLineHeightRatio : nil/,
    'swiftui line-box pin must stay WPT-mode-gated with a nil product path');
  // The ratio itself must stay derived from the pinned 20/16 so the two
  // constants can never drift apart silently.
  assert.match(renderer, /wptRefLineHeightRatio[^=]*=\s*wptRefLineBoxPx \/ 16/,
    'swiftui ratio must be derived from wptRefLineBoxPx, not a free literal');
});
