#!/usr/bin/env node
//
// tools/titan/capture-browser-ref.mjs — Phase 1 browser-ref capture.
//
// Implements TITAN_ARCHITECTURE.md Section 5.2: render every WPT test's
// `*-ref.html` directly in headless Chromium and save it as the
// "spec-truth" reference image.
//
// Cache layout (Section 5.4, revised at the corpus-v4 white-canvas boundary,
// extended at wave-24 with the BROWSER-REV segment):
//   authoritative: refs/<wpt-sha>/<canvas-rev>/<browser-rev>/<section>/<stem>.png
//   scorer view  : refs/<wpt-sha>/<canvas-rev>/<section>/<stem>.png
//
// We key on the WPT_REF SHA so re-pinning regenerates the cache; the extra
// <canvas-rev> segment (CANVAS_REV below) keys the CANVAS CONTRACT so a
// canvas change regenerates the cache too — the corpus-v1..v3 dark-canvas
// refs stay untouched at tools/wpt/refs/<sha>/<section>/ for historical
// reproduction. Everything else hits cache on subsequent runs (the corpus
// is byte-identical for a given pin, so the rendered ref PNG is too within
// AA noise).
//
// ── wave-24 A-RC1: the BROWSER-REV cache segment ─────────────────────────────
// The three keys above (WPT SHA, canvas contract, test stem) all describe
// OUR inputs. The fourth input — the Chromium build that rasterises the ref
// — was unkeyed, so a puppeteer/Chromium upgrade left every previously
// cached PNG a permanent cache HIT: a rendering bug (or a rendering FIX)
// in the browser that produced them was frozen into the acceptance signal
// forever, and the SDUI runtimes were scored against a target no live
// browser would reproduce. Folding `browser.version()` into the path makes
// an upgrade a cache MISS, exactly like a WPT re-pin or a canvas change.
//
// MIGRATION POLICY (why there are two trees, and which is authoritative):
//   * The ~10k PNGs of the existing corpus live under the VERSION-LESS
//     path. Re-rendering all of them at once costs hours, so the versioned
//     tree is populated LAZILY: `adoptLegacyRef` hard-links an existing
//     version-less PNG into the versioned slot the first time a section
//     runs, instead of re-rendering it. Adoption is gated on the CLAIM file
//     (`.legacy-browser-rev`, see legacyClaimPath): the first versioned run
//     records "the version-less tree was produced by THIS browser rev". If
//     a later run sees a different rev, adoption stops dead — every test it
//     touches re-renders. The claim is never auto-advanced, so a browser
//     upgrade permanently retires the grandfathered tree, section by
//     section, without a corpus-wide wipe.
//   * The version-less tree stays populated as the SCORER VIEW: every
//     capture mirrors its PNG there (hard link — no extra bytes). That is
//     what keeps run-titan.sh / section-runner.sh's hardcoded
//     `--refs-root .../refs/$WPT_REF/<canvas-rev>` (consumed by
//     inject-wpt-block.mjs) correct with no change to those scripts. It is
//     a "latest capture wins" view: after an upgrade it holds a MIX of old
//     and new refs, but every test a run actually captures is refreshed in
//     that same run, so a scored run always compares against refs from the
//     browser that is running it.
//
// Capture canvas geometry matches the rest of the Style-Converter pipeline
// so browser-ref images are pixel-comparable against the iOS/Android/web
// captures from compare-screenshots.mjs:
//   - width            : 390 px  (matches CaptureCanvas)
//   - height           : natural (we resize the viewport to documentHeight)
//   - background       : WHITE   (matches the platforms' WPT capture mode)
//   - padding          : 16 px around the body root
//   - deviceScaleFactor: 1
//
// ── DOCUMENTED CORPUS BOUNDARY (corpus-v4): the WHITE canvas ─────────────────
// Through corpus-v3 the ref canvas was the pipeline's dark #1A1A2E stage.
// That was a systematic reftest penalty: WPT tests are authored against the
// spec-default WHITE canvas, and many paint WHITE ink (borders, backgrounds)
// that is *supposed to vanish* into the page — e.g. the 6 abspos-autopos
// tests draw `border: solid white` frames (~5,200 px of ink) whose refs are
// a bare green square. On the dark stage that white ink was VISIBLE in the
// SDUI captures while the ref hid nothing of the sort, so every white-ink
// reftest was structurally penalised regardless of renderer correctness.
// From corpus-v4 the WPT capture canvas is WHITE on the ref AND on all
// three platform harnesses simultaneously (web wptCanvasStyle /
// composedCanvasStyle, Compose WptCaptureMode.WPT_CANVAS_BACKGROUND,
// SwiftUI WPTCanvas.background), restoring the camouflage the reftests
// assume. ALL WPT numbers shift at this boundary — corpus-v4 is the first
// white-canvas snapshot and is NOT comparable to v1..v3.
// ── DOCUMENTED INK+FONT SUB-BOUNDARY (corpus-v4.1): BLACK ink, Inter face ────
// Within the v4 white-canvas era the default TEXT rendering flipped a
// second time — two coupled changes, one boundary:
//
// INK: at the v4 flip the injected default ink was kept `color: #fff` (the
// harness family) so default-ink prose stayed camouflaged on BOTH sides —
// but that symmetry was VACUOUS: real WPT pages paint BLACK prose (the UA
// `color: CanvasText` default), so every default-ink text test "passed" by
// neither side showing the text at all. From corpus-v4.1 the injected ink
// is the spec BLACK (#000) and all three runtime default-text bottom-outs
// flip to black IN WPT MODE simultaneously (web PlaceholderContent
// WPT_MODE ink + index.html wpt-mode body color, Compose
// WPT_DEFAULT_TEXT_INK, SwiftUI WPTCanvas.textInk) — making prose tests
// real instead of vacuous.
//
// FONT: black ink made the prose VISIBLE, which exposed the second half of
// the divergence — the ref rendered it in Chromium's default SERIF while
// all three harnesses render text in the bundled Inter sans stack (web
// index.html html/body rule, Compose InterFontFamily, iOS registered
// "Inter" face). Different faces → different WRAP POINTS, so everything
// below the prose shifts vertically (the a98rgb cluster's entire failure —
// the color math there is pixel-exact). From corpus-v4.1 the ref injection
// pins the SAME stack (REF_FONT_STACK below) and embeds the harness's own
// Inter faces as data-URI @font-face rules so the first stack entry
// actually resolves in the ref browser. The natives need no font hook:
// their default text face is ALREADY the bundled Inter unconditionally
// (WPT and non-WPT modes alike), so only the ref side had to move.
//
// LINE-HEIGHT: the third leg of the same v4.1 sub-boundary. With black ink
// and the shared Inter face landed, ref-vs-capture prose diverged ONLY in
// vertical rhythm: the ref's default paragraphs advanced 36px top-to-top
// (line box ~20px — Chromium's natural Inter `line-height: normal` at
// 16px) while every harness capture advanced 34px (the Round-4 composed
// line-box calibration's ~18px box, tuned against the OLD default-serif
// ref), accumulating 2px per paragraph — 20px over a 10-bar test, the
// whole css-color/css-break/css-flexbox collapse of the first v4.1 run.
// Neither side may depend on font-`normal` metrics: the ref injection now
// pins an explicit deterministic `line-height: 1.25` (REF_LINE_HEIGHT
// below — 20px at the default 16px, matching Chromium's measured natural
// Inter rhythm so ref pixels barely move) and all three harness composed
// line-box calibrations move to the SAME 20px box (web index.html
// wpt rules + ComponentRenderer composed pin, Compose
// REF_DEFAULT_FONT_LINE_HEIGHT_RATIO, SwiftUI wptRefLineBoxPx).
// Author-declared line-height still WINS everywhere — the pin is a
// DEFAULT (`:where` zero specificity here and on web; the natives only
// apply it when no LineHeight property resolved).
//
// The dark-stage property-fixture path keeps its #eee-family/Inter
// defaults byte-identically (all three flips are WPT-mode-only).
// CANVAS_REV bumps at this sub-boundary so v4.0 white-ink refs — and the
// line-height-less black-ink scratch refs from the first v4.1 run — are
// never diffed against v4.1 black-ink/Inter/line-height captures.
//
// ── wave-25 CAL-RC1: the pad moves from CSS SPACE to IMAGE SPACE ─────────────
// The fourth leg of the canvas contract, and a REF-ONLY change.
//
// THE DEFECT: the frame above was injected as `:where(body){ padding: 16px }`.
// CSS padding on the body shifts IN-FLOW content by (16,16) — but it does NOT
// move `position: absolute` boxes, because a padded static body is not a
// containing block: an abspos child resolves against the INITIAL CONTAINING
// BLOCK, whose origin stays (0,0). `position: fixed` is worse still (viewport
// origin, always (0,0)). A WPT ref that draws its expected-result bars as
// abspos overlays therefore rendered them 16px UP and LEFT of the in-flow
// prose they annotate — the ref was INTERNALLY misaligned with itself, and a
// runtime that painted the page CORRECTLY (everything uniformly inset by the
// canvas pad, which is what all three harnesses do) got PENALISED for it.
// Measured on the wave24-final corpus: 92–100 % of every css-gaps mismatch
// area was this systematic (-16,-16) offset, not renderer divergence.
//
// THE FIX: render the ref with ZERO injected padding at viewport width
// REF_RENDER_WIDTH (= CANVAS_WIDTH − 2·CANVAS_PAD_PX = 358), then pad the
// resulting PNG by CANVAS_PAD_PX on all four sides in IMAGE SPACE
// (padPngBuffer below, pngjs). Rasterised pixels have no containing-block
// semantics, so in-flow, absolute and fixed content all translate by exactly
// (+16,+16) together — the frame becomes uniform by construction. The
// CONTENT width is unchanged (358 px was already the padded body's content
// box), so wrap points, line counts and every text-driven layout are
// byte-stable; the final PNG is still 390 wide.
//
// PAD COLOUR: not blindly CANVAS_BG. A ref whose body background covers the
// whole viewport (17/302 cached refs — background-color-animation-in-body's
// olive, the overlay-transition-backdrop greens) paints edge-to-edge today,
// and so does every harness capture of it (the body-root background fills the
// padded canvas). Framing those in white would MANUFACTURE a 32-px divergence
// band. padColorFor() therefore samples the rendered image's border ring: a
// uniform ring is the page's canvas colour and becomes the pad; anything else
// falls back to CANVAS_BG. See its banner for the sampling rule.
//
// AUDIT of the sibling injections at this boundary (all three are SAFE — none
// has the containing-block hazard, because none is a box-model property):
//   * `color` / `font-family` / `line-height` — INHERITED text properties.
//     They change glyph rasterisation and wrap points identically for
//     in-flow and out-of-flow boxes, and establish no containing block.
//   * `margin: 0` on html/body — kept, and now the ONLY box-model
//     declaration. Zeroing a margin cannot displace an abspos box relative
//     to in-flow content: the ICB origin is where the (removed) margin
//     would have started, so both sides move together.
//   * `min-height: 100vh` — the one to watch, and it survives EXACTLY.
//     Old: viewport 390×H, border-box body ⇒ content box H−32.
//     New: viewport 358×(H−32), padding 0 ⇒ content box H−32. Identical,
//     so a `height: 100%` child resolves against the same number as before.
//     `100vh`/`100vw` used ELSEWHERE do shrink by 32/32 px (the pad is no
//     longer inside the viewport) — viewport-relative refs are already
//     bucket-tagged requires-viewport-canvas, and this is the honest
//     reading: the viewport IS the content canvas.
// One deliberate behaviour change beyond the alignment fix: a ref that
// declares its OWN body padding used to LOSE our frame entirely (`:where()`
// has zero specificity, so the author rule replaced it). Now it keeps its
// padding AND gets the 16px image frame — which is exactly what the capture
// side does (canvas pad + the IR's own body-root padding).
//
// The composed CAPTURE canvases are untouched: they render OUR IR, where the
// body-root pad resolver governs the 16px inset in CSS space. Only the REF
// pipeline moves to image space.
//
// The 800×600 spec-default viewport is still not used. Reasons:
//   1. The 327-pair pipeline standardises on 390-wide captures. A
//      browser-ref captured at 800×600 would not be directly comparable
//      to our existing platform captures without re-renormalising every
//      pair.
//   2. The Section 5.3 fuzzy-tolerance metadata is stored alongside the
//      ref but applies to the test↔ref pair, not to a particular canvas
//      size. Rendering both halves at 390 px keeps fuzzy semantics
//      consistent.
//
// Usage:
//   node tools/titan/capture-browser-ref.mjs <test-rel-path>...
//   WPT_REF=<sha> node tools/titan/capture-browser-ref.mjs ...
//
// Exit codes:
//   0 — every input rendered (or already cached)
//   1 — at least one render failed; partial cache populated
//   2 — fatal infra error (Puppeteer launch, FS)

import puppeteer from 'puppeteer';
import { promises as fs } from 'node:fs';
import { existsSync } from 'node:fs';
// wave-25 CAL-RC1: the image-space frame is applied with pngjs — already a
// repo dependency (tools/visual/compare-screenshots-metrics.mjs decodes every
// diff with it), pure JS, and byte-deterministic. sharp would also work but
// pulls a native resampler into a path that only ever memcpys rows.
import { PNG } from 'pngjs';
// basename dropped at the wave-21 collision fix — the one stem-derivation
// site (cachePathFor) now goes through safe-name.mjs's fixtureStem().
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { extractRefHref } from './extract-fixture.mjs';
// The ONE canonical fixture-stem derivation (wave-21 collision fix): the ref
// cache PNG must be keyed by the SAME subdir-encoded stem the fixture files
// use, or two nested tests with equal basenames would share one cache slot —
// the first render's PNG silently serving as the second test's reference.
import { fixtureStem } from './safe-name.mjs';
// wave-45 lane X6 — the Rule-43 NOTO BUNDLING PILOT (see noto-pilot.mjs's
// banner). Every function is env-gated on TITAN_NOTO_PILOT=1 and collapses
// to identity/'' with the flag unset, so the default ref contract below is
// byte-identical (pinned by noto-pilot.test.mjs). With the flag ON: the
// canvas rev gains a '-notopilot' suffix (pilot refs live in their own tree,
// the frozen refs are never touched), the injected stack gains the pilot
// Noto families after 'Inter', and the staged pilot faces ride the same
// data-URI @font-face delivery Inter already uses.
import { notoPilotRevSuffix, notoPilotStack, notoPilotFontFaceCss } from './noto-pilot.mjs';
// wave-52 L12-B Fix B: the body-margin probe reads linked local stylesheets
// synchronously next to the page it is judging (see pageDeclaresBodyMargin).
import { readFileSync } from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
const REFS_ROOT  = join(REPO_ROOT, 'tools', 'wpt', 'refs');

// Capture canvas dimensions — width/padding copied verbatim from the rest of
// the pipeline so browser-ref images line up with iOS/Android/web captures
// pixel-for-pixel. Exported so unit tests can pin the canvas contract.
const CANVAS_WIDTH  = 390;
// The corpus-v4 WHITE canvas (see the header boundary note): WPT reftests
// are authored against a white page, so white ink must vanish in the ref
// exactly as it does upstream. The three platform WPT capture modes paint
// the SAME white simultaneously — this constant and theirs move together.
export const CANVAS_BG = '#FFFFFF';
// Canvas-contract revision segment in the cache path. Bump/replace whenever
// the canvas contract changes (background, padding, width, injected frame,
// injected font, injected line-height) so stale refs from an older contract
// can never be diffed against captures made under the new one.
// 'white-black-ink-font-lh' == the full corpus-v4.1 contract (white canvas
// + spec-BLACK injected default ink + the harness Inter font stack + the
// deterministic REF_LINE_HEIGHT pin — the header's three-legged ink+font+
// line-height sub-boundary). The line-height-less 'white-black-ink-font'
// scratch refs from the first v4.1 experiments, the corpus-v4.0
// white-canvas/white-ink refs at refs/<sha>/white/, and the pre-v4 dark
// refs at the un-segmented refs/<sha>/<section>/ path are ALL stale —
// none is ever mixed into a v4.1 diff.
// '-imgpad' (wave-25 CAL-RC1) marks the fourth leg: the 16px frame moved from
// CSS `:where(body){padding}` to IMAGE-space padding of the rendered PNG, so
// abspos/fixed overlays stop sitting 16px off their own in-flow content. Every
// pre-imgpad ref is geometrically stale for abspos-overlay tests, so the rev
// bump retires them; the browser-rev-keyed cache re-renders LAZILY (each
// section pays for its own refs the first time it runs under the new rev).
// '-htmlpins' (wave-30 A5) marks the fifth leg: the three INHERITED pins
// (color / font-family / line-height) moved from `:where(body)` to
// `:where(html)`, so a ref declaring one of them at :root/html is no longer
// clobbered by a specified value on <body> (full mechanism at
// canvasFrameCss's A5 note). Every pre-htmlpins ref of such a page rasterised
// the WRONG ink/face/rhythm and must never reach a live diff, so the rev bump
// retires the whole tree; refs of pages with no root-scope text declaration
// re-render byte-identically, they just pay the lazy re-render once.
// '-notopilot' (wave-45 lane X6, APPENDED ONLY UNDER TITAN_NOTO_PILOT=1 —
// '' otherwise, so the default rev string is byte-identical): the pilot's
// sixth leg is the extended font contract (pilot Noto families after
// 'Inter' + their staged @font-face payloads). Pilot refs render into their
// own '…-notopilot' tree so they can never be adopted by — or leak into —
// the frozen production scorer view; whether this leg ever becomes a REAL
// corpus-wide rev bump is exactly the wave-46 decision the pilot measures.
// '-rootbg-uamargin' (wave-52 lane L12-B, the CALIBRATION the BACKLOG's
// "Instrument decisions pending" required) marks the sixth leg, two halves
// shipped under ONE bump: (1) ROOTBG — the canvas `background` lives on
// `:where(html)` only (wave-50 B10's verified patch): the body half painted an
// opaque white box at CSS 2.1 Appendix E step 3, OVER every `z-index:-1` body
// child (step 2) and over an author `<html style="background:green">` canvas,
// erasing ref ink no browser erases; (2) UAMARGIN — a ref whose TEST declares
// a body margin while the REF itself relies on the UA `body { margin: 8px }`
// keeps that UA body box (UA_BODY_CSS below), the condition the WPT pair was
// authored under. MEASURED at the bump (tools/titan/results/wave52-instrument-
// and-calibration/_note.md): a same-session old-sheet/new-sheet A/B over the
// 1435 scored refs moves exactly 23 — the UA body box fires on 10 pairs and
// moves 7 refs (the other 3 are abspos/fixed, margin-insensitive), the root-
// only background moves 16 more (15 reveal paint the body box covered; one,
// display-contents-sharing-001, renders the injected sheet's own TEXT under
// `* { display: contents }`); those 23 were re-rendered through this module
// and the other 1412 carry their previous frozen bytes into the new tree
// unchanged (sha1 set digest equal) — NOT re-rendered, because 994 of them come
// back with 1–45 glyph-antialias pixels changed under the OLD sheet too
// (host font-raster drift since the freeze), which would move ~1000 cells for
// no contract reason. The bump retires the old tree so no ref rendered under
// the erasing contract can reach a live diff.
export const CANVAS_REV = `white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin${notoPilotRevSuffix()}`;
// wave-16 POST-LOAD: exported (was module-private) so post-load-extract.mjs
// can frame the TEST page in the identical canvas the ref capture uses —
// computed geometry snapshotted under a different pad would bake a
// systematic offset into every overridden inset.
//
// wave-25 round 3 (BAKE VIEWPORT ALIGNMENT): the pad is now the IMAGE-space
// frame ONLY. Nothing injects it as CSS any more — not the ref, and (as of
// this round) not the two TEST-page bake paths either. An earlier note here
// claimed the bakes should keep a CSS pad "because the runtimes' composed
// canvas has a real 16px CSS inset"; that reasoning inverted the contract.
// The bakes snapshot geometry that is REPLAYED inside the composed canvas's
// content box, so their page must be laid out in the SAME content space the
// ref is: viewport [REF_RENDER_WIDTH] wide with ZERO body pad. Loading them
// at 390 WITH a 16px pad gave every `100vw`/`100vh`/ICB-relative computed
// value a 32px surplus the ref does not have. See canvasFrameCss().
export const CANVAS_PAD_PX = 16;
// wave-25 CAL-RC1: the viewport width the ref page is RENDERED at. Exactly
// the content-box width the old CSS-padded body had (390 − 2×16), so wrap
// points and every text-driven layout are unchanged; the image-space pad
// restores the 390-wide canvas afterwards.
export const REF_RENDER_WIDTH = CANVAS_WIDTH - 2 * CANVAS_PAD_PX;
// The historical minimum canvas HEIGHT (the old code's literal 600, used both
// as the first viewport and as the document-height floor). Named now because
// the image-space frame has to subtract the pad from it before measuring and
// add it back after — a bare literal in two places would drift.
export const REF_MIN_CANVAS_H = 600;
// The viewport HEIGHT floor the ref page is rendered at — the twin of
// REF_RENDER_WIDTH on the block axis (600 − 2×16 = 568), so the IMAGE-padded
// PNG still floors at the historical 600. Exported (wave-25 round 3) because
// the two bake paths must set the SAME viewport: `100vh`, `min-height:100vh`
// and every ICB-relative computed value read at 600 would encode 32px the
// ref's layout never had.
export const REF_RENDER_MIN_HEIGHT = REF_MIN_CANVAS_H - 2 * CANVAS_PAD_PX;

// ── corpus-v4.1 FONT pin (header sub-boundary, FONT half) ────────────────────
//
// The font-family stack injected at zero specificity on the ref's body.
// MUST stay byte-identical to the harness stack in
// apps/web-harness/index.html's `html, body { font-family: … }` rule — the
// whole point is that ref prose and harness prose hit the SAME face and
// wrap at the SAME points. Exported so wpt-white-canvas.test.mjs can pin
// the two strings against each other.
export const REF_FONT_STACK =
  "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif";

// ── corpus-v4.1 LINE-HEIGHT pin (header sub-boundary, third leg) ─────────────
//
// The default line-height injected at zero specificity on the ref's body.
// UNITLESS on purpose (CSS Inheritance: a number inherits as the NUMBER and
// recomputes against each descendant's own font-size — exactly how a UA
// default behaves), so `1.25` yields a 20px line box at the default 16px
// root and scales with any author font-size. 20px matches Chromium's
// MEASURED natural Inter `line-height: normal` rhythm (default ref
// paragraphs advanced 36px top-to-top = 20px box + 16px collapsed margins),
// so pinning it barely moves ref pixels — the point is DETERMINISM: neither
// the ref nor the harnesses may depend on font-`normal` metrics, which are
// face- and rasterizer-specific. The three harness composed line-box
// calibrations pin the SAME 20px box (web index.html wpt rules '1.25' +
// ComponentRenderer composed default, Compose
// REF_DEFAULT_FONT_LINE_HEIGHT_RATIO 1.25, SwiftUI wptRefLineBoxPx 20).
// Exported so wpt-white-canvas.test.mjs can pin all four surfaces together.
export const REF_LINE_HEIGHT = '1.25';

// The harness's bundled Inter faces (the same files the web harness serves
// at /fonts/ and the natives bundle as res/font/inter_*.ttf). The ref page
// is raw WPT HTML with no @font-face of its own, so 'Inter' would silently
// fall through to -apple-system without an embedded face — a soft font
// drift that would defeat the pin. We embed Regular (400) + Bold (700):
// the two weights UA-stylesheet prose can reach (<b>/<strong>/<h*>);
// Medium/Black are only reachable via author `font-weight` rules, which
// the ref's own CSS supplies and which don't route through this default.
const HARNESS_FONT_DIR = join(REPO_ROOT, 'apps', 'web-harness', 'public', 'fonts');
const EMBEDDED_FONT_WEIGHTS = [
  ['Inter-Regular.ttf', 400],
  ['Inter-Bold.ttf', 700],
];

// Lazily-built @font-face CSS with base64 data-URI payloads (~550 KB per
// face — read once per process, injected per page; local CDP handles it).
// base64 is SAFE here, unlike extract-fixture.mjs's percentEncodeBytes:
// this CSS goes straight to the ref browser and never passes through the
// converter's value-lowercasing IR path.
let _interFontFaceCss = null;
// wave-16 POST-LOAD: exported (was module-private) so post-load-extract.mjs
// injects the SAME embedded Inter faces into the live TEST page — text-driven
// geometry (wrap points, line boxes) must settle on the same face the ref and
// the harnesses use, or computed rects would drift per the corpus-v4.1
// font-pin lesson documented in the header.
export async function interFontFaceCss() {
  if (_interFontFaceCss !== null) return _interFontFaceCss;
  const faces = [];
  for (const [file, weight] of EMBEDDED_FONT_WEIGHTS) {
    try {
      const bytes = await fs.readFile(join(HARNESS_FONT_DIR, file));
      // font-display: block mirrors the harness @font-face rules so the
      // capture never races a fallback-face first paint.
      faces.push(
        `@font-face { font-family: 'Inter'; font-style: normal; ` +
        `font-weight: ${weight}; font-display: block; ` +
        `src: url(data:font/ttf;base64,${bytes.toString('base64')}) format('truetype'); }`,
      );
    } catch (err) {
      // No silent fallthrough: a missing harness font means the ref would
      // quietly render system-sans while the harnesses render Inter — the
      // exact wrap-point drift this pin exists to kill. Warn loudly; the
      // stack's -apple-system fallback keeps captures running.
      console.error(`[capture-browser-ref] WARN: cannot embed ${file} ` +
        `(${err.message}) — ref falls back past 'Inter' in REF_FONT_STACK`);
    }
  }
  _interFontFaceCss = faces.join('\n');
  return _interFontFaceCss;
}

/** The ONE canvas-frame stylesheet every page in this pipeline is rendered
 *  under — the ref capture (renderOne) and both TEST-page bake paths
 *  (post-load-extract.mjs, bidi-bake.mjs).
 *
 *  wave-25 round 3: this used to be three hand-copied template literals that
 *  had to be kept byte-identical by comment discipline alone, and they had
 *  already drifted — the ref moved to `padding: 0` + `display: flow-root`
 *  at CAL-RC1 while both bakes still injected `padding: CANVAS_PAD_PX`. A
 *  bake page laid out with a 16px body pad snapshots geometry from a
 *  DIFFERENT box tree than the ref rasterises, which is precisely the
 *  systematic offset the bakes exist to eliminate. One factory, three call
 *  sites, no drift.
 *
 *  Every declaration is at ZERO specificity (`:where()`), so any author rule
 *  in the test/ref still wins — the frame is a UA-like default, not an
 *  override. Per-declaration rationale lives in the file header:
 *    * `margin: 0; padding: 0` — the ONLY box-model declarations, and both
 *      are zeroes: the 16px canvas frame is applied in IMAGE space after the
 *      render (padPngBuffer), so in-flow and out-of-flow content translate
 *      together instead of the CSS pad moving only the in-flow half.
 *    * `background` — the corpus-v4 white canvas.
 *    * `display: flow-root` — the body establishes a BFC so a first child's
 *      block margin cannot escape through the body edge (it would shift the
 *      whole page relative to a harness canvas that clips it).
 *    * `box-sizing: border-box` + `min-height: 100vh` — the body fills the
 *      viewport, so `height: 100%` children resolve against the same number
 *      they did under the pre-CAL-RC1 padded body (header AUDIT note).
 *    * `color` / `font-family` / `line-height` — the corpus-v4.1 ink + font
 *      + rhythm pins. These are INHERITED text properties and live on
 *      `:where(html)`, NOT on the body — see the A5 note below.
 *
 *  ── wave-30 A5: the INHERITED pins move to :where(html) ────────────────────
 *  THE DEFECT (measured, not theoretical). `:where()` makes a rule
 *  zero-SPECIFICITY, which settles selector-vs-selector contests on the SAME
 *  element. It does nothing about the CASCADE-vs-INHERITANCE contest one
 *  level down: CSS Cascade 5 §6.2 says a SPECIFIED value on an element always
 *  wins over an INHERITED one, whatever the specificity that produced it —
 *  inheritance is only consulted when the cascade yields nothing for that
 *  element. So a ref that declares `:root { color: green }` (or `html { … }`)
 *  and relies on <body> INHERITING it was silently overridden: our
 *  `:where(body){ color:#000 }` supplied a specified value on body, body's
 *  descendants inherited BLACK, and the ref rasterised black ink where the
 *  spec truth was green. Our harness captures — which render the same author
 *  CSS with no such body rule — were CORRECT and scored as failures against a
 *  wrong target.
 *  MEASURED: css/selectors/child-indexed-no-parent-ref.html renders
 *  rgb(0,0,0) under the body pins and rgb(0,128,0) under the html pins;
 *  corpus-wide 31 reference files declare one of the three inherited
 *  properties at :root/html without restating it on body.
 *  THE FIX: hoist exactly the three INHERITED pins to `:where(html)`. Then a
 *  root-scope author rule beats them ON THE ROOT (same element, author origin
 *  beats… no — same origin, but `:where()` is specificity 0 and the author's
 *  `:root`/`html` selector is not, so the author wins the cascade there), and
 *  body inherits the AUTHOR's value because nothing supplies a specified
 *  value on body any more. A ref with no root-scope declaration is
 *  byte-identical to before: html gets the pin, body inherits it, every
 *  descendant inherits it.
 *  WHAT STAYS ON BODY: `display: flow-root`, `box-sizing`, `min-height` and
 *  the body half of `margin/padding`. All three are NON-inherited, so
 *  hoisting them would change nothing on body — and flow-root/min-height are
 *  specifically ABOUT the body box (BFC containment, viewport fill), so
 *  moving them would break the CAL-RC1 geometry contract outright.
 *
 *  ── wave-50 B10 / wave-52 L12-B: `background` LEAVES THE BODY HALF ────────
 *  THE DEFECT (executed, not theoretical). Canvas propagation reads the
 *  ROOT's background first, and only falls through to the body's when the
 *  root has none — so `background` on html alone already paints the white
 *  canvas. Repeating it on BODY additionally paints an opaque box in the
 *  body's own layer, and CSS 2.1 Appendix E orders the root stacking context
 *  (1) root background, (2) NEGATIVE-z-index descendants, (3) in-flow block
 *  backgrounds. The body background is step 3; every `z-index: -1` body child
 *  is step 2. The recipe therefore ERASED the decoration boxes of every
 *  reference document that draws them at a negative z-index — and, by the
 *  same step-3 box, the author canvas of `<html style="background: green">`
 *  (css-cascade/reference/all-green.html: frozen WHITE, captures green).
 *  MEASURED by B10 with the live recipe vs the same sheet minus the body half
 *  (puppeteer, BROWSER_LAUNCH_ARGS, 390×600):
 *    css-gaps/flex/flex-gap-decorations-033-ref  0 vs 5200 chromatic px
 *    …-034-ref 0 vs 2500 · …-035-ref 0 vs 4050 · …-036/037-ref 0 vs 2050
 *    css-text/…/hanging-punctuation-block-bound-001-ref 0 vs 58184
 *  THE FIX: keep the canvas white where canvas propagation reads it (the
 *  root) and stop painting a second opaque box over the negative-z-index
 *  layer. A ref that declares its own `body { background: … }` still wins on
 *  specificity, so the wave-30 full-bleed cases are untouched.
 *  COST: an INSTRUMENT change — CANVAS_REV was bumped ('-rootbg-uamargin')
 *  and the affected refs re-frozen; the resulting cell moves are adjudicated
 *  as a calibration (capture bytes identical ⇒ instrument-only), never as a
 *  render delta. The two TEST-page bake paths inject this same sheet; they
 *  snapshot GEOMETRY, which a background never moves.
 *
 *  async because the embedded @font-face payloads are read from disk once
 *  per process (interFontFaceCss memoises them). */
export async function canvasFrameCss() {
  // wave-45 lane X6: both pilot calls are env-gated identities by default —
  // notoPilotFontFaceCss() is '' and notoPilotStack() returns its input
  // unchanged unless TITAN_NOTO_PILOT=1, so the flag-off sheet is
  // byte-identical to the pre-pilot literal. With the flag on, the pilot
  // faces ride the SAME data-URI delivery as Inter and the stack gains the
  // pilot families AFTER 'Inter' (Latin keeps resolving exactly where it
  // does today; only the fall-through the frozen stack hands to the
  // platform cascade is caught). Reading env at CALL time (not module load)
  // is deliberate: the two TEST-page bake paths (post-load-extract.mjs,
  // bidi-bake.mjs) inject this same sheet, so a pilot extraction run bakes
  // its geometry under the same faces the pilot ref rasterises — the
  // corpus-v4.1 font-pin lesson applied to the pilot itself.
  return `
      ${await interFontFaceCss()}
      ${await notoPilotFontFaceCss(process.env, { readFile: fs.readFile, existsSync })}
      :where(html) { color: #000;
                     font-family: ${notoPilotStack(REF_FONT_STACK)};
                     line-height: ${REF_LINE_HEIGHT}; }
      :where(html) { background: ${CANVAS_BG}; }
      :where(html, body) { margin: 0; padding: 0; }
      :where(body) { display: flow-root; box-sizing: border-box;
                     min-height: 100vh; }
    `;
}

// ── wave-52 L12-B Fix B: the UA body box, for the pairs authored under it ───
//
// THE DEFECT (page-padding-overrun brief §3-B, PNG-measured). The frame above
// zeroes the body margin on EVERY ref. `clip-path-paddingBox-1d.html` declares
// `body { margin: 0 }` and draws its green box at (8,8) through an 8-px border;
// its match `reference/green-100x100.html` is a bare 100×100 div that lands at
// (8,8) ONLY through the UA sheet's `body { margin: 8px }` (HTML §15.3.3, the
// rendering section's body rule). Under the injection the ref's green sat at
// (0,0) → image (16,16) while Chrome-on-the-test and all three runtimes put it
// at (8,8) → image (24,24): the CSS-correct picture scored 0.9585 against a ref
// displaced by the very 8 px the injection removed. Same for paddingBox-1e and
// contentBox-1d/-1e.
// THE FIX: when the TEST author declares a body margin and the REF author does
// not (body-margin-census.json's "test-declares-ref-relies-on-UA" direction),
// the ref gets the UA body box back, injected AFTER canvasFrameCss() at the
// same zero specificity so it wins by order (CSS Cascade 5 §6.4) and any author
// rule in the ref still wins over it. All three declarations restore UA html.css
// values the frame overrode: `margin: 8px` (the UA body margin), `display:
// block` (UA — so a first child's margin collapses through the body exactly as
// in an unframed Chrome render; the frame's flow-root would stack 8 + the
// child's margin), and `min-height: auto` (the initial value — with the UA
// margin, the frame's `min-height: 100vh` would make the document 16 px taller
// than the viewport and the PNG 616 tall; with `auto` a short ref stays at the
// 568 viewport floor → 600 after the image pad).
// Every other ref never sees this sheet, so its bytes cannot move (pinned:
// the sheet is a SEPARATE constant, never interpolated into canvasFrameCss).
export const UA_BODY_MARGIN_PX = 8;
export const UA_BODY_CSS =
  `:where(body) { margin: ${UA_BODY_MARGIN_PX}px; display: block; min-height: auto; }`;

/** The margin-family property names a body rule may set (CSS Box 4 §3 +
 *  CSS Logical 1 §4.3). `scroll-margin*` is deliberately NOT matched: the
 *  boundary class keeps the `-` before `margin` from qualifying. */
const MARGIN_DECL_RX = /(?:^|[;{\s"'])margin(?:-(?:top|right|bottom|left|block|inline|block-start|block-end|inline-start|inline-end))?\s*:/i;

/** Does one selector (no commas) match the BODY element? Static judgement
 *  over the selector text, the census's own rule widened to combinators:
 *  the LAST compound must be a `body` type selector (any class/id/attribute/
 *  pseudo-class qualifiers allowed — a pseudo-ELEMENT is not the body), or a
 *  bare `*` whose ancestor compounds can only be html / :root (`*`, `html *`,
 *  `:root > *`) — `div *` cannot reach body, whose only ancestor is html. */
function selectorHitsBody(sel) {
  // Split into compounds on the descendant/child/sibling combinators.
  const parts = sel.trim().split(/\s*[>+~]\s*|\s+/).filter(Boolean);
  // An empty selector (a stray comma) matches nothing.
  if (!parts.length) return false;
  // The subject compound is the last one.
  const last = parts[parts.length - 1];
  // `body::before` styles a generated box, not the body's own margins.
  if (last.includes('::')) return false;
  // `body`, `body.x`, `body:not(.y)`, `body[dir]` — type selector first.
  if (/^body(?![\w-])/i.test(last)) return true;
  // The universal subject reaches body only through html/:root ancestors.
  if (/^\*(?![\w-])/.test(last)) return parts.slice(0, -1).every((p) => /^(html|:root)$/i.test(p));
  // Anything else (classes alone, other types) is not provably the body.
  return false;
}

/** Walk one stylesheet's rules (comments stripped, braces balanced) and
 *  report whether any rule whose selector list hits body declares a margin.
 *  Grouping at-rules are recursed (`@media` minus print-only, `@supports`,
 *  `@layer`, `@container`); descriptor blocks (`@font-face`, `@keyframes`,
 *  `@page`, …) are skipped — none of them styles the body box. */
function cssDeclaresBodyMargin(css) {
  // Comments can hide braces and selectors; drop them first.
  const s = String(css).replace(/\/\*[\s\S]*?\*\//g, '');
  // i walks the text; each iteration consumes one statement or one block.
  let i = 0;
  while (i < s.length) {
    // The next block opener; a `;` first means a block-less at-statement.
    const open = s.indexOf('{', i);
    if (open < 0) return false;
    const semi = s.indexOf(';', i);
    if (semi >= 0 && semi < open && s.slice(i, semi).trim().startsWith('@')) { i = semi + 1; continue; }
    // Find the matching close brace (depth-balanced for nested blocks).
    let depth = 1, j = open + 1;
    while (j < s.length && depth > 0) { if (s[j] === '{') depth++; else if (s[j] === '}') depth--; j++; }
    const prelude = s.slice(i, open).trim(), block = s.slice(open + 1, j - 1);
    i = j;
    if (prelude.startsWith('@')) {
      // print-only media never applies to the screen-medium ref render.
      const printOnly = /^@media\b/i.test(prelude) && /\bprint\b/i.test(prelude) && !/\b(screen|all)\b/i.test(prelude);
      // Grouping rules carry style rules inside; recurse into them.
      if (!printOnly && /^@(media|supports|layer|container)\b/i.test(prelude) && cssDeclaresBodyMargin(block)) return true;
      continue;
    }
    // A style rule: some selector hits body AND the block sets a margin.
    if (prelude.split(',').some(selectorHitsBody) && MARGIN_DECL_RX.test(block)) return true;
  }
  return false;
}

/** wave-52 L12-B (pure + exported): does this HTML page's AUTHOR CSS set a
 *  margin on <body>? Reads the inline `<body style>` attribute, every inline
 *  `<style>` block, and any `linkedCss` strings the caller resolved from
 *  `<link rel=stylesheet>` (pageDeclaresBodyMargin does that from disk).
 *  Static by design: it judges the source the WPT pair was authored as, with
 *  no page load (a test page's scripts never run in this pipeline's ref path). */
export function bodyDeclaresMargin(html, linkedCss = []) {
  const src = String(html ?? '');
  // The inline attribute is the highest-precedence author declaration.
  const bodyTag = /<body\b([^>]*)>/i.exec(src);
  const style = bodyTag && /\bstyle\s*=\s*(?:"([^"]*)"|'([^']*)')/i.exec(bodyTag[1]);
  if (style && MARGIN_DECL_RX.test(` ${style[1] ?? style[2]}`)) return true;
  // Every inline sheet, then every linked one, under the same rule walk.
  const sheets = [...src.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)].map((m) => m[1]);
  return [...sheets, ...linkedCss].some(cssDeclaresBodyMargin);
}

/** Read a page from disk plus its linked LOCAL stylesheets and apply
 *  bodyDeclaresMargin. Root-relative hrefs resolve against WPT_DIR (the
 *  corpus root, as the WPT server would), relative ones against the page's
 *  directory; remote or missing sheets are skipped and reported on stderr —
 *  never silently counted as "no margin". */
export function pageDeclaresBodyMargin(absPath, wptDir = WPT_DIR) {
  const html = readFileSync(absPath, 'utf8');
  const linked = [];
  for (const m of html.matchAll(/<link\b[^>]*>/gi)) {
    // Only stylesheet links carry author CSS.
    if (!/\brel\s*=\s*["']?stylesheet\b/i.test(m[0])) continue;
    const href = /\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i.exec(m[0]);
    const h = href && (href[1] ?? href[2] ?? href[3]);
    if (!h || /^[a-z]+:/i.test(h)) continue;   // remote: not readable here
    const p = h.startsWith('/') ? join(wptDir, h.slice(1)) : resolve(dirname(absPath), h);
    try { linked.push(readFileSync(p, 'utf8')); } catch {
      process.stderr.write(`[capture-browser-ref] body-margin probe: cannot read ${h} linked from ${absPath}\n`);
    }
  }
  return bodyDeclaresMargin(html, linked);
}

/** The Fix-B switch for one test (exported for the census and the pins):
 *  true iff the TEST declares a body margin and its REF does not — the only
 *  direction in which the frame's zero margin displaces the ref (the reverse
 *  pairs keep today's sheet: their test side is undeclared). */
export function uaBodyMarginFor(testAbs, refAbs, wptDir = WPT_DIR) {
  return pageDeclaresBodyMargin(testAbs, wptDir) && !pageDeclaresBodyMargin(refAbs, wptDir);
}

// ── wave-25 CAL-RC1: the image-space frame ───────────────────────────────────

/** Parse the `#RRGGBB` canvas background into an opaque RGBA quad.
 *
 *  Kept tiny and total: CANVAS_BG is the only input this ever sees, and a
 *  malformed literal must fail LOUDLY at import-adjacent call time rather
 *  than silently framing every ref in transparent black. */
export function parseHexRgb(hex) {
  const m = /^#([0-9a-f]{6})$/i.exec(String(hex).trim());
  // No silent fallthrough: an unparseable canvas colour is a contract bug,
  // not a pixel to guess at.
  if (!m) throw new Error(`capture-browser-ref: CANVAS_BG must be #RRGGBB, got ${hex}`);
  const n = parseInt(m[1], 16);
  return { r: (n >> 16) & 0xFF, g: (n >> 8) & 0xFF, b: n & 0xFF, a: 0xFF };
}

/** Decide what colour the image-space frame is painted with.
 *
 *  The frame stands in for the CSS canvas OUTSIDE the rendered content box.
 *  Two cases, and they need different answers:
 *    * ordinary ref (white page, ink somewhere inside) → the canvas is
 *      CANVAS_BG, and framing in white reproduces the old geometry exactly.
 *    * full-bleed ref (a body background covering the viewport — 17 of the
 *      302 cached refs, e.g. background-color-animation-in-body's olive) →
 *      the OLD ref painted that colour edge-to-edge, and so does every
 *      harness capture of it (verified on wave24-final: the composed web
 *      capture of that test is olive to the pixel border). Framing it white
 *      would manufacture a 32-px divergence band that no renderer caused.
 *
 *  Rule: sample 8 points on the image's border ring (4 corners + 4 edge
 *  midpoints). If ALL EIGHT are the same RGBA, that is the page's canvas
 *  colour and it becomes the pad. Otherwise the border carries content
 *  (e.g. css-values/attr-color-valid's green block touching the left edge
 *  while the right edge stays white) and we fall back to CANVAS_BG — the
 *  conservative choice, because smearing edge INK into the frame would
 *  invent geometry the capture does not have.
 *
 *  Known limit, stated rather than hidden: a full-bleed coloured page that
 *  ALSO paints ink touching its outer border reads as non-uniform and gets a
 *  CANVAS_BG frame. That pair loses the full-bleed match it had before. No
 *  such ref exists in the current 302-ref cache (the 17 full-bleed refs all
 *  have uniform rings). */
export function padColorFor(png, fallbackHex = CANVAS_BG) {
  const fallback = parseHexRgb(fallbackHex);
  const { width: w, height: h, data } = png;
  // Degenerate images have no ring to sample; the canvas default is the only
  // honest answer.
  if (!w || !h) return fallback;
  const at = (x, y) => {
    const i = (y * w + x) * 4;
    return [data[i], data[i + 1], data[i + 2], data[i + 3]];
  };
  const mx = Math.floor(w / 2), my = Math.floor(h / 2);   // edge midpoints
  const samples = [
    at(0, 0), at(w - 1, 0), at(0, h - 1), at(w - 1, h - 1), // corners
    at(mx, 0), at(mx, h - 1), at(0, my), at(w - 1, my),     // edge middles
  ];
  const [r, g, b, a] = samples[0];
  // Strict equality on all four channels: a ring that is "nearly" uniform is
  // a ring with ink on it, and anti-aliasing against the canvas is exactly
  // the signal we must not average away.
  const uniform = samples.every((s) => s[0] === r && s[1] === g && s[2] === b && s[3] === a);
  return uniform ? { r, g, b, a } : fallback;
}

/** Pad an encoded PNG by `pad` px on all four sides, in IMAGE space.
 *
 *  Pure (Buffer in → Buffer out) so the canvas contract is unit-testable
 *  without launching Chromium. The source rows are memcpy'd into the centre
 *  of a pre-filled canvas — no resampling, so every content pixel survives
 *  byte-identically and only its coordinates move by (+pad,+pad). That is
 *  the whole point: an image-space translation cannot distinguish in-flow
 *  from absolutely- or fixed-positioned content, which is precisely the
 *  distinction CSS padding made and that broke the ref frame. */
export function padPngBuffer(buffer, pad = CANVAS_PAD_PX, fallbackHex = CANVAS_BG) {
  const src = PNG.sync.read(buffer);
  // pad 0 is a legitimate no-op (a caller pinning "unframed" geometry);
  // re-encoding would still be lossless, but returning the input avoids a
  // pointless decode/encode round trip.
  if (pad <= 0) return buffer;
  const color = padColorFor(src, fallbackHex);
  const out = new PNG({ width: src.width + 2 * pad, height: src.height + 2 * pad });
  // Fill the whole canvas with the frame colour first, then blit content
  // over the middle — one pass each, and the frame colour is correct in the
  // corners (which a per-edge fill would have to special-case).
  for (let i = 0; i < out.data.length; i += 4) {
    out.data[i]     = color.r;
    out.data[i + 1] = color.g;
    out.data[i + 2] = color.b;
    out.data[i + 3] = color.a;
  }
  const srcRowBytes = src.width * 4;
  for (let y = 0; y < src.height; y++) {
    // Row-at-a-time copy: source row y lands at destination row y+pad,
    // horizontally offset by pad px (4 bytes/px).
    const srcStart = y * srcRowBytes;
    const dstStart = ((y + pad) * out.width + pad) * 4;
    src.data.copy(out.data, dstStart, srcStart, srcStart + srcRowBytes);
  }
  return PNG.sync.write(out);
}

/** Resolve the WPT SHA the same way bucket-wpt.mjs and fetch-wpt.sh do. */
async function resolveWptRef() {
  if (process.env.WPT_REF) return process.env.WPT_REF;
  const refFile = join(__dirname, 'WPT_REF');
  const raw = await fs.readFile(refFile, 'utf8');
  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    return trimmed.split(/\s+/)[0];
  }
  throw new Error('WPT_REF file contains no SHA line');
}

/** Resolve the on-disk path of the rel="match" reference for a test path.
 *
 *  wave-21 bookkeeping 6b (text-combine-emphasis investigation): a test can
 *  be a genuine reftest yet carry ONLY a rel="mismatch" link — its pass
 *  condition is "does NOT pixel-equal the ref", which this match-asserting
 *  pipeline cannot capture a comparable reference for (rendering the
 *  NOTREF would gate on the exact opposite of the test's semantics). Such
 *  tests throw an error carrying `skipReason: 'mismatch-only-reftest'` so
 *  captureRefs reports an EXPLICIT SKIP (not a FAIL) and the denominator
 *  stays honest; bucket-wpt.mjs now also pre-classifies them bucket-C so
 *  they never reach a section run in the first place. A test with neither
 *  link keeps the plain no-rel=match error (a real input mistake). */
export async function resolveRefPath(testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  const refHref = extractRefHref(html);
  if (!refHref) {
    // Distinguish "inverted reftest" from "not a reftest at all". The
    // regex mirrors bucket-wpt's relMatch shape; extractRefHref above
    // already proved there is no rel="match" link, so a mismatch hit here
    // means mismatch-ONLY.
    const mismatchOnly = /<link[^>]+rel=["']?mismatch\b/i.test(html);
    const err = new Error(mismatchOnly
      ? `mismatch-only reftest (rel="mismatch") in ${testRel} — explicitly skipped: pipeline asserts pixel-match only`
      : `no rel="match" link in ${testRel}`);
    // Machine-readable skip marker — consumed by captureRefs' catch.
    if (mismatchOnly) err.skipReason = 'mismatch-only-reftest';
    throw err;
  }
  return refHref.startsWith('/')
    ? join(WPT_DIR, refHref.slice(1))
    : resolve(dirname(testAbs), refHref);
}

// ── wave-24 A-RC1: browser-rev key derivation ────────────────────────────────
//
// Puppeteer's `browser.version()` returns a User-Agent-style product string
// — 'Chrome/150.0.7871.24' on the bundled build, 'HeadlessChrome/141.0.…'
// on older/`--headless=old` launches. Both name the SAME rasteriser family,
// so we normalise the product half ('HeadlessChrome' → 'chrome') and keep
// the FULL four-part build number: Chromium ships paint fixes in patch
// releases (the class of change this key exists to catch), so milestone-only
// granularity would still freeze a bug across a patch bump.
//
// The result is used as a filesystem path segment, so every character
// outside [A-Za-z0-9._-] is folded to '-' — a version string is never
// allowed to escape the cache root via '/' or '..'.
export const UNKNOWN_BROWSER_REV = 'browser-unknown';
export function browserRevFrom(versionString) {
  // No silent fallthrough: a missing/blank version is a REAL condition
  // (a stubbed browser in a unit test, a CDP hiccup). It gets its own
  // explicit bucket rather than silently sharing a slot with a real build.
  const raw = String(versionString ?? '').trim();
  if (!raw) return UNKNOWN_BROWSER_REV;
  // 'Product/1.2.3.4' → ['Product', '1.2.3.4']; a bare string with no '/'
  // keeps the whole thing as the product half and yields no build number.
  const slash = raw.indexOf('/');
  const product = (slash >= 0 ? raw.slice(0, slash) : raw).toLowerCase()
    // 'headlesschrome' and 'chrome' are the same binary in two launch
    // modes — collapsing them keeps a headless/headful flip from
    // invalidating an otherwise identical cache.
    .replace(/^headless/, '');
  const build = slash >= 0 ? raw.slice(slash + 1).trim() : '';
  const slug = build ? `${product}-${build}` : product;
  // Path-segment hardening (see banner) + a length cap so a pathological
  // UA string cannot produce an unusable filename. Runs of 2+ dots collapse
  // to one: separators are already folded above, so a literal '..' could
  // never be a traversal here — but leaving it in a directory name is a
  // trap for the next reader (and for any shell glob), and no real build
  // number contains one.
  const safe = slug.replace(/[^A-Za-z0-9._-]/g, '-').replace(/\.{2,}/g, '.').slice(0, 64);
  return safe || UNKNOWN_BROWSER_REV;
}

/** Compute the cache PNG path for a given test under a given WPT_REF.
 *
 *  `browserRev` (wave-24 A-RC1) selects WHICH tree:
 *    - omitted/null → the VERSION-LESS legacy path, byte-identical to the
 *      pre-wave-24 layout. This is the scorer view that run-titan.sh /
 *      section-runner.sh hand to inject-wpt-block.mjs as --refs-root, and
 *      the tree the ~10k already-captured PNGs live in.
 *    - a slug from browserRevFrom() → the authoritative versioned path,
 *      one extra segment BESIDE CANVAS_REV.
 */
export function cachePathFor(wptRef, testRel, browserRev = null) {
  const parts = testRel.split('/'); // posix
  // Spec section is the second segment when the test lives under
  // css/<section>/.... When a test lives directly under css/ (rare —
  // some CSS2 stragglers do), there's no section dir and we bucket it
  // under "css" so the path layout stays uniform.
  const section = parts.length >= 3 ? parts[1] : 'css';
  // wave-21 collision fix: subdir-encoded stem (safe-name.mjs fixtureStem)
  // so nested tests with equal basenames get DISTINCT cache PNGs. Top-level
  // tests keep the exact historical path — the existing cache stays valid.
  const stem = fixtureStem(testRel);
  // CANVAS_REV keys the canvas contract (corpus-v4 white canvas — header
  // note): a contract change re-renders every ref instead of silently
  // reusing PNGs captured under the old canvas. run-titan.sh and
  // section-runner.sh derive their --refs-root with the SAME segment.
  // The browser-rev segment (when asked for) sits BESIDE it, one level
  // deeper, so the version-less scorer view keeps its exact historical
  // shape and the two trees never interleave.
  return browserRev
    ? join(REFS_ROOT, wptRef, CANVAS_REV, browserRev, section, `${stem}.png`)
    : join(REFS_ROOT, wptRef, CANVAS_REV, section, `${stem}.png`);
}

/** Path of the claim file that credits the version-less tree to one browser
 *  rev. Lives at the ROOT of the canvas-rev tree (beside the section dirs,
 *  never inside one) so it can never be mistaken for a ref PNG. See the
 *  header's MIGRATION POLICY for the state machine it drives. */
export function legacyClaimPath(wptRef) {
  return join(REFS_ROOT, wptRef, CANVAS_REV, '.legacy-browser-rev');
}

/** Read the claim, or null when the version-less tree is unclaimed. */
async function readLegacyClaim(wptRef) {
  try {
    return (await fs.readFile(legacyClaimPath(wptRef), 'utf8')).trim() || null;
  } catch {
    // ENOENT is the normal pre-migration state, not an error worth
    // surfacing: an unclaimed tree is exactly what the first versioned run
    // expects to find, and it claims it below.
    return null;
  }
}

/** Link `src` to `dest` without copying bytes when the filesystem allows.
 *
 *  Hard links (fs.link) make the versioned tree and the scorer view share
 *  ONE inode, so mirroring ~10k PNGs costs directory entries and nothing
 *  else. `dest` is unlinked first: puppeteer's page.screenshot() opens its
 *  target with O_TRUNC, so writing through a live hard link would rewrite
 *  the other tree's bytes in place. copyFile is the documented fallback for
 *  the cross-device (EXDEV) and link-unsupported (EPERM) cases — correctness
 *  first, disk second. */
async function linkOrCopy(src, dest) {
  await fs.mkdir(dirname(dest), { recursive: true });
  await fs.rm(dest, { force: true });
  try {
    await fs.link(src, dest);
  } catch (err) {
    // No silent fallthrough: say which fallback fired and why, so a
    // surprising filesystem shows up in the capture log instead of as a
    // mysterious doubling of tools/wpt/refs.
    process.stderr.write(`[capture-browser-ref] link ${dest} failed (${err.code ?? err.message}) — copying instead\n`);
    await fs.copyFile(src, dest);
  }
}

/** Make the version-less scorer view point at the versioned PNG.
 *
 *  Cheap no-op on the common path: when both paths already resolve to the
 *  same inode there is nothing to do, which is the state after the first
 *  mirror of any given ref. */
async function mirrorToLegacy(versioned, legacy) {
  try {
    const [a, b] = await Promise.all([fs.stat(versioned), fs.stat(legacy)]);
    if (a.dev === b.dev && a.ino === b.ino) return;
    // Same path, different inode → the legacy entry is a stale copy (or a
    // pre-wave-24 original that was NOT adopted). The versioned tree is
    // authoritative, so it wins.
  } catch {
    // legacy missing (or unstattable) → fall through and (re)create it.
  }
  await linkOrCopy(versioned, legacy);
}

/** Render a single ref HTML to PNG. Returns the cache path.
 *
 *  wave-24 A-RC1 dual-read (header MIGRATION POLICY):
 *    1. versioned hit  → reuse, mirror to the scorer view, done.
 *    2. legacy hit AND the version-less tree is claimed by THIS browser
 *       rev → adopt it into the versioned tree (hard link, no re-render).
 *    3. otherwise → render, write the versioned PNG, mirror it.
 *  `adoptable` is resolved ONCE per run by captureRefs (one claim read for
 *  N tests) and passed in, so step 2 costs a single existsSync per test. */
async function renderOne(page, wptRef, testRel, browserRev, adoptable) {
  // Resolved up front (as before the wave-52 split) so a test with no
  // rel=match link fails/skips here even when a stale cache entry exists.
  await resolveRefPath(testRel);
  const dest   = cachePathFor(wptRef, testRel, browserRev);
  const legacy = cachePathFor(wptRef, testRel);           // scorer view
  if (existsSync(dest)) {
    await mirrorToLegacy(dest, legacy);
    return { dest, cached: true };
  }
  if (adoptable && existsSync(legacy)) {
    // Grandfathered ref: the claim file says this browser rev produced the
    // version-less tree, so the PNG is exactly what a re-render would
    // produce — adopting it is the whole reason the migration is lazy
    // rather than a ~10k-page re-render.
    await linkOrCopy(legacy, dest);
    return { dest, cached: true, adopted: true };
  }

  await fs.mkdir(dirname(dest), { recursive: true });
  // wave-52 L12-B: the render itself lives in renderRefPng (exported, so the
  // re-freeze A/B renders through the PRODUCTION path, never a copy of it).
  const { png, uaBodyMargin } = await renderRefPng(page, testRel);
  await fs.writeFile(dest, png);
  // wave-24 A-RC1: the versioned PNG is authoritative; the version-less
  // tree is the "latest capture wins" scorer view inject-wpt-block.mjs
  // reads through the shell scripts' hardcoded --refs-root (header
  // MIGRATION POLICY). Mirroring AFTER the write — never before — keeps
  // the O_TRUNC hazard documented on linkOrCopy impossible.
  await mirrorToLegacy(dest, legacy);
  return { dest, cached: false, uaBodyMargin };
}

/** Render ONE test's ref page to a framed PNG buffer under the live canvas
 *  contract — canvasFrameCss() for every ref, plus UA_BODY_CSS for the
 *  test-declares/ref-relies-on-UA pairs (uaBodyMarginFor). No cache logic:
 *  renderOne owns the cache; the wave-52 re-freeze A/B calls this directly
 *  so its "new contract" arm IS the shipped path.
 *  @returns {{ png: Buffer, refAbs: string, uaBodyMargin: boolean }} */
export async function renderRefPng(page, testRel) {
  const refAbs = await resolveRefPath(testRel);
  // Fix B decision, from the two SOURCES (static, no extra page load).
  const uaBodyMargin = uaBodyMarginFor(join(WPT_DIR, testRel), refAbs);

  // Render via file:// so the test's relative resource paths resolve.
  // Puppeteer requires the file:// URL to be absolute and properly encoded
  // for spaces / weird chars.
  const fileUrl = 'file://' + encodeURI(refAbs);

  // Chromeless wrapper page that frames the ref in our canvas. We can't
  // edit the WPT ref HTML in place (the corpus is gitignored, and we'd
  // pollute its hash). Instead we use Puppeteer's `page.goto(refUrl)` and
  // then evaluate a tiny CSS injection that pads the body and sets the
  // background. The ref's own root margin/padding still applies — the
  // injection is the OUTER frame, so reftest geometry stays intact.
  await page.goto(fileUrl, { waitUntil: 'load', timeout: 30_000 });
  // Frame the ref in our 390-wide WHITE canvas (CANVAS_BG — the corpus-v4
  // contract) WITHOUT clobbering any body/html styling the WPT ref itself
  // declares.
  //
  // Why `:where(...)` and not bare `html, body`: per the CSS Selectors L4
  // spec, `:where()` zeroes out the specificity of its argument list. A
  // bare `body { background: olive; }` in the ref (specificity 0,0,1)
  // therefore wins over our injected `:where(html, body) { background:
  // CANVAS_BG }` (specificity 0,0,0). This was the root cause of
  // https://…/pilot-001/css-backgrounds__background-color-animation-in-body
  // — the original `html, body { background }` injection silently
  // overrode the ref's own `body { background-color: rgb(100,100,0) }`,
  // turning every `body`-painted ref into a uniform CANVAS_BG square.
  //
  // Style precedence per CSS 2.1 §6.4.3 (cascading order, last step is
  // "later one wins" for equal specificity), so injecting *after* page
  // load with equal specificity would still win. `:where()` is the only
  // way to inject a true "default" that any author rule can override.
  //
  // PADDING IS GONE FROM THIS INJECTION (wave-25 CAL-RC1 — header note).
  // `:where(body){ padding: 16px }` shifted only IN-FLOW content: a static
  // body is not a containing block, so `position:absolute` overlays kept
  // resolving against the ICB at (0,0) and `position:fixed` against the
  // viewport — the ref was internally misaligned by (-16,-16) and punished
  // renderers that inset everything uniformly. The frame is now applied to
  // the RASTER (padPngBuffer below), where every box translates together.
  // `margin: 0` stays: it removes the UA's 8px body margin so the render
  // starts at the viewport origin, and zeroing it displaces nothing
  // relative to anything.
  //
  // `display: flow-root` REPLACES the one side effect the padding had that
  // was actually load-bearing. A padded body cannot collapse margins with
  // its first/last child; an unpadded one can, and that collapse escapes to
  // the root — measured on the css-color sample, every ref grew 16px taller
  // (390×600 → 390×616) purely from a `<p>`'s bottom margin leaving the
  // body box. flow-root establishes a block formatting context, which blocks
  // exactly that collapse, and — unlike `position: relative`, `transform`,
  // `contain` or `overflow: hidden` — it does NOT make the body a containing
  // block for absolutely-positioned descendants (CSS Position §3: only
  // position≠static / transform-like properties do), so the abspos fix above
  // survives it. Measured: with flow-root, 24/24 sampled refs keep their old
  // canvas height and the 13 in-flow-only ones are PIXEL-IDENTICAL to the
  // old contract, while all 11 abspos-overlay refs move (that movement IS
  // the fix).
  //
  // `color: #000` — the corpus-v4.1 ink+font sub-boundary, INK half
  // (header note). Real WPT pages paint default prose in the UA `color:
  // CanvasText` default, which is BLACK on the light-scheme white page
  // (html.css UA stylesheet); through corpus-v4.0 we injected the harness
  // `color: #fff` family instead, so default-ink text vanished on BOTH
  // sides of the diff and every prose test passed VACUOUSLY. Stating #000
  // explicitly (rather than deleting the declaration) pins the
  // light-scheme value even if the headless UA ever resolves CanvasText
  // differently (e.g. a forced dark scheme). `:where()` keeps it
  // zero-specificity, so any author `color` rule in the ref still wins —
  // exactly like a UA default. The three runtime WPT-mode bottom-outs
  // flip to the same black at this sub-boundary (see the header list);
  // the flip is WPT-mode-only, so the dark-stage 327-pair path keeps its
  // #eee-family ink byte-identically.
  //
  // `font-family: REF_FONT_STACK` — the FONT half of the same
  // sub-boundary. Without it the ref laid instruction prose out in
  // Chromium's default SERIF while every harness renders the bundled
  // Inter sans — different wrap points shifted everything below the prose
  // (the a98rgb cluster's whole failure; its color math is pixel-exact).
  // The @font-face preamble (interFontFaceCss) embeds the harness's own
  // Inter Regular/Bold as data URIs so the stack's first entry resolves
  // here too; `:where(body)` + inheritance carry the face to descendants
  // at zero specificity, so any author font rule in the ref still wins.
  //
  // `line-height: REF_LINE_HEIGHT` — the LINE-HEIGHT leg of the same
  // sub-boundary (constant doc above). With ink and face pinned, the last
  // ref-vs-capture prose divergence was VERTICAL RHYTHM: the ref's
  // `line-height: normal` Inter box (~20px @16px) vs the harnesses'
  // Round-4 calibrated ~18px box — 2px of drift PER PARAGRAPH down a
  // stacked test. Pinning an explicit unitless 1.25 here (20px @16px —
  // Chromium's measured natural rhythm, so ref pixels barely move) makes
  // the ref deterministic while the harness calibrations move to the same
  // 20px box. `:where(body)` + unitless inheritance keep it a true UA-like
  // default: any author line-height rule in the ref still wins.
  //
  // wave-25 round 3: the literal moved into canvasFrameCss() so the two
  // TEST-page bake paths inject the byte-identical sheet (they had drifted —
  // see that factory's banner). The prose above stays here because this is
  // where the contract is exercised.
  await page.addStyleTag({
    content: await canvasFrameCss(),
  });
  // wave-52 L12-B Fix B: the UA body box, SECOND and only for the pairs that
  // need it — same zero specificity, later in order, so it overrides the
  // frame's body margin/display/min-height and nothing else (UA_BODY_CSS).
  if (uaBodyMargin) await page.addStyleTag({ content: UA_BODY_CSS });

  // wave-25 CAL-RC1: render at the CONTENT width (358) — exactly what the
  // old CSS-padded body's content box measured, so wrap points and line
  // counts are unchanged — and restore the 390-wide canvas afterwards in
  // image space. Heights follow the same −32 shift: the floor is
  // REF_MIN_CANVAS_H − 2·pad so the PADDED PNG still floors at the historical
  // 600, and `min-height: 100vh` resolves to the same content box it did
  // under the padded border-box body (header AUDIT note), so `height: 100%`
  // children are byte-stable.
  // wave-25 round 3: the arithmetic moved to the exported REF_RENDER_MIN_HEIGHT
  // so the bake paths set the identical viewport (a local const here and a
  // second literal there is exactly how the pad drift happened).
  const innerFloor = REF_RENDER_MIN_HEIGHT;
  await page.setViewport({ width: REF_RENDER_WIDTH, height: innerFloor, deviceScaleFactor: 1 });
  // Let layout settle once at the standard height before measuring.
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  const docHeight = await page.evaluate((floor) => Math.max(
    document.documentElement.scrollHeight,
    document.body?.scrollHeight ?? 0,
    floor,
  ), innerFloor);
  await page.setViewport({ width: REF_RENDER_WIDTH, height: docHeight, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));

  // corpus-v4.1 FONT half: the injected Inter @font-face (base64 data-URI)
  // loads ASYNCHRONOUSLY — the settle timers above are not a font-ready
  // guarantee, and a screenshot taken before the face applies renders the
  // UA default (serif), silently diverging every prose wrap from the
  // harness captures. This exact race collapsed the first v4.1 section
  // run (css-color web 0.93 → 0.66). document.fonts.ready resolves when
  // all pending FontFace loads settle; the double-rAF then guarantees a
  // relayout with the loaded face has actually painted.
  await page.evaluate(() => document.fonts.ready.then(
    () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
  ));

  // wave-25 CAL-RC1: screenshot to a BUFFER (not straight to `dest`), frame
  // it in image space, then write the framed bytes. Writing the unframed PNG
  // first and rewriting it would leave a valid-looking but mis-sized ref on
  // disk if the process died between the two writes — and a stale-size ref
  // is exactly the kind of silent scoring corruption the cache keys exist to
  // prevent.
  const rawPng = await page.screenshot({ type: 'png' });
  return { png: padPngBuffer(rawPng, CANVAS_PAD_PX, CANVAS_BG), refAbs, uaBodyMargin };
}

// wave-16 POST-LOAD: the launch flag set, factored to an exported const so
// post-load-extract.mjs drives the SAME Chromium configuration (CPU raster,
// no throttling, no focus steal) — the settle/behaviour lessons recorded on
// each flag below were paid for once and must not fork per capture path.
export const BROWSER_LAUNCH_ARGS = [
      // Force CPU rasterization — see capture-screenshots.mjs. Two reasons
      // it matters HERE too: (1) rendering a real WPT reference page would
      // otherwise deadlock Page.captureScreenshot the same way the platform
      // capture did; (2) the browser-ref and the platform-web capture MUST
      // use the same raster backend or a GPU-vs-CPU sub-pixel delta would
      // depress every web-ref SSIM. Both CPU → apples-to-apples.
      '--disable-gpu',
      '--disable-background-timer-throttling',
      '--disable-renderer-backgrounding',
      '--disable-backgrounding-occluded-windows',
      // macOS focus-steal hardening — mirrors capture-screenshots.mjs.
      // Critical for TITAN swarm: 8+ parallel browser-ref captures
      // would otherwise flash the dock repeatedly.
      //
      // DO NOT add `--no-startup-window` here. It tells Chrome not to
      // open any window at startup, which prevents puppeteer from
      // creating a page target — `puppeteer.launch()` then hangs (no
      // window to host newPage), every `page.waitForSelector` times out
      // at 30 s, and every browser-ref capture fails with "TimeoutError"
      // → section-runner reports `browser-ref had failures` and the
      // downstream classifier sees no reference frames. See
      // apps/web-harness/capture-screenshots.mjs for the matching fix.
      '--no-default-browser-check',
      '--no-first-run',
      '--disable-features=Translate,MediaRouter,OptimizationHints',
];

/** Render N tests with one shared browser instance. Returns per-test status. */
export async function captureRefs(testRels, opts = {}) {
  const wptRef = opts.wptRef ?? await resolveWptRef();
  const browser = await puppeteer.launch({
    headless: 'new',
    // Match capture-screenshots.mjs's flag set so any timer-throttling
    // weirdness behaves identically across the two capture paths (the flag
    // set itself lives in the exported BROWSER_LAUNCH_ARGS above).
    args: BROWSER_LAUNCH_ARGS,
    protocolTimeout: 300_000,
  });

  // wave-24 A-RC1: resolve the cache's fourth key — the Chromium build
  // that will rasterise every ref in this run — ONCE, right after launch.
  const browserRev = browserRevFrom(await browser.version());
  // Claim state for the version-less tree, read ONCE for the whole run
  // (header MIGRATION POLICY): an unclaimed tree is the pre-wave-24 corpus
  // and is credited to the browser that first runs under versioning; a
  // tree claimed by a DIFFERENT rev is retired — nothing is adopted from
  // it, so every test this run touches re-renders under the live browser.
  const claim = await readLegacyClaim(wptRef);
  const adoptable = claim === null || claim === browserRev;
  if (claim === null) {
    // Establish the claim before any adoption so an interrupted run cannot
    // leave adopted PNGs behind an unclaimed tree.
    await fs.mkdir(dirname(legacyClaimPath(wptRef)), { recursive: true });
    await fs.writeFile(legacyClaimPath(wptRef), `${browserRev}\n`, 'utf8');
  } else if (!adoptable && !opts.quiet) {
    // LOUD, never silent: this is the moment a browser upgrade starts
    // costing real re-renders, and the log is where that shows up.
    process.stderr.write(`[capture-browser-ref] browser rev ${browserRev} != legacy claim ${claim} ` +
      `— version-less refs retired; tests in this run re-render\n`);
  }

  const results = [];
  try {
    const page = await browser.newPage();
    page.on('pageerror', (err) => console.error('[pageerror]', err.message));

    let i = 0;
    for (const rel of testRels) {
      i++;
      try {
        const { dest, cached, adopted, uaBodyMargin } = await renderOne(page, wptRef, rel, browserRev, adoptable);
        results.push({ test: rel, dest, cached, adopted: !!adopted, uaBodyMargin: !!uaBodyMargin, ok: true });
        if (!opts.quiet) {
          // An adoption IS a cache hit (no render happened), so the line
          // must keep the literal `cache ` prefix section-runner.sh counts
          // with `grep -c 'cache '` — the adoption note rides as a suffix
          // instead of replacing the verb, or an all-adopt migration run
          // would report `rendered=0 cached=0` and read as total failure.
          // wave-52 L12-B: a ref rendered with the UA body box says so, so
          // the Fix-B population is readable off every browser-ref.log.
          const note = adopted ? ' (adopted from the version-less tree)'
            : (uaBodyMargin ? ' (ua body margin: test declares, ref relies on UA)' : '');
          process.stderr.write(
            `  [${i}/${testRels.length}] ${cached ? 'cache' : 'rendered'} ${rel}${note}\n`);
        }
      } catch (err) {
        // wave-21 6b: mismatch-only reftests are an EXPLICIT SKIP, not a
        // failure — no ref image can honestly exist for an inverted
        // assertion (see resolveRefPath). `ok: true` keeps the exit code
        // clean; `skipped` + `reason` keep the log and any consumer honest
        // about WHY there is no PNG for this test.
        if (err.skipReason) {
          results.push({ test: rel, ok: true, skipped: true, reason: err.skipReason });
          if (!opts.quiet) {
            process.stderr.write(`  [${i}/${testRels.length}] SKIP  ${rel}: ${err.message}\n`);
          }
          continue;
        }
        results.push({ test: rel, ok: false, error: err.message ?? String(err) });
        if (!opts.quiet) {
          process.stderr.write(`  [${i}/${testRels.length}] FAIL  ${rel}: ${err.message ?? err}\n`);
        }
      }
    }
  } finally {
    await browser.close();
  }

  // browserRev rides the return so every caller (and the CLI banner) can
  // record WHICH Chromium produced this run's refs — the provenance the
  // pre-wave-24 cache silently dropped.
  return { wptRef, browserRev, results };
}

// ── CLI ──────────────────────────────────────────────────────────────────────
async function main() {
  const inputs = process.argv.slice(2);
  if (inputs.length === 0) {
    console.error('usage: capture-browser-ref.mjs <relative-test-path>...');
    process.exit(1);
  }
  const { wptRef, browserRev, results } = await captureRefs(inputs);
  // wave-21 6b: report skips as their own column — a skipped mismatch-only
  // reftest is neither a success (no PNG exists) nor a failure (nothing
  // broke), and folding it into either would re-hide the denominator.
  const skipped = results.filter((r) => r.skipped).length;
  const ok = results.filter((r) => r.ok && !r.skipped).length;
  const fail = results.length - ok - skipped;
  // wave-24 A-RC1: browserRev in the banner makes ref provenance visible in
  // every section log (section-runner.sh tees this to browser-ref.log), so a
  // Chromium upgrade is readable from the archived run instead of invisible.
  console.log(`capture-browser-ref: wptRef=${wptRef.slice(0, 12)}  browser=${browserRev}  ok=${ok}  fail=${fail}  skipped=${skipped}`);
  process.exit(fail > 0 ? 1 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('capture-browser-ref: fatal:', err);
    process.exit(2);
  });
}
