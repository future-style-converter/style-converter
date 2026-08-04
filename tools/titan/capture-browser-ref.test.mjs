#!/usr/bin/env node
//
// Unit tests for tools/titan/capture-browser-ref.mjs.
//
// We test the pure helpers (path resolution + cache key) here. The
// puppeteer-driven render path is exercised by the orchestrator's smoke
// run rather than per-PR unit tests so node --test stays headless.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { sep } from 'node:path';
// wave-25 CAL-RC1: the image-space frame is pure (Buffer in → Buffer out),
// so pngjs is all the fixture machinery these tests need — still headless.
import { PNG } from 'pngjs';

import {
  cachePathFor,
  resolveRefPath,
  browserRevFrom,
  legacyClaimPath,
  CANVAS_BG,
  CANVAS_REV,
  CANVAS_PAD_PX,
  REF_RENDER_WIDTH,
  REF_MIN_CANVAS_H,
  padPngBuffer,
  padColorFor,
  parseHexRgb,
  UNKNOWN_BROWSER_REV,
  canvasFrameCss,
  REF_FONT_STACK,
  REF_LINE_HEIGHT,
} from './capture-browser-ref.mjs';

// ── the corpus-v4 white-canvas contract ─────────────────────────────────────
//
// The ref canvas flipped from the dark #1A1A2E stage to WHITE at the
// corpus-v4 boundary (white WPT ink must vanish into the canvas on BOTH
// sides of the diff — see the module header). These pins hold the contract
// so a canvas change can never ship without (a) being deliberate and (b)
// bumping the cache revision segment that keeps stale refs out of diffs.

test('CANVAS_BG is the corpus-v4 white canvas', () => {
  assert.equal(CANVAS_BG, '#FFFFFF');
});

test('CANVAS_REV names the wave-30 html-pins cache revision', () => {
  // The ONE literal pin on the rev string — every other assertion in this
  // file interpolates CANVAS_REV, so a deliberate bump costs one line here
  // and a drifted one fails loudly.
  // 'white-black-ink-font-lh-imgpad' = the corpus-v4.1 typographic contract
  // (white canvas + spec-BLACK injected default ink + the harness Inter font
  // stack + the deterministic REF_LINE_HEIGHT pin) PLUS the wave-25 CAL-RC1
  // fourth leg: the 16px frame applied in IMAGE space instead of as
  // `:where(body){padding}`, so abspos/fixed overlays stop rendering 16px
  // off their own in-flow content. Every earlier rev (…-lh, …-font, white/,
  // and the un-segmented pre-v4 dark tree) is geometrically stale for
  // abspos-overlay refs and must never be mixed into a diff.
  // '-htmlpins' = the wave-30 A5 fifth leg: the three INHERITED pins moved to
  // `:where(html)`, so a ref declaring color/font-family/line-height at
  // :root/html is no longer clobbered by a specified value on <body>. Every
  // pre-htmlpins ref of such a page rasterised the WRONG ink and is stale.
  assert.equal(CANVAS_REV, 'white-black-ink-font-lh-imgpad-htmlpins');
});

test('REF_RENDER_WIDTH + REF_MIN_CANVAS_H reproduce the historical canvas', () => {
  // The image-space frame splits the old single 390×600 contract in two: the
  // page RENDERS at the inner box and the pad restores the outer one. These
  // two identities are the whole reason wrap points and `height: 100%`
  // children are byte-stable across the boundary — if either drifts, refs
  // silently re-wrap and every prose test moves.
  assert.equal(REF_RENDER_WIDTH + 2 * CANVAS_PAD_PX, 390, 'padded width must stay the 390 canvas');
  assert.equal(REF_MIN_CANVAS_H, 600, 'padded height floor must stay the historical 600');
  // The measurement floor the renderer uses is the outer floor minus the pad,
  // so a short ref still yields exactly 600 after framing.
  assert.equal((REF_MIN_CANVAS_H - 2 * CANVAS_PAD_PX) + 2 * CANVAS_PAD_PX, REF_MIN_CANVAS_H);
});

// ── wave-30 A5: the INHERITED pins live on :where(html), never on body ──────
//
// THE BUG THESE PIN AGAINST (measured, css/selectors/child-indexed-no-parent-
// ref.html): `:where()` zeroes SPECIFICITY, which only settles contests on
// the SAME element. One level down, CSS Cascade 5 §6.2 says a SPECIFIED value
// always beats an INHERITED one regardless of the specificity that produced
// it — so `:where(body){ color:#000 }` silently overrode every ref that
// declared `:root { color: green }` and let body inherit it. The ref
// rasterised BLACK where spec truth is GREEN, and our (correct) harness
// captures were scored as failures against that wrong target.
//
// Splitting the rules by INHERITANCE is the whole fix, so the split is what
// these tests hold. Structural, not cosmetic: fold the three declarations
// back onto :where(body) and the clobber returns with no other symptom.
// A live-browser confirmation of the cascade claim is out of scope for
// `node --test` (headless by contract, see the file header) — it was measured
// with puppeteer at implementation time and re-derivable from the rule text
// pinned here.

/** Pull one `:where(<sel>) { … }` declaration block out of the frame sheet.
 *  Returns null when the rule is absent, so a missing rule fails as a clear
 *  assertion rather than a TypeError on `.includes`. */
function frameRule(css, selector) {
  const rx = new RegExp(`:where\\(${selector.replace(/[()[\]{}*+?.\\^$|]/g, '\\$&')}\\)\\s*\\{([^}]*)\\}`);
  return rx.exec(css)?.[1] ?? null;
}

test('wave-30 A5: the three inherited pins sit on :where(html)', async () => {
  const css = await canvasFrameCss();
  const html = frameRule(css, 'html');
  assert.ok(html, ':where(html) rule missing from the canvas frame');
  // The corpus-v4.1 ink + font + rhythm trio, all INHERITED properties, all
  // on the ROOT so an author `:root`/`html` rule wins the cascade there and
  // body inherits the AUTHOR's value.
  assert.match(html, /color:\s*#000;/, 'ink pin left the root rule');
  assert.ok(html.includes(REF_FONT_STACK), 'font pin left the root rule');
  assert.ok(html.includes(`line-height: ${REF_LINE_HEIGHT};`), 'rhythm pin left the root rule');
});

test('wave-30 A5: :where(body) carries NO inherited text property', async () => {
  // This is the assertion the defect would have failed. `:where(html, body)`
  // is a different literal and is matched separately below, so this scopes to
  // the body-only rule.
  const css = await canvasFrameCss();
  const body = frameRule(css, 'body');
  assert.ok(body, ':where(body) rule missing from the canvas frame');
  assert.doesNotMatch(body, /(?<![-\w])color\s*:/, 'the ink pin came back on body — it clobbers :root refs');
  assert.doesNotMatch(body, /font-family\s*:/, 'the font pin came back on body — it clobbers :root refs');
  assert.doesNotMatch(body, /line-height\s*:/, 'the rhythm pin came back on body — it clobbers :root refs');
});

test('wave-30 A5: the NON-inherited body frame is untouched by the hoist', async () => {
  const css = await canvasFrameCss();
  const body = frameRule(css, 'body');
  // flow-root (margin-collapse containment), box-sizing + min-height
  // (viewport fill) are all NON-inherited and all specifically ABOUT the body
  // box — hoisting them would break the CAL-RC1 geometry contract, so the
  // hoist must have left them exactly where they were.
  assert.match(body, /display:\s*flow-root;/, 'body BFC lost');
  assert.match(body, /box-sizing:\s*border-box;/, 'body box-sizing lost');
  assert.match(body, /min-height:\s*100vh;/, 'body viewport fill lost');
  // margin/padding/background legitimately stay on BOTH (canvas propagation
  // reads the root's background then the body's — html.css UA behaviour).
  assert.ok(css.includes(':where(html, body) { margin: 0; padding: 0; background: '),
    'the shared html/body box+canvas rule must survive the hoist');
});

// ── wave-25 CAL-RC1: the image-space frame ──────────────────────────────────

test('padPngBuffer grows the canvas by 2×pad and translates content by +pad', () => {
  // 4×3 solid mid-grey with one distinguishable pixel: after framing, that
  // pixel must appear at (x+pad, y+pad) and NOWHERE else — an image-space
  // translation, not a resample.
  const src = new PNG({ width: 4, height: 3 });
  for (let i = 0; i < src.data.length; i += 4) {
    src.data[i] = 8; src.data[i + 1] = 9; src.data[i + 2] = 10; src.data[i + 3] = 255;
  }
  const markAt = ((1 * 4) + 2) * 4;                 // (x=2, y=1)
  src.data[markAt] = 200; src.data[markAt + 1] = 30; src.data[markAt + 2] = 40;
  const out = PNG.sync.read(padPngBuffer(PNG.sync.write(src), 2));
  assert.equal(out.width, 8);
  assert.equal(out.height, 7);
  const px = (x, y) => [...out.data.slice((y * out.width + x) * 4, (y * out.width + x) * 4 + 3)];
  assert.deepEqual(px(4, 3), [200, 30, 40], 'marker must land at (2+2, 1+2)');
  assert.deepEqual(px(2, 1), [8, 9, 10], 'the marker must not stay at its old coordinates');
});

test('padPngBuffer frames a uniform-ring image in its OWN canvas colour', () => {
  // Full-bleed refs (17 of the 302 cached; background-color-animation-in-body's
  // olive is the canonical one) paint edge-to-edge, and so do their harness
  // captures — a white frame there would MANUFACTURE a 32px divergence band.
  const src = new PNG({ width: 4, height: 4 });
  for (let i = 0; i < src.data.length; i += 4) {
    src.data[i] = 100; src.data[i + 1] = 100; src.data[i + 2] = 0; src.data[i + 3] = 255;
  }
  const out = PNG.sync.read(padPngBuffer(PNG.sync.write(src), 3));
  assert.deepEqual([...out.data.slice(0, 3)], [100, 100, 0], 'olive page → olive frame');
});

test('padPngBuffer falls back to CANVAS_BG when the border ring carries ink', () => {
  // css-values/attr-color-valid is the measured case: a green block anchored
  // at the ICB origin touches the LEFT edge while the right edge stays white.
  // The ring is non-uniform, so the frame must be the canvas colour — the
  // block then sits inset by the pad, exactly where the capture puts it.
  const src = new PNG({ width: 4, height: 4 });
  for (let y = 0; y < 4; y++) {
    for (let x = 0; x < 4; x++) {
      const i = (y * 4 + x) * 4;
      const green = x < 2;
      src.data[i] = green ? 0 : 255;
      src.data[i + 1] = green ? 128 : 255;
      src.data[i + 2] = green ? 0 : 255;
      src.data[i + 3] = 255;
    }
  }
  const out = PNG.sync.read(padPngBuffer(PNG.sync.write(src), 1));
  assert.deepEqual([...out.data.slice(0, 3)], [255, 255, 255], 'mixed ring → CANVAS_BG frame');
});

test('padColorFor reads the ring, not the interior', () => {
  // Guards the sampling rule itself: a white-bordered page with a coloured
  // middle is an ordinary ref and must be framed white.
  const src = new PNG({ width: 5, height: 5 });
  for (let i = 0; i < src.data.length; i += 4) {
    src.data[i] = 255; src.data[i + 1] = 255; src.data[i + 2] = 255; src.data[i + 3] = 255;
  }
  const mid = ((2 * 5) + 2) * 4;
  src.data[mid] = 0; src.data[mid + 1] = 0; src.data[mid + 2] = 255;
  assert.deepEqual(padColorFor(src), { r: 255, g: 255, b: 255, a: 255 });
});

test('padPngBuffer is a no-op at pad 0 and parseHexRgb rejects junk', () => {
  const src = new PNG({ width: 2, height: 2 });
  const buf = PNG.sync.write(src);
  assert.equal(padPngBuffer(buf, 0), buf, 'pad 0 must not re-encode');
  // No silent fallthrough: a malformed canvas colour is a contract bug.
  assert.throws(() => parseHexRgb('white'), /#RRGGBB/);
  assert.deepEqual(parseHexRgb('#FFFFFF'), { r: 255, g: 255, b: 255, a: 255 });
});

// ── cachePathFor ────────────────────────────────────────────────────────────

test('cachePathFor produces canvas-rev/section/stem layout under WPT SHA', () => {
  const p = cachePathFor('abc123', 'css/css-color/a98rgb-001.html');
  // Use sep so the assertion holds on Windows too even though dev is macOS.
  // The CANVAS_REV segment sits between the SHA and the section so the
  // pre-v4 dark refs (refs/<sha>/<section>/) are never picked up.
  assert.match(p, new RegExp(`tools\\${sep}wpt\\${sep}refs\\${sep}abc123\\${sep}${CANVAS_REV}\\${sep}css-color\\${sep}a98rgb-001\\.png$`));
});

test('cachePathFor preserves SHA verbatim (no truncation)', () => {
  const sha = '9b5435e55e0b54a6cd09c1c563861eb3c999cef1';
  const p = cachePathFor(sha, 'css/css-grid/grid-001.html');
  assert.ok(p.includes(sha), `expected SHA in path; got ${p}`);
});

test('cachePathFor encodes subdirs into the stem for nested test paths', () => {
  // Some WPT tests live one level deeper, e.g. css/css-flexbox/abspos/foo.html
  // — section is still the second segment ("css-flexbox"), and the stem is
  // the SUBDIR-ENCODED fixtureStem (`abspos__foo`), NOT the bare basename.
  // wave-21 collision fix: with a bare basename, two nested tests with equal
  // basenames (css-break/flexbox vs css-break/grid monolithic-overflow
  // family) shared ONE cache slot — the first render's PNG silently served
  // as the second test's reference.
  const p = cachePathFor('sha', 'css/css-flexbox/abspos/abspos-autopos-htb-ltr.html');
  assert.match(p, new RegExp(`refs\\${sep}sha\\${sep}${CANVAS_REV}\\${sep}css-flexbox\\${sep}abspos__abspos-autopos-htb-ltr\\.png$`));
});

test('cachePathFor gives colliding-basename nested tests DISTINCT cache slots', () => {
  // The exact wave-21 skeptic pair: same section, same basename, different
  // subdir — these used to flatten to one identical cache path.
  const a = cachePathFor('sha', 'css/css-break/flexbox/monolithic-overflow-001.tentative.html');
  const b = cachePathFor('sha', 'css/css-break/grid/monolithic-overflow-001.tentative.html');
  assert.notEqual(a, b, 'nested tests with equal basenames must not share a ref cache slot');
  assert.match(a, /flexbox__monolithic-overflow-001\.tentative\.png$/);
  assert.match(b, /grid__monolithic-overflow-001\.tentative\.png$/);
});

test('cachePathFor for tests directly under /css/ falls back to "css" section', () => {
  // Defensive: very few tests live directly under /css/, but the spec
  // section helper in the bucketer hands them "css".
  const p = cachePathFor('sha', 'css/orphan.html');
  assert.match(p, new RegExp(`refs.sha.${CANVAS_REV}.css.orphan\\.png$`));
});

// ── wave-24 A-RC1: the browser-rev cache key ────────────────────────────────
//
// The cache used to key on OUR inputs only (WPT SHA, canvas contract, test
// stem). The Chromium build that rasterises the ref was unkeyed, so a
// browser upgrade left every cached PNG a permanent HIT and froze that
// browser's paint behaviour — bug or fix — into the acceptance signal. These
// pins hold the derivation (what counts as "a different browser") and the
// two-tree layout the lazy migration depends on.

test('browserRevFrom slugs a puppeteer product string into a path segment', () => {
  // The exact shape puppeteer's browser.version() returns on the bundled
  // build this repo pins today.
  assert.equal(browserRevFrom('Chrome/150.0.7871.24'), 'chrome-150.0.7871.24');
});

test('browserRevFrom collapses HeadlessChrome onto chrome', () => {
  // Same binary, two launch modes — a headless/headful flip must NOT
  // invalidate an otherwise identical cache, or every local headful debug
  // session would re-render the corpus.
  assert.equal(browserRevFrom('HeadlessChrome/150.0.7871.24'), 'chrome-150.0.7871.24');
  assert.equal(browserRevFrom('HeadlessChrome/150.0.7871.24'), browserRevFrom('Chrome/150.0.7871.24'));
});

test('browserRevFrom keeps the FULL build number, not just the milestone', () => {
  // Chromium ships paint fixes in patch releases — the precise class of
  // change this key exists to catch. Milestone-only granularity would let
  // a patch bump keep serving refs from the pre-fix rasteriser.
  assert.notEqual(browserRevFrom('Chrome/150.0.7871.24'), browserRevFrom('Chrome/150.0.7871.25'));
  assert.notEqual(browserRevFrom('Chrome/150.0.7871.24'), browserRevFrom('Chrome/151.0.7900.1'));
});

test('browserRevFrom hardens the slug against path traversal', () => {
  // The slug becomes a filesystem path segment; a '/' or '..' in a UA
  // string must never escape the refs root.
  const rev = browserRevFrom('Chrome/../../etc/passwd');
  assert.doesNotMatch(rev, /[/\\]/, 'no path separators may survive');
  assert.ok(!rev.includes('..'), `traversal survived: ${rev}`);
});

test('browserRevFrom buckets an unusable version explicitly, never silently', () => {
  // A blank/absent version is a real condition (stubbed browser, CDP
  // hiccup). It gets its OWN bucket rather than sharing a slot with a
  // real build — no silent fallthrough.
  assert.equal(browserRevFrom(''), UNKNOWN_BROWSER_REV);
  assert.equal(browserRevFrom(null), UNKNOWN_BROWSER_REV);
  assert.equal(browserRevFrom(undefined), UNKNOWN_BROWSER_REV);
  assert.notEqual(UNKNOWN_BROWSER_REV, browserRevFrom('Chrome/150.0.7871.24'));
});

test('browserRevFrom tolerates a product string with no build number', () => {
  assert.equal(browserRevFrom('Chromium'), 'chromium');
});

test('cachePathFor inserts the browser rev BESIDE the canvas rev', () => {
  const rev = browserRevFrom('Chrome/150.0.7871.24');
  const p = cachePathFor('abc123', 'css/css-gaps/flex/flex-gap-decorations-001.html', rev);
  assert.match(p, new RegExp(
    `refs\\${sep}abc123\\${sep}${CANVAS_REV}\\${sep}chrome-150\\.0\\.7871\\.24` +
    `\\${sep}css-gaps\\${sep}flex__flex-gap-decorations-001\\.png$`));
});

test('cachePathFor WITHOUT a browser rev keeps the historical scorer path', () => {
  // run-titan.sh / section-runner.sh hand inject-wpt-block.mjs the
  // version-less `.../refs/$WPT_REF/white-black-ink-font-lh` root. The
  // 2-arg call must stay byte-identical to the pre-wave-24 layout or every
  // scored pair would lose its reference.
  const p = cachePathFor('abc123', 'css/css-gaps/flex/flex-gap-decorations-001.html');
  assert.match(p, new RegExp(
    `refs\\${sep}abc123\\${sep}${CANVAS_REV}\\${sep}css-gaps\\${sep}flex__flex-gap-decorations-001\\.png$`));
  // …and the two must be DIFFERENT paths, or the segment does nothing.
  assert.notEqual(p, cachePathFor('abc123', 'css/css-gaps/flex/flex-gap-decorations-001.html', 'chrome-150.0.7871.24'));
});

test('a browser upgrade changes the cache path (the whole point of the key)', () => {
  const a = cachePathFor('sha', 'css/css-color/a98rgb-001.html', browserRevFrom('Chrome/150.0.7871.24'));
  const b = cachePathFor('sha', 'css/css-color/a98rgb-001.html', browserRevFrom('Chrome/151.0.7900.1'));
  assert.notEqual(a, b, 'an upgrade must MISS the cache, not silently reuse the old rasteriser');
});

test('legacyClaimPath sits at the canvas-rev root, outside every section dir', () => {
  // The claim file credits the version-less tree to one browser rev (the
  // lazy-migration state machine). It must never live inside a section dir
  // where a glob for refs could pick it up as a PNG.
  const p = legacyClaimPath('abc123');
  assert.match(p, new RegExp(`refs\\${sep}abc123\\${sep}${CANVAS_REV}\\${sep}\\.legacy-browser-rev$`));
  assert.doesNotMatch(p, /\.png$/);
});

// ── resolveRefPath ──────────────────────────────────────────────────────────
//
// Hits the filesystem (reads tools/wpt/css/css-color/a98rgb-001.html). We
// only run it when the corpus is present; in a fresh worktree with no WPT
// checkout we silently skip rather than failing.

import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const __dirname  = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT  = resolve(__dirname, '..', '..');
const SAMPLE_TEST = 'css/css-color/a98rgb-001.html';
const SAMPLE_TEST_ABS = join(REPO_ROOT, 'tools', 'wpt', SAMPLE_TEST);

test('resolveRefPath returns the absolute ref path for a real test', { skip: !existsSync(SAMPLE_TEST_ABS) }, async () => {
  const ref = await resolveRefPath(SAMPLE_TEST);
  assert.ok(ref.endsWith('greensquare-ref.html'), `unexpected ref path: ${ref}`);
  assert.ok(ref.startsWith('/'), 'ref path should be absolute');
});

// ── wave-21 6b: mismatch-only reftests are an explicit skip ─────────────────
//
// css/css-text-decor/text-combine-emphasis.html carries ONLY a
// rel="mismatch" link (pass = "does NOT equal the ref"), which a
// match-asserting capture cannot represent. resolveRefPath must throw an
// error carrying the machine-readable `skipReason` marker so captureRefs
// reports SKIP (ok:true, skipped:true) instead of FAIL — the wave-21 gate
// logged this exact test as `FAIL … no rel="match" link`, which then read
// as capture breakage rather than a structural skip.

const MISMATCH_ONLY_TEST = 'css/css-text-decor/text-combine-emphasis.html';
const MISMATCH_ONLY_ABS = join(REPO_ROOT, 'tools', 'wpt', MISMATCH_ONLY_TEST);

test('resolveRefPath marks mismatch-only reftests with skipReason', { skip: !existsSync(MISMATCH_ONLY_ABS) }, async () => {
  await assert.rejects(
    resolveRefPath(MISMATCH_ONLY_TEST),
    (err) => {
      // The marker drives the SKIP branch in captureRefs.
      assert.equal(err.skipReason, 'mismatch-only-reftest');
      // Message must say WHY, for the humans reading browser-ref.log.
      assert.match(err.message, /mismatch-only reftest/);
      return true;
    },
  );
});

test('resolveRefPath keeps the plain error for tests with NO ref link at all', { skip: !existsSync(SAMPLE_TEST_ABS) }, async () => {
  // A ref page itself has neither rel="match" nor rel="mismatch" — the
  // error must NOT carry skipReason (that would silently skip real input
  // mistakes). a98rgb-001's own ref file is a handy no-link fixture.
  const refAbs = await resolveRefPath(SAMPLE_TEST);
  const refRel = refAbs.slice(join(REPO_ROOT, 'tools', 'wpt').length + 1);
  await assert.rejects(
    resolveRefPath(refRel),
    (err) => {
      assert.equal(err.skipReason, undefined);
      assert.match(err.message, /no rel="match" link/);
      return true;
    },
  );
});
