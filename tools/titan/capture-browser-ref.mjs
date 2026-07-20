#!/usr/bin/env node
//
// tools/titan/capture-browser-ref.mjs — Phase 1 browser-ref capture.
//
// Implements TITAN_ARCHITECTURE.md Section 5.2: render every WPT test's
// `*-ref.html` directly in headless Chromium and save it as the
// "spec-truth" reference image.
//
// Cache layout (Section 5.4, revised at the corpus-v4 white-canvas boundary):
//   tools/wpt/refs/<wpt-sha>/<canvas-rev>/<spec-section>/<test-stem>.png
//
// We key on the WPT_REF SHA so re-pinning regenerates the cache; the extra
// <canvas-rev> segment (CANVAS_REV below) keys the CANVAS CONTRACT so a
// canvas change regenerates the cache too — the corpus-v1..v3 dark-canvas
// refs stay untouched at tools/wpt/refs/<sha>/<section>/ for historical
// reproduction. Everything else hits cache on subsequent runs (the corpus
// is byte-identical for a given pin, so the rendered ref PNG is too within
// AA noise).
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
import { resolve, dirname, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

import { extractRefHref } from './extract-fixture.mjs';

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
export const CANVAS_REV = 'white-black-ink-font-lh';
// wave-16 POST-LOAD: exported (was module-private) so post-load-extract.mjs
// can frame the TEST page in the identical canvas the ref capture uses —
// computed geometry snapshotted under a different pad would bake a
// systematic offset into every overridden inset.
export const CANVAS_PAD_PX = 16;

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

/** Resolve the on-disk path of the rel="match" reference for a test path. */
export async function resolveRefPath(testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  const refHref = extractRefHref(html);
  if (!refHref) throw new Error(`no rel="match" link in ${testRel}`);
  return refHref.startsWith('/')
    ? join(WPT_DIR, refHref.slice(1))
    : resolve(dirname(testAbs), refHref);
}

/** Compute the cache PNG path for a given test under a given WPT_REF. */
export function cachePathFor(wptRef, testRel) {
  const parts = testRel.split('/'); // posix
  // Spec section is the second segment when the test lives under
  // css/<section>/.... When a test lives directly under css/ (rare —
  // some CSS2 stragglers do), there's no section dir and we bucket it
  // under "css" so the path layout stays uniform.
  const section = parts.length >= 3 ? parts[1] : 'css';
  const stem = basename(parts[parts.length - 1], '.html');
  // CANVAS_REV keys the canvas contract (corpus-v4 white canvas — header
  // note): a contract change re-renders every ref instead of silently
  // reusing PNGs captured under the old canvas. run-titan.sh and
  // section-runner.sh derive their --refs-root with the SAME segment.
  return join(REFS_ROOT, wptRef, CANVAS_REV, section, `${stem}.png`);
}

/** Render a single ref HTML to PNG. Returns the cache path. */
async function renderOne(page, wptRef, testRel) {
  const refAbs = await resolveRefPath(testRel);
  const dest = cachePathFor(wptRef, testRel);
  if (existsSync(dest)) return { dest, cached: true };

  await fs.mkdir(dirname(dest), { recursive: true });

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
  // Padding is handled the same way: `:where(body) { padding }` lets a
  // ref that explicitly sets its own body padding/margin keep it.
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
  await page.addStyleTag({
    content: `
      ${await interFontFaceCss()}
      :where(html, body) { margin: 0; padding: 0; background: ${CANVAS_BG}; }
      :where(body) { padding: ${CANVAS_PAD_PX}px; box-sizing: border-box;
                     min-height: 100vh; color: #000;
                     font-family: ${REF_FONT_STACK};
                     line-height: ${REF_LINE_HEIGHT}; }
    `,
  });

  // Match the existing capture pipeline: viewport 390 wide, height = full
  // document height so we capture the whole reftest output without scroll
  // clipping.
  await page.setViewport({ width: CANVAS_WIDTH, height: 600, deviceScaleFactor: 1 });
  // Let layout settle once at the standard height before measuring.
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  const docHeight = await page.evaluate(() => Math.max(
    document.documentElement.scrollHeight,
    document.body?.scrollHeight ?? 0,
    600,
  ));
  await page.setViewport({ width: CANVAS_WIDTH, height: docHeight, deviceScaleFactor: 1 });
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

  await page.screenshot({ path: dest, type: 'png' });
  return { dest, cached: false };
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

  const results = [];
  try {
    const page = await browser.newPage();
    page.on('pageerror', (err) => console.error('[pageerror]', err.message));

    let i = 0;
    for (const rel of testRels) {
      i++;
      try {
        const { dest, cached } = await renderOne(page, wptRef, rel);
        results.push({ test: rel, dest, cached, ok: true });
        if (!opts.quiet) {
          process.stderr.write(`  [${i}/${testRels.length}] ${cached ? 'cache' : 'rendered'} ${rel}\n`);
        }
      } catch (err) {
        results.push({ test: rel, ok: false, error: err.message ?? String(err) });
        if (!opts.quiet) {
          process.stderr.write(`  [${i}/${testRels.length}] FAIL  ${rel}: ${err.message ?? err}\n`);
        }
      }
    }
  } finally {
    await browser.close();
  }

  return { wptRef, results };
}

// ── CLI ──────────────────────────────────────────────────────────────────────
async function main() {
  const inputs = process.argv.slice(2);
  if (inputs.length === 0) {
    console.error('usage: capture-browser-ref.mjs <relative-test-path>...');
    process.exit(1);
  }
  const { wptRef, results } = await captureRefs(inputs);
  const ok = results.filter((r) => r.ok).length;
  const fail = results.length - ok;
  console.log(`capture-browser-ref: wptRef=${wptRef.slice(0, 12)}  ok=${ok}  fail=${fail}`);
  process.exit(fail > 0 ? 1 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('capture-browser-ref: fatal:', err);
    process.exit(2);
  });
}
