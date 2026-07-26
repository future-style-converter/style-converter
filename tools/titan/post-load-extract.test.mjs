// tools/titan/post-load-extract.test.mjs — unit pins for the wave-16
// POST-LOAD extraction mode (post-load-extract.mjs).
//
// Coverage map (mirrors the module's contract sections):
//   1. the enumerated computed-property set (exact list pin);
//   2. the top-layer decline boundary (TOP_LAYER_API_RX / postLoadDecline);
//   3. wall-tag activation (hasWallTag against EXTRACTION_WALL_TAGS);
//   4. traversal identity — flattenStaticPaths / staticPathsForHtml must
//      reproduce buildComponents' id paths (head-only skip, inline-merge
//      filter, depth cap);
//   5. element-mapping cross-check (mappingMismatch);
//   6. settle-stability (snapshotsStable);
//   7. merge-override semantics (componentAtPath / overlayComputedOnComponent
//      / mergePostLoadIntoFixture);
//   8. the PROVING SET — the css-position wall fixtures' baked post-load
//      values (skip-guarded: fixtures/wpt is a generated artifact, so these
//      pins only run on a machine that has run the extraction);
//   9. an optional LIVE end-to-end run (browser + corpus), env-gated so the
//      default `node --test` stays hermetic and fast.
//
// The gate-interplay pins (applyNaScoreGate × postLoadExtracted) live in
// inject-wpt-block.test.mjs next to the rest of the scoring-gate suite.

import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  POST_LOAD_COMPUTED_PROPERTIES, WRITE_RULES, SHORTHAND_CONFLICTS,
  TOP_LAYER_API_RX, postLoadDecline, hasWallTag,
  flattenStaticPaths, staticPathsForHtml,
  mappingMismatch, snapshotsStable,
  componentAtPath, overlayComputedOnComponent, mergePostLoadIntoFixture,
} from './post-load-extract.mjs';
import { extractBodyTreeNested, buildComponents, parseCss, stripComments, extractInlineStyle, collectStyledTags } from './extract-fixture.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');
// The generated-fixture root the proving-set pins read (gitignored artifact
// — pins skip when absent, see section 8's banner).
const FIXTURES = join(REPO_ROOT, 'fixtures', 'wpt', 'css-position');

// ── 1. The enumerated computed-property set ─────────────────────────────────

test('post-load: POST_LOAD_COMPUTED_PROPERTIES is the exact deliberate set', () => {
  // Pin the exact enumeration — adding or removing a computed property
  // changes what every post-load fixture bakes and must break a test first
  // (same convention as the EXTRACTION_WALL_TAGS pin).
  assert.deepEqual(POST_LOAD_COMPUTED_PROPERTIES, [
    'position',
    'top', 'right', 'bottom', 'left',
    'width', 'height', 'box-sizing',
    'margin-top', 'margin-right', 'margin-bottom', 'margin-left',
    'padding-top', 'padding-right', 'padding-bottom', 'padding-left',
    'border-top-width', 'border-right-width', 'border-bottom-width', 'border-left-width',
    'border-top-style', 'border-right-style', 'border-bottom-style', 'border-left-style',
    'border-top-color', 'border-right-color', 'border-bottom-color', 'border-left-color',
    'background-color',
    'transform',
    'align-self',
    'display', 'overflow-x', 'overflow-y', 'z-index',
  ]);
});

test('post-load: WRITE_RULES delete-not-write defaults are pinned', () => {
  // 'auto' insets / z-index and 'none' transform carry no declaration;
  // width/height must be concrete px. Silently widening these would bloat
  // every fixture (or bake keywords the converter treats differently).
  assert.deepEqual(Object.keys(WRITE_RULES).sort(),
    ['align-self', 'bottom', 'height', 'left', 'right', 'top', 'transform', 'width', 'z-index']);
  assert.equal(WRITE_RULES.top.deleteWhen, 'auto');
  assert.equal(WRITE_RULES.transform.deleteWhen, 'none');
  assert.equal(WRITE_RULES.width.requirePx, true);
  // RC-A5a: align-self's initial 'auto' carries no declaration (css-align-3
  // §6.1) — delete-not-write, so a stale pre-mutation keyword cannot linger.
  assert.equal(WRITE_RULES['align-self'].deleteWhen, 'auto');
});

// ── 2. Top-layer decline boundary ───────────────────────────────────────────

test('post-load: top-layer API calls decline (the overlay family)', () => {
  // The measured overlay-transition-backdrop shape: whole signal lives on
  // ::backdrop after showPopover()/hidePopover() — per-element computed
  // snapshots cannot represent it.
  assert.equal(postLoadDecline('<script>foo.showPopover(); foo.hidePopover();</script>'),
    'top-layer-undeliverable');
  // <dialog>.showModal() and fullscreen promotion are the same top layer.
  assert.equal(postLoadDecline('<script>dlg.showModal()</script>'), 'top-layer-undeliverable');
  assert.equal(postLoadDecline('<script>el.requestFullscreen ()</script>'), 'top-layer-undeliverable');
});

test('post-load: style-mutation scripts do NOT decline', () => {
  // The deliverable family — style/property mutation with no top-layer API.
  assert.equal(postLoadDecline('<script>onload = () => { el.style.left = "100px"; }</script>'), null);
  // Property names that merely CONTAIN an API substring must not fire.
  assert.equal(postLoadDecline('<div popover id=foo></div>'), null);
});

test('post-load: TOP_LAYER_API_RX matches call syntax only', () => {
  // A prose mention (no call parens) is not a promotion — regex requires
  // the `.name(` call shape.
  assert.equal(TOP_LAYER_API_RX.test('showPopover is an API'), false);
  assert.equal(TOP_LAYER_API_RX.test('x.togglePopover()'), true);
});

// ── 3. Wall-tag activation ──────────────────────────────────────────────────

test('post-load: hasWallTag fires on exactly the extraction-wall tags', () => {
  // Activation must track the score gate's wall set — both script tags fire,
  // capability tags and empty/missing tag lists do not.
  assert.equal(hasWallTag(['requires-script-mutation']), true);
  assert.equal(hasWallTag(['requires-containment', 'requires-script-driven-scroll']), true);
  assert.equal(hasWallTag(['requires-float-layout']), false);
  assert.equal(hasWallTag([]), false);
  assert.equal(hasWallTag(undefined), false);
});

// ── 4. Traversal identity ───────────────────────────────────────────────────

// The hypothetical-dynamic-change family's markup shape (nested fixed pair).
const NESTED_HTML = `
<style>.a { position: fixed; } </style>
<div class="a"><div class="b"></div></div>
<script>onload = () => {};</script>`;

test('post-load: flattenStaticPaths mirrors buildComponents id paths', () => {
  // The path list must be exactly the `__N[__M…]` id suffixes buildComponents
  // assigns — verified by building both from the SAME tree and comparing.
  const cleaned = stripComments(NESTED_HTML);
  const rules = parseCss(extractInlineStyle(cleaned));
  const mergeCtx = { styledTags: collectStyledTags(rules) };
  const tree = extractBodyTreeNested(cleaned, 5, mergeCtx);
  const paths = flattenStaticPaths(tree);
  // Two nodes: the outer div at [0], its child at [0,0]; <style>/<script>
  // are head-only and never enter the walk.
  assert.deepEqual(paths, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'div' },
  ]);
  // Cross-check against the component ids the fixture builder emits.
  const built = buildComponents(cleaned, rules, 'stem');
  assert.deepEqual(Object.keys(built.components), ['stem__0']);
  assert.deepEqual(Object.keys(built.components.stem__0.children), ['stem__0__0']);
});

test('post-load: staticPathsForHtml applies the inline-merge filter', async () => {
  // The change-insets shape: instruction <p> with a mergeable <strong> —
  // the strong is absorbed into the p's _text and must NOT get a path
  // (the browser walk skips it symmetrically).
  const html = `
<p>Test passes if there is a filled green square and <strong>no red</strong>.</p>
<div style="width:100px"></div>`;
  const { paths } = await staticPathsForHtml(html, join(REPO_ROOT, 'nonexistent.html'));
  assert.deepEqual(paths, [
    { path: [0], tag: 'p' },
    { path: [1], tag: 'div' },
  ]);
});

test('post-load: staticPathsForHtml keeps a rule-targeted inline tag as a component', async () => {
  // The styledTags merge guard: `strong { color: red }` makes the <strong>
  // a styled subject — it keeps its component path on BOTH sides.
  const html = `
<style>strong { color: red; }</style>
<p>text <strong>subject</strong></p>`;
  const { paths } = await staticPathsForHtml(html, join(REPO_ROOT, 'nonexistent.html'));
  assert.deepEqual(paths, [
    { path: [0], tag: 'p' },
    { path: [0, 0], tag: 'strong' },
  ]);
});

// ── 5. Element-mapping cross-check ──────────────────────────────────────────

const STATIC_PATHS = [
  { path: [0], tag: 'div' },
  { path: [0, 0], tag: 'div' },
];

test('post-load: mappingMismatch passes on aligned walks', () => {
  // Identical path/tag sequences ⇒ overlay is safe.
  assert.equal(mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'div' },
  ]), null);
});

test('post-load: mappingMismatch catches script-inserted elements', () => {
  // The containing-block-change-button shape: appendChild adds a child the
  // static tree never saw — count drift must bail.
  const m = mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'div' },
    { path: [0, 1], tag: 'div' },
  ]);
  assert.match(m, /element count: static 2 vs browser 3/);
});

test('post-load: mappingMismatch catches tag drift at a slot', () => {
  // Same shape, different element kind — identity check must bail.
  const m = mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [0, 0], tag: 'span' },
  ]);
  assert.match(m, /tag@0\.0: static <div> vs browser <span>/);
});

test('post-load: mappingMismatch catches path drift', () => {
  // Same count, different structure (sibling instead of child).
  const m = mappingMismatch(STATIC_PATHS, [
    { path: [0], tag: 'div' },
    { path: [1], tag: 'div' },
  ]);
  assert.match(m, /path\[1\]: static 0\.0 vs browser 1/);
});

// ── 6. Settle-stability ─────────────────────────────────────────────────────

const SNAP = {
  records: [{ path: [0], tag: 'div', styles: { left: '10px' },
              rect: { x: 10, y: 0, width: 100, height: 100 },
              scrollTop: 0, scrollLeft: 0 }],
  docScrollTop: 0, docScrollLeft: 0,
};

test('post-load: snapshotsStable accepts identical snapshots', () => {
  // Deep-equal via deterministic serialization — same walker, same order.
  assert.equal(snapshotsStable(SNAP, JSON.parse(JSON.stringify(SNAP))), true);
});

test('post-load: snapshotsStable bails on any computed-style drift', () => {
  // A running transition / rAF loop moves a value between the two 100ms
  // samples — the mode must fall back to the static path.
  const b = JSON.parse(JSON.stringify(SNAP));
  b.records[0].styles.left = '11px';
  assert.equal(snapshotsStable(SNAP, b), false);
});

test('post-load: snapshotsStable bails on rect drift too', () => {
  // Geometry can move without any enumerated style changing (e.g. an
  // animating ancestor) — the rect cross-check catches that class.
  const b = JSON.parse(JSON.stringify(SNAP));
  b.records[0].rect.y = 5;
  assert.equal(snapshotsStable(SNAP, b), false);
});

// ── 7. Merge-override semantics ─────────────────────────────────────────────

/** A minimal fixture mirroring the hypothetical-dynamic-change-001 static
 *  shape (pre-mutation left:0, shorthand background) for merge pins. */
function makeFixture() {
  return {
    _wpt: { test: 't.html', lossy: false, lossyReasons: [] },
    components: {
      stem__0: {
        properties: { position: 'fixed', left: '0', top: '0',
                      background: 'red', 'box-sizing': 'border-box' },
        _text: 'own text stays',
        children: {
          stem__0__0: { id: 'stem__0__0', properties: { position: 'fixed' } },
        },
      },
    },
  };
}

test('post-load: componentAtPath resolves nested children ids', () => {
  const fx = makeFixture();
  // Top level: path [0] → stem__0; nested: [0,0] → children map stem__0__0.
  assert.equal(componentAtPath(fx, 'stem', [0]), fx.components.stem__0);
  assert.equal(componentAtPath(fx, 'stem', [0, 0]), fx.components.stem__0.children.stem__0__0);
  // Absent paths resolve null (mergePostLoadIntoFixture turns that into a
  // LOUD error — pinned below).
  assert.equal(componentAtPath(fx, 'stem', [3]), null);
  assert.equal(componentAtPath(fx, 'stem', [0, 7]), null);
});

test('post-load: overlay overrides statics, strips shorthands, pins the basis', () => {
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  overlayComputedOnComponent(cmp, {
    position: 'fixed', left: '100px', top: '0px', right: '190px', bottom: 'auto',
    width: '100px', height: '100px', 'box-sizing': 'content-box',
    'background-color': 'rgb(255, 0, 0)', transform: 'none',
    display: 'block', 'overflow-x': 'visible', 'overflow-y': 'visible', 'z-index': 'auto',
  });
  const p = cmp.properties;
  // The mutation landed: computed px override the stale static '0'.
  assert.equal(p.left, '100px');
  // Conflicting shorthand stripped so it can't outrank the baked longhand.
  assert.equal(p.background, undefined);
  assert.equal(p['background-color'], 'rgb(255, 0, 0)');
  // delete-not-write defaults: auto/none carry no declaration.
  assert.equal(p['z-index'], undefined);
  assert.equal(p.transform, undefined);
  // Over-constraint drop (CSS2 §10.3.7): left+width kept ⇒ right dropped.
  assert.equal(p.right, undefined);
  // The COMPUTED basis overrides the stale static 'border-box' — gCS
  // width/height are expressed in the element's own basis, so the baked
  // basis must be the browser's, not the author's leftover.
  assert.equal(p['box-sizing'], 'content-box');
  // Static-only content is untouched by the overlay.
  assert.equal(cmp._text, 'own text stays');
});

test('post-load: overlay keeps the trailing inset when the leading one is auto', () => {
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  // Right/bottom-anchored box: left/top auto ⇒ deleted, right/bottom kept —
  // they are the real anchors, not over-constraint redundancy.
  overlayComputedOnComponent(cmp, {
    position: 'absolute', left: 'auto', top: 'auto', right: '20px', bottom: '30px',
    width: '50px', height: '50px', 'background-color': 'rgb(0, 128, 0)',
    transform: 'none', display: 'block', 'overflow-x': 'visible',
    'overflow-y': 'visible', 'z-index': 'auto',
  });
  assert.equal(cmp.properties.left, undefined);   // stale static '0' removed
  assert.equal(cmp.properties.right, '20px');
  assert.equal(cmp.properties.bottom, '30px');
});

test('post-load: stale align-self is displaced by the computed keyword (dynamic-align-self-001 shape)', () => {
  // RC-A5a — the exact css-flexbox/abspos/dynamic-align-self-001 shape: the
  // abspos flex child statically declares `align-self: end`, the script flips
  // it to 'start' after load. wave18-final baked the used insets (top/left
  // 0px) but left the stale 'end' underneath — a runtime that gives
  // self-alignment priority over the baked insets rendered the PRE-mutation
  // state (android-ref 0.9416 vs web-ref 0.999 on one fixture). The overlay
  // must bake the computed post-mutation keyword over the stale static one.
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  cmp.properties = { display: 'flex', position: 'absolute', width: '100px',
                     height: '100px', 'background-color': 'green',
                     'align-self': 'end' };
  overlayComputedOnComponent(cmp, {
    position: 'absolute', top: '0px', left: '0px', right: 'auto', bottom: 'auto',
    width: '100px', height: '100px', 'box-sizing': 'content-box',
    'background-color': 'rgb(0, 128, 0)', transform: 'none',
    'align-self': 'start',
    display: 'flex', 'overflow-x': 'visible', 'overflow-y': 'visible', 'z-index': 'auto',
  });
  // The post-script alignment displaced the stale pre-mutation keyword.
  assert.equal(cmp.properties['align-self'], 'start');
  // The used insets are baked alongside (top+height ⇒ bottom dropped, §10.6.4).
  assert.equal(cmp.properties.top, '0px');
  assert.equal(cmp.properties.left, '0px');
  assert.equal(cmp.properties.bottom, undefined);
});

test('post-load: computed align-self auto deletes a stale static keyword', () => {
  // The delete-not-write arm: a script that RESETS alignment to the initial
  // 'auto' must remove the stale declaration, not bake the keyword 'auto'.
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  cmp.properties['align-self'] = 'end';
  overlayComputedOnComponent(cmp, { 'align-self': 'auto' });
  assert.equal(cmp.properties['align-self'], undefined);
});

test('post-load: non-px width/height are delete-not-write', () => {
  const fx = makeFixture();
  const cmp = fx.components.stem__0;
  cmp.properties.width = '100px'; // stale static size
  // display:none boxes report width/height 'auto' — no concrete used value.
  overlayComputedOnComponent(cmp, { width: 'auto', height: 'auto', display: 'none' });
  assert.equal(cmp.properties.width, undefined);
  assert.equal(cmp.properties.display, 'none');
});

test('post-load: mergePostLoadIntoFixture stamps _wpt and counts overlays', () => {
  const fx = makeFixture();
  const overlaid = mergePostLoadIntoFixture(fx, 'stem', [
    { path: [0], tag: 'div', styles: { left: '100px' } },
    { path: [0, 0], tag: 'div', styles: { left: '100px' } },
  ]);
  assert.equal(overlaid, 2);
  // The delivery stamp the score gate consults (via the combined keyMap).
  assert.equal(fx._wpt.postLoadExtracted, true);
  assert.equal(fx.components.stem__0.children.stem__0__0.properties.left, '100px');
});

test('post-load: mergePostLoadIntoFixture fails LOUDLY on an unmapped path', () => {
  // A merge without a prior mappingMismatch pass is a programming error —
  // silent under-overlaying is exactly the kind of quiet lie this pipeline
  // bans (no silent fallthroughs).
  const fx = makeFixture();
  assert.throws(
    () => mergePostLoadIntoFixture(fx, 'stem', [{ path: [9], tag: 'div', styles: {} }]),
    /no component at path 9/);
});

test('post-load: SHORTHAND_CONFLICTS covers every family the overlay writes', () => {
  // Each longhand family baked by the overlay must have its displacing
  // shorthands listed, or a stale static shorthand could win in a runtime
  // cascade. Exact-list pin (reviewed change, not drift).
  assert.deepEqual([...SHORTHAND_CONFLICTS].sort(), [
    'background', 'border', 'border-bottom', 'border-color', 'border-left',
    'border-right', 'border-style', 'border-top', 'border-width',
    'inset', 'margin', 'overflow', 'padding',
  ]);
});

// ── 8. The proving set — baked css-position wall fixtures ───────────────────
//
// fixtures/wpt is a GENERATED artifact (gitignored): these pins verify the
// values the post-load run actually baked, and skip on checkouts that have
// not run `node tools/titan/post-load-extract.mjs <the css-position list>`.
// Each pin states the test's expected FINAL state per its mutation script.

/** Read a proving-set fixture, or null when not present/not post-loaded. */
function provingFixture(stem) {
  const p = join(FIXTURES, `${stem}.json`);
  if (!existsSync(p)) return null;
  const fx = JSON.parse(readFileSync(p, 'utf8'));
  return fx._wpt?.postLoadExtracted === true ? fx : null;
}

const hyp1 = provingFixture('hypothetical-dynamic-change-001');
test('proving: hypothetical-dynamic-change-001 → Left 100px, green box', { skip: !hyp1 }, () => {
  // Script: onload sets ancestor.style.left = "100px"; the auto-positioned
  // fixed child follows its hypothetical static position to x=100.
  const anc = hyp1.components['hypothetical-dynamic-change-001__0'];
  assert.equal(anc.properties.position, 'fixed');
  assert.equal(anc.properties.left, '100px');           // the baked mutation
  assert.equal(anc.properties.top, '0px');
  assert.equal(anc.properties['background-color'], 'rgb(255, 0, 0)'); // red under…
  const child = anc.children['hypothetical-dynamic-change-001__0__0'];
  assert.equal(child.properties.left, '100px');         // …the green cover
  assert.equal(child.properties.top, '0px');
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
  // Over-constraint dropped + basis pinned on every overlaid component.
  assert.equal(anc.properties.right, undefined);
  assert.equal(anc.properties['box-sizing'], 'content-box');
});

const hyp2 = provingFixture('hypothetical-dynamic-change-002');
test('proving: hypothetical-dynamic-change-002 → child follows abspos ancestor to x=100', { skip: !hyp2 }, () => {
  // Absolute ancestor: its containing block is the ICB, so the injected
  // canvas pad does not shift it — child's static position = 100 exactly.
  const anc = hyp2.components['hypothetical-dynamic-change-002__0'];
  assert.equal(anc.properties.left, '100px');
  const child = anc.children['hypothetical-dynamic-change-002__0__0'];
  assert.equal(child.properties.position, 'fixed');
  assert.equal(child.properties.left, '100px');
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
});

const hyp3 = provingFixture('hypothetical-dynamic-change-003');
test('proving: hypothetical-dynamic-change-003 → relative ancestor: 100px offset baked, +16 canvas pad on the child', { skip: !hyp3 }, () => {
  // Relative ancestor: `left` stays the RELATIVE offset (100px) — but the
  // ancestor is IN-FLOW, so the pipeline's injected 16px body pad (the
  // canvas contract every capture shares — capture-browser-ref.renderOne)
  // shifts its static position to x=16, and the fixed child's resolved
  // hypothetical static position is 16+100 = 116px. This is the browser
  // TRUTH under the canvas contract (the static path's runtime layout
  // produces the same in-flow shift) — pinned so a canvas-contract change
  // that moves it breaks a test first.
  const anc = hyp3.components['hypothetical-dynamic-change-003__0'];
  assert.equal(anc.properties.position, 'relative');
  assert.equal(anc.properties.left, '100px');
  const child = anc.children['hypothetical-dynamic-change-003__0__0'];
  assert.equal(child.properties.position, 'fixed');
  assert.equal(child.properties.left, '116px');
  assert.equal(child.properties['background-color'], 'rgb(0, 128, 0)');
});

const insets = provingFixture('change-insets-inside-strict-containment-nested');
test('proving: change-insets → f1 left 0, f2 left 50px, preamble text intact', { skip: !insets }, () => {
  // Script: f1.style.left = "0"; f2.style.left = "50px" — two 50×100 green
  // strips composing the ref's filled green 100px square.
  const c = insets.components;
  const f1 = c['change-insets-inside-strict-containment-nested__1']
    .children['change-insets-inside-strict-containment-nested__1__0'];
  assert.equal(f1.properties.left, '0px');
  assert.equal(f1.properties.width, '50px');
  assert.equal(f1.properties.height, '100px');
  assert.equal(f1.properties['background-color'], 'rgb(0, 128, 0)');
  const f2 = f1.children['change-insets-inside-strict-containment-nested__1__0__0']
    .children['change-insets-inside-strict-containment-nested__1__0__0__0'];
  assert.equal(f2.properties.left, '50px');
  assert.equal(f2.properties['background-color'], 'rgb(0, 128, 0)');
  // Static-only content kept the static path: the instruction paragraph's
  // merged _text survives the overlay byte-identically.
  assert.equal(c['change-insets-inside-strict-containment-nested__0']._text,
    'Test passes if there is a filled green square and no red.');
});

const blockAxis = provingFixture('block-axis-constraint-changes-for-out-of-flow-box');
test('proving: block-axis-constraint → mutated borders baked per side', { skip: !blockAxis }, () => {
  // Script adds border-top 50px to parent 1 and border-bottom 50px to
  // parent 2 — the abspos children's containing blocks shrink accordingly.
  const p0 = blockAxis.components['block-axis-constraint-changes-for-out-of-flow-box__0'];
  assert.equal(p0.properties['border-top-width'], '50px');
  assert.equal(p0.properties['border-top-style'], 'solid');
  const p1 = blockAxis.components['block-axis-constraint-changes-for-out-of-flow-box__1'];
  assert.equal(p1.properties['border-bottom-width'], '50px');
  // The authored `box-sizing: border-box` survives as the COMPUTED basis,
  // and the baked height is the border-box 100px (gCS expresses sizes in
  // the element's own basis — the measured Chromium behaviour that made
  // the naive content-box pin a bug this pin originally caught).
  assert.equal(p0.properties['box-sizing'], 'border-box');
  assert.equal(p0.properties.height, '100px');
});

const abspos = provingFixture('absolute-pos-box-inside-fixed-pos-box-with-changing-height');
test('proving: changing-height → fixed box 300px, bottom-anchored child at 250', { skip: !abspos }, () => {
  // Script: fixed.style.height = "300px"; the bottom:0 abspos child lands
  // at top 250px — exactly covering the red .ref square at top 250px.
  const fixed = abspos.components['absolute-pos-box-inside-fixed-pos-box-with-changing-height__1']
    .children['absolute-pos-box-inside-fixed-pos-box-with-changing-height__1__0'];
  assert.equal(fixed.properties.height, '300px');       // the baked mutation
  const box = fixed.children['absolute-pos-box-inside-fixed-pos-box-with-changing-height__1__0__0'];
  assert.equal(box.properties.top, '250px');            // 300 − 50 (used bottom:0)
  assert.equal(box.properties['background-color'], 'rgb(0, 128, 0)');
  const ref = abspos.components['absolute-pos-box-inside-fixed-pos-box-with-changing-height__0'];
  assert.equal(ref.properties.top, '250px');            // the red target it covers
  assert.equal(ref.properties['background-color'], 'rgb(255, 0, 0)');
});

// The bail/decline halves of the proving set: their fixtures must NOT carry
// the stamp — the wave-15 exclusion stays for undelivered tests.
for (const stem of ['containing-block-change-button', 'containing-block-change-scrollframe']) {
  const p = join(FIXTURES, `${stem}.json`);
  test(`proving: ${stem} stays static (bailed, no stamp)`, { skip: !existsSync(p) }, () => {
    // button: appendChild structural drift (element-mapping bail);
    // scrollframe: scrollTop=400 (scroll-offset bail) — both undeliverable.
    const fx = JSON.parse(readFileSync(p, 'utf8'));
    assert.equal(fx._wpt.postLoadExtracted, undefined);
  });
}
for (const stem of ['author-overlay-top-layer-removal', 'overlay-transition-backdrop',
                    'overlay-transition-backdrop-entry', 'overlay-button-appearance']) {
  const p = join(FIXTURES, `${stem}.json`);
  test(`proving: ${stem} stays static (top-layer decline, no stamp)`, { skip: !existsSync(p) }, () => {
    // The overlay family's signal lives in top-layer/::backdrop rendering —
    // declined before any browser launch (TOP_LAYER_API_RX).
    const fx = JSON.parse(readFileSync(p, 'utf8'));
    assert.equal(fx._wpt.postLoadExtracted, undefined);
  });
}

// ── 9. Optional live end-to-end (env-gated) ─────────────────────────────────
//
// Launches real Chromium against the real corpus — too heavy for the default
// hermetic `node --test` run, so it only fires with POST_LOAD_E2E=1 (and the
// corpus present). Verifies the full augment path end-to-end on the family's
// canonical test.
const WPT_DIR = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
const E2E_TEST = 'css/css-position/hypothetical-dynamic-change-001.html';
const e2eReady = process.env.POST_LOAD_E2E === '1' && existsSync(join(WPT_DIR, E2E_TEST));

test('post-load e2e: live augment delivers Left 100px', { skip: !e2eReady }, async () => {
  // Dynamic import keeps extractFixture/browser cost out of skipped runs.
  const { extractFixture } = await import('./extract-fixture.mjs');
  const { postLoadAugmentFixture, closePostLoadBrowser } = await import('./post-load-extract.mjs');
  try {
    const { fixture } = await extractFixture(E2E_TEST);
    const outcome = await postLoadAugmentFixture(fixture, E2E_TEST);
    assert.equal(outcome.status, 'extracted');
    assert.equal(fixture._wpt.postLoadExtracted, true);
    assert.equal(fixture.components['hypothetical-dynamic-change-001__0'].properties.left, '100px');
  } finally {
    await closePostLoadBrowser(); // never leak the shared browser
  }
});
