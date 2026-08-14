//
// tools/titan/svg-preraster.mjs — wave-40 lane T5: the SVG PRE-RASTER hop.
//
// A HOST-SIDE pre-pass for the replaced-element asset channel (wave-39 lane
// A2, feed-lib.mjs's hop banner): before either native feeder delivers a
// document's `meta.attrs.src` files, every SVG among them is rasterised to a
// PNG SIBLING by the headless Chromium the pipeline already runs, and the
// NATIVE copy of the wire is re-pointed at that PNG. The web harness is not
// touched — it keeps the real vector, which its browser renders natively.
//
// ── Why this hop exists ─────────────────────────────────────────────────────
//
// Neither native ships an SVG rasteriser. Both DocumentImageRegistry twins say
// so in their format tables, in the honest way: Android's BitmapFactory has no
// SVG decoder at all, and iOS decodes SVG ONLY out of a compiled asset catalog
// (there is no public API turning an arbitrary SVG FILE into a UIImage). So
// wave-39 delivered these files to both sandboxes and both declined them by
// NAME — a loud, correct decline that still leaves an EMPTY BOX where the
// browser ref paints the image.
//
// MEASURED (tools/titan/runs/wave39-final, the composed browser-ref scores):
// every SVG-referencing test in the depth-48 corpus is the css-ui box-sizing
// 007..025 cluster (19 tests) plus css-flexbox align-items-007. Eleven of the
// nineteen fail on BOTH natives with the image simply absent — box-sizing-009
// scores web 0.9811 PASS against iOS 0.5390 / Android 0.5380, and its capture
// shows one green rectangle (a plain `<div>`) where the ref shows six (the
// other five are `<img src=…h100.svg>`).
//
// The platforms cannot grow an SVG rasteriser. The HOST already has one — the
// same Chromium build that rasterises every browser-ref PNG. Moving the raster
// one hop upstream is the whole idea.
//
// ── The SCALE CONTRACT (read this before changing anything) ─────────────────
//
// A vector has no pixels; a PNG has exactly one set. This hop therefore makes
// an irreversible choice, and the choice is: **the raster is written at the
// SVG's CONCRETE OBJECT SIZE — the `naturalWidth` × `naturalHeight` Chromium
// reports for an `<img>` pointed at that file — at deviceScaleFactor 1.**
//
// That is NOT a quality knob; it is the only size that keeps the wire honest.
// On both natives the delivered raster's PIXEL DIMENSIONS *are* the CSS
// intrinsic size of the replaced element:
//
//   * images/ReplacedImageContent.{kt,swift} `Mode.INTRINSIC` (neither axis
//     declared — the whole box-sizing 007..009 family) sizes the content to
//     `decoded.intrinsicWidthPx` dp / pt DIRECTLY;
//   * the two single-axis modes derive the free axis from
//     `DecodedImage.aspectRatio`, which is computed from those same pixels;
//   * `object-fit: contain/cover` maps the raster into the box by that ratio.
//
// So a raster written at 2× "for crispness" would silently double every
// auto-sized replaced box, and one written at a component's USED size would
// flatten the intrinsic ratio to the box ratio and turn `object-fit: contain`
// into a no-op. Both were considered and rejected on that evidence; the
// natural size is the one scale at which the PNG is a drop-in INTRINSIC
// EQUIVALENT of the vector it stands in for. `naturalWidth/naturalHeight` is
// exactly css-images-3 §5.1's default sizing algorithm run against the
// 300×150 default object size, which is also what the ref browser used, so
// the two sides agree by construction rather than by luck.
//
// PROBED (Chromium 141, tools/wpt/css/css-ui/support/):
//   w100_h100.svg  → 100×100   (both intrinsic dimensions)
//   w100.svg       → 100×150   (intrinsic width only; 150 = default object h)
//   h100.svg       → 300×100   (intrinsic height only; 300 = default object w)
//   r1-1.svg       → 150×150   (ratio only — no intrinsic size at all)
//   w100_r1-1.svg  → 100×100   (intrinsic width + ratio)
//   h100_r1-1.svg  → 100×100   (intrinsic height + ratio)
//
// WHAT IS STILL LOST, stated plainly rather than hidden:
//   1. ONE SIZE. A document that paints the same SVG in a 20 px box and a
//      400 px box gets one raster for both; the small one is downsampled and
//      the large one is bilinearly upscaled by the platform. A vector would
//      be crisp at both.
//   2. "NO intrinsic size" is UNREPRESENTABLE. `r1-1.svg` has a 1:1 ratio and
//      no intrinsic dimensions; its PNG necessarily asserts 150×150. The
//      RATIO survives (which is what the single-axis rules need), the absence
//      does not. A box that leaves BOTH axes auto will therefore hug 150×150
//      on the natives where a browser stretch-fits the containing block.
//   3. Anything the SVG reaches for OUTSIDE itself (an external stylesheet, a
//      remote font, a nested <image href>) is not fetched: the file is loaded
//      as a self-contained `data:` payload, so such a reference renders as it
//      would with the network off.
// Every one of those is a NARROWING of the vector, never a widening, and each
// is stamped: the feeders log the raster and its size, and both natives log a
// PRE-RASTER line when a `*.svg.png` resolves (DocumentImageRegistry).
//
// ── The naming contract ─────────────────────────────────────────────────────
//
// `<path>.svg` → `<path>.svg.png`: the extension is APPENDED, never replaced.
// Two reasons, both load-bearing:
//   * the original stem stays legible in every log, on-device path and adb
//     listing, so an investigator reading `h100.svg.png` knows instantly that
//     it is a stand-in and for what;
//   * replacement (`h100.png`) could COLLIDE with a real `h100.png` sibling
//     the corpus already ships, and silently deliver the wrong picture.
// The raster is a SIBLING of the vector for the same reason the feeders keep
// the corpus-relative path verbatim: no name mangling means no escaping rule
// to drift between the four codebases, and the existing
// `resolveReplacedImageFile` containment + extension checks accept it with no
// change at all.
//
// ── Where it runs, and what it must never touch ─────────────────────────────
//
// The rewrite is applied to the feeders' IN-MEMORY copy of the document only.
// The per-test IR on disk, the section fixture, the converter output and the
// web harness's bundle all keep the real `.svg` — this is a NATIVE-side
// stand-in, not a corpus edit, and the web score must stay byte-identical.
//

import { promises as fs } from 'node:fs';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** The suffix appended to a vector's path to name its raster stand-in. See
 *  the naming-contract banner: APPENDED, never substituted. */
export const PRERASTER_SUFFIX = '.png';

/** MIME type used for the `data:` payload the rasteriser loads. Fixed rather
 *  than sniffed: this hop only ever admits `.svg`, and a wrong type here would
 *  make Chromium decline the image with an `onerror` that reads like a corrupt
 *  file. */
const SVG_MIME = 'image/svg+xml';

/** css-images-3 §5.1 DEFAULT OBJECT SIZE, restated as a sanity CEILING rather
 *  than as an input: Chromium already applies it inside `naturalWidth`. A
 *  raster larger than this in BOTH axes is fine (a genuinely 800×600 SVG), so
 *  the ceiling below is only about pathological documents. */
const MAX_RASTER_EDGE = 4096;

/** Is this wire `src` a vector this hop will stand in for?
 *
 *  PURE and deliberately narrow: a corpus-relative path whose extension is
 *  `.svg`. A `data:` URI is EXCLUDED — its payload already rides the wire and
 *  the natives decline it in their own data: branch, which is a different
 *  (and separately stamped) gap; standing in for it would mean inventing a
 *  file the wire never named. An absolute or protocol path is excluded for the
 *  same containment reason `resolveReplacedImageFile` excludes it. */
export function isPrerasterCandidate(src) {
  if (typeof src !== 'string') return false;
  const v = src.trim();
  if (v === '') return false;
  if (/^data:/i.test(v)) return false;
  if (v.startsWith('/') || /^[a-z][a-z0-9+.-]*:/i.test(v)) return false;
  return /\.svg$/i.test(v);
}

/** The raster stand-in path for one vector `src` (naming contract above).
 *  Pure string math — says nothing about whether the file exists. */
export function prerasterSrcFor(src) {
  return `${src}${PRERASTER_SUFFIX}`;
}

/** Does this `src` name a HOST PRE-RASTER (rather than an authored PNG)?
 *
 *  The exact predicate both natives' DocumentImageRegistry twins implement, so
 *  the host and the two devices agree on what deserves the stamp. Kept here
 *  too — and unit-pinned on all three codebases — because a drift between them
 *  would mean a raster painting with no line in any log saying it was one. */
export function isPrerasterSrc(src) {
  return typeof src === 'string' && /\.svg\.png$/i.test(src.trim());
}

/**
 * Rewrite a decoded IR document's replaced-element sources onto their raster
 * stand-ins, IN PLACE, for the NATIVE side only.
 *
 * `rasterized` is the map of vector `src` → raster `src` that
 * [prerasterizeSvgSources] actually PRODUCED. Only those are rewritten: a
 * vector whose raster failed (or was never attempted) keeps its `.svg` on the
 * wire so the runtime's existing format decline fires and the gap stays
 * visible. Silently rewriting to a file that does not exist would swap a
 * precise "this platform has no SVG rasteriser" for a misleading "the feeder
 * hop did not deliver it".
 *
 * Walks the v2 FLAT component list and, for robustness, a v1 `children` nest —
 * the same shape and for the same reason as feed-lib.mjs's
 * `documentReplacedSrcs`, whose tag table this mirrors by construction (the
 * rewrite reads the src the feeder is about to deliver, so restricting the
 * tag set again here could only ever DESYNC the two).
 *
 * Returns the list of `{ src, rasterSrc }` pairs actually applied, in document
 * order and deduped, for the feeder's log line.
 */
export function applyPrerasterRewrite(doc, rasterized) {
  const applied = [];
  const seen = new Set();
  const visit = (c) => {
    if (!c || typeof c !== 'object') return;
    const attrs = c.meta?.attrs;
    const src = typeof attrs?.src === 'string' ? attrs.src.trim() : '';
    const rasterSrc = src ? rasterized.get(src) : undefined;
    if (rasterSrc) {
      attrs.src = rasterSrc;
      if (!seen.has(src)) { seen.add(src); applied.push({ src, rasterSrc }); }
    }
    for (const kid of Array.isArray(c.children) ? c.children : []) visit(kid);
  };
  for (const c of Array.isArray(doc?.components) ? doc.components : []) visit(c);
  return applied;
}

/** Every distinct `.svg` replaced-source across a batch of decoded documents,
 *  in first-seen order. The feeders call this ONCE per run so a single browser
 *  launch covers the whole section (48 tests share six support vectors in the
 *  css-ui cluster). `srcsOf` is injected so this stays testable without
 *  importing the feeder's document walker. */
export function collectSvgSources(docs, srcsOf) {
  const out = [];
  const seen = new Set();
  for (const doc of docs) {
    for (const src of srcsOf(doc)) {
      if (!isPrerasterCandidate(src) || seen.has(src)) continue;
      seen.add(src);
      out.push(src);
    }
  }
  return out;
}

/**
 * The ONE entry point both feeders call: read a batch of per-test IR files,
 * find every `.svg` replaced-source among them, and rasterise each once.
 *
 * Lives here rather than being cloned into the two feeders — unlike the font
 * and image HOPS, which are deliberate clones because each writes into a
 * different sandbox with different semantics, this pre-pass writes into the
 * shared CORPUS and must produce byte-identical rasters for both natives. Two
 * copies of it would be two chances for iOS and Android to be scored against
 * differently-sized stand-ins, which is precisely the divergence the campaign
 * exists to eliminate.
 *
 * An unreadable/unparsable fixture is SKIPPED silently here: the feeders' own
 * loops read the same files a moment later and report the failure with their
 * per-fixture row, so reporting it twice would double-count it.
 *
 * @param {string[]} fixturePaths per-test IR JSON paths (the feeder's --fixtures list)
 * @param {object}   opts         wptDir / log / force, plus `srcsOf` (feed-lib's
 *                                documentReplacedSrcs — injected so this module
 *                                does not import the feeder library)
 */
export async function prerasterizeFixtures(fixturePaths, opts) {
  const docs = [];
  for (const fx of fixturePaths) {
    try { docs.push(JSON.parse(await fs.readFile(fx, 'utf8'))); }
    catch { /* the feeder's own loop reports this file */ }
  }
  const srcs = collectSvgSources(docs, opts.srcsOf);
  return prerasterizeSvgSources(srcs, opts);
}

/** Is an existing raster still good for this vector? TRUE only when the PNG
 *  exists and is NOT older than the SVG.
 *
 *  Deliberately mtime-based rather than content-hashed: the corpus is a
 *  read-only mirror materialised by fetch-wpt.sh, so a vector only changes
 *  when the pin moves, and a re-fetch rewrites mtimes. `>=` (not `>`) because
 *  a checkout can land both files in the same second. Pass `force` to ignore
 *  the cache entirely — the escape hatch for a Chromium upgrade, which changes
 *  the rasteriser without touching either file. */
async function rasterIsFresh(svgAbs, pngAbs, force) {
  if (force) return false;
  try {
    const [svgStat, pngStat] = await Promise.all([fs.stat(svgAbs), fs.stat(pngAbs)]);
    return pngStat.mtimeMs >= svgStat.mtimeMs;
  } catch {
    return false;
  }
}

/**
 * Rasterise one already-read SVG payload in an open page, at its natural size.
 *
 * Split out of [prerasterizeSvgSources] so the browser lifecycle and the
 * per-file work read separately, and so a single failure is a per-file decline
 * rather than a run-ending throw.
 *
 * The vector is loaded as a self-contained `data:` payload rather than a
 * `file://` URL: a page at `about:blank` may not read `file://` images at all
 * (Chromium's file-origin rule), and the alternative — `--allow-file-access-
 * from-files` — would widen what every OTHER capture in this process may read.
 * The cost is limitation 3 in the header banner, which is documented, narrow,
 * and true of the corpus's support vectors either way.
 *
 * Returns `{ width, height }` of the written PNG, or throws with a message the
 * caller turns into a per-file decline.
 */
async function rasterizeOne(page, svgBytes, pngAbs) {
  const uri = `data:${SVG_MIME};base64,${svgBytes.toString('base64')}`;
  // A blank, TRANSPARENT stage. `omitBackground` below preserves the alpha
  // channel, which matters because the replaced element paints its own
  // `background` UNDER the image (the box-sizing family sets `background:
  // white` on every `<img>`); baking an opaque backdrop into the raster would
  // paint a rectangle the stylesheet never asked for.
  await page.setContent(
    '<!doctype html><html style="background:transparent">' +
    '<body style="margin:0;background:transparent"></body></html>',
  );
  // Step 1 — ASK THE BROWSER for the concrete object size. This is the whole
  // scale contract: `naturalWidth/naturalHeight` on an `<img>` is css-images-3
  // §5.1's default sizing algorithm, already run, by the same engine that
  // rasterised the reference PNG we are scored against.
  const nat = await page.evaluate((u) => new Promise((res) => {
    const im = new Image();
    im.onload = () => res({ w: im.naturalWidth, h: im.naturalHeight });
    im.onerror = () => res(null);
    im.src = u;
  }), uri);
  if (!nat) throw new Error('Chromium declined the vector (onerror) — malformed SVG?');
  const width = Math.round(nat.w);
  const height = Math.round(nat.h);
  if (!(width > 0) || !(height > 0)) {
    throw new Error(`degenerate natural size ${nat.w}x${nat.h}`);
  }
  if (width > MAX_RASTER_EDGE || height > MAX_RASTER_EDGE) {
    throw new Error(`natural size ${width}x${height} exceeds the ${MAX_RASTER_EDGE}px raster ceiling`);
  }
  // Step 2 — paint it at exactly that size, one device pixel per CSS px.
  // `deviceScaleFactor: 1` is the other half of the scale contract: a 2 here
  // would write a raster whose pixel dimensions are twice the intrinsic size
  // the natives read out of them.
  await page.setViewport({ width, height, deviceScaleFactor: 1 });
  await page.evaluate((u, w, h) => new Promise((res) => {
    const im = new Image();
    im.onload = () => res(true);
    im.onerror = () => res(false);
    im.style.cssText = `display:block;position:absolute;left:0;top:0;width:${w}px;height:${h}px`;
    im.src = u;
    document.body.appendChild(im);
  }), uri, width, height);
  await fs.mkdir(dirname(pngAbs), { recursive: true });
  // `omitBackground` keeps the alpha channel (see the transparent stage
  // above). The clip is the full viewport, which IS the natural size.
  await page.screenshot({ path: pngAbs, omitBackground: true, captureBeyondViewport: false });
  return { width, height };
}

/**
 * Rasterise every SVG in `srcs` to its PNG sibling under `wptDir`.
 *
 * Returns a Map of vector `src` → raster `src` for the ones that SUCCEEDED —
 * exactly the map [applyPrerasterRewrite] consumes. A failure is a per-file
 * decline: it is logged, omitted from the map, and the vector keeps riding the
 * wire so the runtime's own format decline fires. Nothing here is ever fatal;
 * failing the section would hide every other property its tests measure, which
 * is the same argument the two asset hops make about their own declines.
 *
 * The browser is launched LAZILY (and `puppeteer` imported dynamically) so a
 * section with no vectors — which is 29 of the 30 in the depth-48 corpus —
 * pays nothing at all for this hop existing.
 *
 * @param {string[]} srcs     corpus-relative vector paths (from collectSvgSources)
 * @param {object}   opts
 * @param {string}   opts.wptDir  corpus root; a src that escapes it is declined
 * @param {Function} opts.log     one-line logger (the feeder's own)
 * @param {boolean} [opts.force]  ignore the mtime cache (Chromium upgrades)
 * @param {Function} [opts.launch] puppeteer.launch override, for tests
 */
export async function prerasterizeSvgSources(srcs, opts) {
  const rasterized = new Map();
  const log = opts.log ?? (() => {});
  if (!Array.isArray(srcs) || srcs.length === 0) return rasterized;
  // ── THE SWITCH, and why it defaults OFF ────────────────────────────────────
  //
  // `TITAN_SVG_PRERASTER=1` engages the hop; anything else (including unset)
  // leaves both natives in their exact pre-wave-40 behaviour — the vector
  // rides the wire, the runtime declines it by name, the box paints empty.
  //
  // OFF is the default ON PURPOSE, and the reason is a MEASUREMENT THAT HAS
  // NOT HAPPENED YET, not caution for its own sake. Of the 19 css-ui
  // box-sizing tests this hop targets, eleven fail on both natives today with
  // the image entirely absent (box-sizing-009: web 0.9811 PASS vs iOS 0.5390 /
  // Android 0.5380) and the raster can only help them. The other EIGHT — 010,
  // 011, 014, 015, 016, 017, 018, 019 — currently PASS VACUOUSLY at 0.9561 /
  // 0.9570: they pass *because* the image is missing and the missing area is
  // small. Each of those constrains the box with `max-width`/`max-height` plus
  // `box-sizing: border-box`, and ReplacedBoxSizing deliberately does NOT model
  // CSS 2.1 §10.4's constraint-violation rules for replaced content (its own
  // header says so), so a delivered raster will paint its INTRINSIC size on
  // the unconstrained axis and overshoot the reference box — 150 px of green
  // where box-sizing-016's ref has 70. Whether that overshoot costs those
  // eight their pass is an SSIM question, and SSIM questions are answered on a
  // device, not in a comment.
  //
  // So the hop ships COMPLETE and OFF: the gate is byte-identical with it off
  // (no rewrite, no browser, no delivery change — the empty map makes
  // applyPrerasterRewrite a no-op), and the A/B that decides the default is a
  // single variable flip on one binary:
  //
  //   node tools/titan/feed-{android,ios}.mjs --fixtures <19 per-test IR> \
  //       --out <arm> --composed --wpt-dir tools/wpt --udid <dev>
  //   #  arm A: TITAN_SVG_PRERASTER unset   arm B: TITAN_SVG_PRERASTER=1
  //   # then score both with inject-wpt-block.mjs's diffComposedVsRef against
  //   # tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/css-ui/
  //
  // Flip the default to ON in the same commit that publishes arm B's numbers.
  const envSwitch = process.env.TITAN_SVG_PRERASTER;
  const enabled = opts.enabled === true || envSwitch === '1';
  if (!enabled) {
    log(`svg pre-raster OFF (default; set TITAN_SVG_PRERASTER=1 to engage) — `
      + `${srcs.length} vector(s) keep riding the wire and both natives will decline them by name`);
    return rasterized;
  }
  if (!opts.wptDir) {
    log(`svg pre-raster SKIPPED for ${srcs.length} vector(s): no --wpt-dir, so nothing can be resolved`);
    return rasterized;
  }
  const root = resolve(opts.wptDir);

  // Resolve + cache-check FIRST, so a fully-cached section never launches a
  // browser at all (the common case on a re-run of the same section).
  const work = [];
  for (const src of srcs) {
    const svgAbs = resolve(root, src);
    // Containment — the same check, for the same reason, as
    // resolveReplacedImageFile: a `..` chain must never let a fixture name a
    // file outside the corpus and get a raster of it written into a sandbox.
    if (svgAbs !== root && !svgAbs.startsWith(root + '/')) {
      log(`svg pre-raster DECLINED ${src}: resolves outside the corpus root`);
      continue;
    }
    if (!existsSync(svgAbs)) {
      log(`svg pre-raster DECLINED ${src}: no such vector on disk`);
      continue;
    }
    const rasterSrc = prerasterSrcFor(src);
    const pngAbs = resolve(root, rasterSrc);
    if (await rasterIsFresh(svgAbs, pngAbs, opts.force)) {
      // LOUD even on a cache hit: the stamp is about the raster path being
      // ENGAGED at all, not about work having been done this minute.
      log(`svg pre-raster CACHED  ${src} → ${rasterSrc}`);
      rasterized.set(src, rasterSrc);
      continue;
    }
    work.push({ src, svgAbs, rasterSrc, pngAbs });
  }
  if (work.length === 0) return rasterized;

  let browser = null;
  try {
    const { default: puppeteer } = await import('puppeteer');
    const launch = opts.launch ?? puppeteer.launch;
    browser = await launch.call(puppeteer, {
      headless: 'new',
      // The capture pipeline's own flag set (capture-browser-ref.mjs
      // BROWSER_LAUNCH_ARGS): CPU raster on both sides, no focus steal on
      // macOS, no first-run interstitial. Kept literal rather than imported so
      // this module does not drag the ref-capture module (and its puppeteer
      // top-level import) into every feeder run.
      args: [
        '--disable-gpu',
        '--disable-background-timer-throttling',
        '--disable-renderer-backgrounding',
        '--disable-backgrounding-occluded-windows',
        '--no-default-browser-check',
        '--no-first-run',
        '--disable-features=Translate,MediaRouter,OptimizationHints',
      ],
      protocolTimeout: 120_000,
      // LAUNCH timeout, raised from puppeteer's 30 s default. MEASURED on the
      // wave-40 device farm: with seven lanes' emulators, simulators and
      // browser-ref swarms live the host sat at load ~680, and the launch died
      // with "Timed out after 30000 ms while waiting for the WS endpoint URL"
      // — declining a whole section's vectors for a reason that had nothing to
      // do with any vector. A slow launch should cost seconds, not a section.
      timeout: 120_000,
    });
    const page = await browser.newPage();
    for (const w of work) {
      try {
        const bytes = await fs.readFile(w.svgAbs);
        const { width, height } = await rasterizeOne(page, bytes, w.pngAbs);
        rasterized.set(w.src, w.rasterSrc);
        // The LOUD STAMP. It names the stand-in AND its one size, because the
        // size is the lossy part of this hop (header: THE SCALE CONTRACT).
        log(`svg pre-raster ${w.src} → ${w.rasterSrc} at ${width}x${height} (natural size, 1x)`);
      } catch (err) {
        log(`svg pre-raster DECLINED ${w.src}: ${err.message} — the vector keeps riding the wire and the runtime will decline it by name`);
      }
    }
  } catch (err) {
    // A launch failure declines the WHOLE batch, loudly, and the run continues
    // exactly as it did before this hop existed.
    log(`svg pre-raster UNAVAILABLE (${err.message}) — ${work.length} vector(s) keep riding the wire`);
  } finally {
    if (browser) { try { await browser.close(); } catch { /* best effort */ } }
  }
  return rasterized;
}
